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
        let upcoming = detail.appointments.filter { $0.startsAt >= now && $0.status != .cancelled }.sorted { $0.startsAt < $1.startsAt }
        let past = detail.appointments.filter { !upcoming.contains($0) }
        let visits = detail.appointments.filter { $0.status == .confirmed && $0.startsAt < now }
        let spent = visits.reduce(0) { $0 + ($1.priceAmount ?? 0) }
        return ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 12) {
                    Avatar(name: customer.fullName, size: 76)
                    Text(customer.fullName).font(.title2.weight(.bold)).foregroundStyle(Theme.ink).multilineTextAlignment(.center)
                    if let since = customer.createdAt {
                        Text(L.Customers.since(Fmt.dayWithYear(workspace.clock.localDate(of: since))))
                            .font(.footnote).foregroundStyle(Theme.muted)
                    }
                    ContactActions(phone: customer.phone, email: customer.email)
                        .padding(.top, 4)
                }
                .frame(maxWidth: .infinity)
                .card()

                HStack(spacing: 12) {
                    StatTile(label: L.Customers.visitsDone, value: "\(visits.count)", symbol: "checkmark.circle")
                    StatTile(label: L.Customers.spent, value: workspace.money(spent), symbol: "banknote", tint: Color(hex: "#0284c7"))
                }

                PrimaryButton(title: L.Agenda.newAppointment, symbol: "calendar.badge.plus") { booking = true }

                VStack(alignment: .leading, spacing: 8) {
                    SectionTitle(text: L.Customers.contact)
                    VStack(spacing: 0) {
                        if let phone = customer.phone {
                            InfoRow(label: L.Common.phone, value: Phone.format(phone), symbol: "phone")
                        }
                        if let email = customer.email {
                            if customer.phone != nil { Divider() }
                            InfoRow(label: L.Common.email, value: email, symbol: "envelope")
                        }
                        if let birth = customer.birthDate.flatMap(LocalDate.init) {
                            Divider()
                            InfoRow(label: L.Customers.birthDate, value: Fmt.dayWithYear(birth), symbol: "gift")
                        }
                        if let document = customer.document, !document.isEmpty {
                            Divider()
                            InfoRow(label: L.Customers.document, value: document, symbol: "person.text.rectangle")
                        }
                        if customer.gender != nil {
                            Divider()
                            InfoRow(label: L.Customers.gender, value: L.Customers.genderName(customer.gender), symbol: "person")
                        }
                    }
                    .card(padding: 14)
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
                }

                if !upcoming.isEmpty {
                    history(L.Customers.upcoming, upcoming)
                }
                if past.isEmpty && upcoming.isEmpty {
                    EmptyState(symbol: "calendar", title: L.Customers.noHistory).card()
                } else if !past.isEmpty {
                    history(L.Customers.history, past)
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await load() }
    }

    private func history(_ title: String, _ items: [CustomerAppointment]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: title, trailing: "\(items.count)")
            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    if index > 0 { Divider() }
                    HistoryRow(item: item)
                }
            }
            .card(padding: 14)
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
    @Environment(Workspace.self) private var workspace

    var body: some View {
        let clock = workspace.clock
        let status = AppointmentRules.displayStatus(item.status, startsAt: item.startsAt)
        HStack(spacing: 12) {
            VStack(spacing: 0) {
                Text("\(clock.localDate(of: item.startsAt).day)").font(.headline).fontDesign(.rounded)
                Text(Fmt.monthShort(clock.localDate(of: item.startsAt)).prefix(3).uppercased())
                    .font(.caption2.weight(.semibold)).foregroundStyle(Theme.muted)
            }
            .frame(width: 44, height: 44)
            .background(Theme.fill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(item.serviceName).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                Text([clock.time(of: item.startsAt), item.resourceName,
                      clock.localDate(of: item.startsAt).year == clock.today().year ? nil : "\(clock.localDate(of: item.startsAt).year)"]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(Theme.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Chip(text: status == .confirmed ? L.Customers.attended : L.Status.label(status),
                     tone: status == .confirmed ? .success : Tone.of(status))
                if let price = item.priceAmount {
                    Text(workspace.money(price)).font(.caption).monospacedDigit().foregroundStyle(Theme.subtle)
                }
            }
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
    }
}
