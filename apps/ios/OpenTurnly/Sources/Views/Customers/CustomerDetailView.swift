import SwiftUI

/// One customer: contact, visits, notes, upcoming and past appointments.
struct CustomerDetailView: View {
    let customerId: String
    let name: String
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var detail: CustomerDetail?
    @State private var error: Error?
    @State private var editing = false
    @State private var editingNotes = false
    @State private var booking = false
    @State private var opening: String?
    @State private var openedAppointment: Appointment?

    var body: some View {
        Group {
            if let detail {
                content(detail)
            } else if let error {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else {
                VStack(spacing: 16) {
                    CardsSkeleton(count: 3)
                    Spacer()
                }
                .padding(16)
            }
        }
        .background(Theme.canvas)
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if detail != nil {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(L.Common.edit) { editing = true }
                }
            }
        }
        .navigationDestination(item: $openedAppointment) { appointment in
            AppointmentDetailView(initial: appointment)
        }
        .task { await load() }
        .task(id: workspace.revision) { if workspace.revision > 0 { await load() } }
        .sheet(isPresented: $editing) {
            if let customer = detail?.customer {
                CustomerFormView(customer: customer) { _ in Task { await load() } }
            }
        }
        .sheet(isPresented: $editingNotes) {
            NotesEditor(initial: detail?.customer.notes ?? "") { text in
                do {
                    _ = try await workspace.api.updateCustomer(workspace.id, customerId, ["notes": .text(text)])
                    toasts.show(L.Appointment.notesSaved)
                    await load()
                    return true
                } catch {
                    toasts.error(error)
                    return false
                }
            }
        }
        .sheet(isPresented: $booking) {
            if let customer = detail?.customer {
                BookingView(date: workspace.clock.today(),
                            preset: PickedCustomer(id: customer.id, name: customer.fullName, phone: customer.phone, email: customer.email))
            }
        }
    }

    private func content(_ detail: CustomerDetail) -> some View {
        let customer = detail.customer
        let now = Date()
        let clock = workspace.clock
        let upcoming = CustomerRules.upcoming(detail.appointments, now: now)
        let past = CustomerRules.history(detail.appointments, now: now)
        let stats = CustomerRules.stats(detail.appointments, now: now)
        return ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 12) {
                    Avatar(name: customer.fullName, size: 76)
                    Text(customer.fullName).font(.title2.weight(.bold)).foregroundStyle(Theme.ink).multilineTextAlignment(.center)
                    if let since = customer.createdAt {
                        Text(L.Customers.since(Fmt.dayWithYear(clock.localDate(of: since))))
                            .font(.footnote).foregroundStyle(Theme.muted)
                    }
                    if customer.phone != nil || customer.email != nil {
                        ContactActions(phone: customer.phone, email: customer.email)
                            .padding(.top, 4)
                    }
                }
                .frame(maxWidth: .infinity)
                .card()

                PrimaryButton(title: L.Agenda.newAppointment, symbol: "calendar.badge.plus") { booking = true }

                HStack(spacing: 10) {
                    StatTile(label: L.Customers.visits, value: "\(stats.visits)", symbol: "checkmark.circle")
                    StatTile(label: L.Customers.lastVisit, value: stats.lastVisit.map { Fmt.dayShort(clock.localDate(of: $0)) } ?? L.Customers.never,
                             symbol: "clock.arrow.circlepath", tint: Color(hex: "#0284c7"))
                    StatTile(label: L.Customers.nextVisit, value: stats.nextVisit.map { Fmt.dayShort(clock.localDate(of: $0)) } ?? L.Customers.never,
                             symbol: "calendar", valueColor: stats.nextVisit == nil ? Theme.ink : Theme.brand)
                }

                if customer.phone != nil || customer.email != nil || customer.birthDate != nil
                    || TextRules.clean(customer.document) != nil || customer.gender != nil {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionTitle(text: L.Customers.details)
                        VStack(spacing: 0) {
                            ForEach(Array(detailRows(customer).enumerated()), id: \.offset) { index, row in
                                if index > 0 { Divider() }
                                InfoRow(label: row.label, value: row.value, symbol: row.symbol)
                            }
                        }
                        .card(padding: 14)
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    SectionTitle(text: L.Customers.notes)
                    Button {
                        editingNotes = true
                    } label: {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "note.text").foregroundStyle(Theme.brand)
                            Text(customer.notes?.isEmpty == false ? customer.notes! : L.Appointment.addNotes)
                                .font(.subheadline)
                                .foregroundStyle(customer.notes?.isEmpty == false ? Theme.ink : Theme.muted)
                                .multilineTextAlignment(.leading)
                            Spacer()
                            Image(systemName: "pencil").foregroundStyle(Theme.subtle)
                        }
                        .card(padding: 14)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(L.Appointment.editNotes)
                }

                if !upcoming.isEmpty {
                    history(L.Customers.upcoming, upcoming)
                }
                if past.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionTitle(text: L.Customers.history, trailing: "0")
                        Text(L.Customers.noHistory).font(.subheadline).foregroundStyle(Theme.muted).card(padding: 14)
                    }
                } else {
                    history(L.Customers.history, past)
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await load() }
    }

    /// Celular, Email, Fecha de nacimiento, Documento, Género: only those with data.
    private func detailRows(_ customer: Customer) -> [(label: String, value: String, symbol: String)] {
        var rows: [(label: String, value: String, symbol: String)] = []
        if let phone = customer.phone { rows.append((L.Common.phone, Phone.format(phone), "phone")) }
        if let email = customer.email { rows.append((L.Common.email, email, "envelope")) }
        if let birth = customer.birthDate.flatMap(LocalDate.init) { rows.append((L.Customers.birthDate, Fmt.dayWithYear(birth), "gift")) }
        if let document = TextRules.clean(customer.document) { rows.append((L.Customers.document, document, "person.text.rectangle")) }
        if customer.gender != nil { rows.append((L.Customers.gender, L.Customers.genderName(customer.gender), "person")) }
        return rows
    }

    private func history(_ title: String, _ items: [CustomerAppointment]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: title, trailing: "\(items.count)")
            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider() }
                    Button {
                        open(item)
                    } label: {
                        HistoryRow(item: item, busy: opening == item.id)
                    }
                    .buttonStyle(.plain)
                    .disabled(opening != nil)
                }
            }
            .card(padding: 14)
        }
    }

    /// Finds the appointment on its day, like a tapped notification, and opens it.
    private func open(_ item: CustomerAppointment) {
        Haptics.selection()
        opening = item.id
        Task {
            defer { opening = nil }
            do {
                if let found = try await workspace.reloadAppointment(item.id, near: item.startsAt) {
                    openedAppointment = found
                } else {
                    toasts.show(L.Appointment.notFound, error: true)
                }
            } catch {
                toasts.error(error)
            }
        }
    }

    private func load() async {
        do {
            detail = try await workspace.api.customer(workspace.id, customerId)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
}

private struct HistoryRow: View {
    let item: CustomerAppointment
    var busy = false
    @Environment(Workspace.self) private var workspace

    var body: some View {
        let clock = workspace.clock
        let status = AppointmentRules.displayStatus(item.status, startsAt: item.startsAt, endsAt: item.endsAt)
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(Fmt.dayWithYear(clock.localDate(of: item.startsAt))) · \(clock.time(of: item.startsAt))")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                Text([item.serviceName, item.resourceName].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(Theme.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                StatusChip(status: status)
                if let price = item.priceAmount {
                    Text(workspace.money(price)).font(.caption).monospacedDigit().foregroundStyle(Theme.subtle)
                }
            }
            if busy {
                ProgressView().controlSize(.small)
            } else {
                Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.subtle)
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}
