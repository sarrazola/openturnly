import SwiftUI

/// Everything about one appointment, and what can be done with it.
///
/// There is no route for a single appointment, so the screen starts from the
/// agenda's copy and reloads the appointment's day after each change.
struct AppointmentDetailView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var appointment: Appointment
    @State private var gone = false
    @State private var busy: AppointmentRules.Action?
    @State private var confirmingCancel = false
    @State private var rescheduling = false
    @State private var charging = false
    @State private var depositing = false
    @State private var editingNotes = false
    @State private var notes: String?
    @State private var notesLoaded = false

    init(initial: Appointment) {
        _appointment = State(initialValue: initial)
    }

    var body: some View {
        let actions = AppointmentRules.actions(for: appointment, isAdmin: workspace.isAdmin)
        ScrollView {
            VStack(spacing: 16) {
                if gone {
                    NoticeBanner(text: L.Appointment.gone)
                }
                hero(actions)
                if appointment.customer.phone != nil || appointment.customer.email != nil {
                    ContactActions(phone: appointment.customer.phone, email: appointment.customer.email)
                }
                if workspace.isAdmin && [.confirmed].contains(appointment.status) {
                    paymentCard(actions)
                }
                if appointment.status == .pendingPayment {
                    NoticeBanner(text: L.Status.pendingPaymentHint, symbol: "clock.badge.exclamationmark")
                }
                detailsCard
                if let answers = appointment.answers, !answers.isEmpty {
                    answersCard(answers)
                }
                if workspace.isAdmin {
                    notesCard
                }
                actionButtons(actions)
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.canvas)
        .navigationTitle(L.Appointment.title)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await reload() }
        .task(id: workspace.revision) {
            if workspace.revision > 0 { await reload() }
        }
        .task { await loadNotes() }
        .sheet(isPresented: $rescheduling) {
            RescheduleView(appointment: appointment) { startsAt in
                Task { await reload(near: startsAt) }
            }
        }
        .sheet(isPresented: $charging) { CheckoutView(appointment: appointment) }
        .sheet(isPresented: $depositing) { DepositView(appointment: appointment) }
        .sheet(isPresented: $editingNotes) {
            NotesEditor(initial: notes ?? "") { text in
                await saveNotes(text)
            }
        }
    }

    // MARK: Sections

    private func hero(_ actions: [AppointmentRules.Action]) -> some View {
        let status = AppointmentRules.displayStatus(appointment.status, startsAt: appointment.startsAt)
        let clock = workspace.clock
        return VStack(spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                Avatar(name: appointment.customer.fullName, size: 56)
                VStack(alignment: .leading, spacing: 5) {
                    Text(appointment.customer.fullName)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.ink)
                    HStack(spacing: 6) {
                        Chip(text: L.Status.label(status), tone: Tone.of(status))
                        Text(appointment.code).font(.caption.weight(.semibold)).monospaced().foregroundStyle(Theme.muted)
                            .textSelection(.enabled)
                    }
                }
                Spacer(minLength: 0)
            }
            Divider()
            HStack(spacing: 0) {
                heroStat(symbol: "calendar", value: Fmt.relativeDay(clock.localDate(of: appointment.startsAt), today: clock.today(), short: true))
                Divider().frame(height: 32)
                heroStat(symbol: "clock", value: "\(clock.time(of: appointment.startsAt)) – \(clock.time(of: appointment.endsAt))")
            }
            if actions.contains(.viewCustomer) {
                NavigationLink(value: Route.customer(id: appointment.customer.id, name: appointment.customer.fullName)) {
                    HStack {
                        Image(systemName: "person.crop.circle")
                        Text(L.Appointment.viewCustomer)
                        Spacer()
                        Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.subtle)
                    }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Theme.brand)
                    .padding(.horizontal, 14)
                    .frame(minHeight: 44)
                    .background(Theme.brandSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
        .card()
    }

    private func heroStat(symbol: String, value: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: symbol).foregroundStyle(Theme.brand)
            Text(value).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
    }

    private var detailsCard: some View {
        let clock = workspace.clock
        let minutes = Int(appointment.endsAt.timeIntervalSince(appointment.startsAt) / 60)
        return VStack(spacing: 0) {
            InfoRow(label: L.Appointment.when, value: Fmt.dayLong(clock.localDate(of: appointment.startsAt)), symbol: "calendar")
            Divider()
            InfoRow(label: L.Appointment.service, value: "\(appointment.serviceName) · \(WallTime.duration(minutes))", symbol: "scissors")
            if let name = appointment.resourceName {
                Divider()
                HStack(spacing: 12) {
                    Image(systemName: "person").font(.subheadline).foregroundStyle(Theme.subtle).frame(width: 20)
                    Text(L.Appointment.professional).font(.subheadline).foregroundStyle(Theme.muted)
                    Spacer()
                    Circle().fill(workspace.color(for: appointment.resourceId)).frame(width: 8, height: 8)
                    Text(name).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                }
                .padding(.vertical, 10)
            }
            if let location = appointment.locationName, workspace.locations.count > 1 {
                Divider()
                InfoRow(label: L.Appointment.location, value: location, symbol: "mappin.and.ellipse")
            }
            if workspace.isAdmin {
                Divider()
                InfoRow(label: L.Appointment.price, value: workspace.business.price(appointment.priceAmount), symbol: "tag")
                if appointment.discountAmount > 0 {
                    Divider()
                    InfoRow(label: L.Appointment.discountCode(appointment.giftCode ?? ""), value: "−\(workspace.money(appointment.discountAmount))",
                            symbol: "percent", valueColor: Theme.success)
                }
            }
            Divider()
            InfoRow(label: L.Appointment.bookedVia, value: sourceText, symbol: "arrow.down.circle")
        }
        .card(padding: 14)
    }

    private var sourceText: String {
        switch AppointmentRules.source(appointment) {
        case .online: return L.Appointment.sourceOnline
        case .admin: return L.Appointment.sourceAdmin
        case .agent(let label): return [L.Appointment.sourceAgent, label].compactMap { $0 }.joined(separator: " · ")
        case .other: return appointment.source ?? "—"
        }
    }

    private func paymentCard(_ actions: [AppointmentRules.Action]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(L.Appointment.payment).font(.caption.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase)
                    paymentLine
                }
                Spacer()
                if appointment.sale == nil, appointment.priceAmount != nil, AppointmentRules.owed(appointment) > 0 {
                    Text(workspace.money(AppointmentRules.owed(appointment)))
                        .font(.title3.weight(.bold)).fontDesign(.rounded).monospacedDigit()
                        .foregroundStyle(Theme.ink)
                }
            }
            HStack(spacing: 10) {
                if actions.contains(.viewSale), let sale = appointment.sale {
                    NavigationLink(value: Route.saleId(sale.id)) {
                        Label(L.Checkout.viewSale, systemImage: "doc.text")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.brand)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(Theme.brand.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                if actions.contains(.deposit) {
                    SecondaryButton(title: L.Checkout.deposit, symbol: "tray.and.arrow.down") { depositing = true }
                }
                if actions.contains(.charge) || actions.contains(.closeSale) {
                    Button {
                        Haptics.tap()
                        charging = true
                    } label: {
                        Label(actions.contains(.closeSale) ? L.Checkout.closeSale : L.Checkout.charge, systemImage: "banknote")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Theme.onBrand)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(Theme.brand.gradient, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(PressableStyle())
                }
            }
        }
        .card()
    }

    @ViewBuilder
    private var paymentLine: some View {
        switch AppointmentRules.paymentState(appointment) {
        case .paid(let number):
            HStack(spacing: 6) {
                Chip(text: L.Checkout.paid, tone: .info, symbol: "checkmark")
                Text(L.Checkout.paidIn(number)).font(.subheadline).foregroundStyle(Theme.muted)
            }
        case .covered(let deposit):
            VStack(alignment: .leading, spacing: 2) {
                Chip(text: L.Checkout.paid, tone: .info, symbol: "checkmark")
                Text(L.Checkout.depositLeft(workspace.money(deposit))).font(.footnote).foregroundStyle(Theme.muted)
            }
        case .partly(let owed, let deposit):
            VStack(alignment: .leading, spacing: 2) {
                Text(L.Checkout.dueLeft(workspace.money(owed))).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                Text(L.Checkout.depositLeft(workspace.money(deposit))).font(.footnote).foregroundStyle(Theme.muted)
            }
        case .unpaid, .none:
            Text(L.Checkout.unpaid).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
        }
    }

    private func answersCard(_ answers: [BookingAnswer]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.answers)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(answers.enumerated()), id: \.offset) { index, answer in
                    if index > 0 { Divider() }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(answer.label).font(.footnote).foregroundStyle(Theme.muted)
                        Text(answer.value).font(.subheadline).foregroundStyle(Theme.ink)
                    }
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .card(padding: 14)
        }
    }

    private var notesCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.customerNotes)
            Button {
                editingNotes = true
            } label: {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: "note.text").foregroundStyle(Theme.brand)
                    Group {
                        if !notesLoaded {
                            SkeletonBlock(width: 160, height: 12).skeleton()
                        } else if let notes, !notes.isEmpty {
                            Text(notes).foregroundStyle(Theme.ink)
                        } else {
                            Text(L.Appointment.addNotes).foregroundStyle(Theme.muted)
                        }
                    }
                    .font(.subheadline)
                    .multilineTextAlignment(.leading)
                    Spacer()
                    Image(systemName: "pencil").font(.subheadline).foregroundStyle(Theme.subtle)
                }
                .card(padding: 14)
            }
            .buttonStyle(.plain)
            .disabled(!notesLoaded)
            .accessibilityLabel(L.Appointment.editNotes)
        }
    }

    @ViewBuilder
    private func actionButtons(_ actions: [AppointmentRules.Action]) -> some View {
        VStack(spacing: 10) {
            if actions.contains(.reschedule) {
                PrimaryButton(title: L.Appointment.reschedule, symbol: "calendar.badge.clock") { rescheduling = true }
            }
            if actions.contains(.undoNoShow) {
                PrimaryButton(title: L.Appointment.undoNoShow, symbol: "person.fill.checkmark", busy: busy == .undoNoShow) {
                    Task { await run(.undoNoShow) }
                }
            }
            if actions.contains(.markNoShow) {
                SecondaryButton(title: L.Appointment.markNoShow, symbol: "person.fill.xmark", tint: Color(hex: "#ea580c"), busy: busy == .markNoShow) {
                    Task { await run(.markNoShow) }
                }
            }
            if actions.contains(.cancel) {
                SecondaryButton(title: L.Appointment.cancel, symbol: "xmark.circle", tint: Theme.danger, busy: busy == .cancel) {
                    confirmingCancel = true
                }
                .confirmationDialog(L.Appointment.cancelTitle, isPresented: $confirmingCancel, titleVisibility: .visible) {
                    Button(L.Appointment.cancel, role: .destructive) { Task { await run(.cancel) } }
                    Button(L.Appointment.keep, role: .cancel) {}
                } message: {
                    Text(L.Appointment.cancelBody)
                }
            }
        }
        .padding(.top, 4)
    }

    // MARK: Work

    private func run(_ action: AppointmentRules.Action) async {
        busy = action
        defer { busy = nil }
        do {
            switch action {
            case .cancel:
                _ = try await workspace.api.cancel(workspace.id, appointment.id)
                toasts.show(L.Appointment.cancelled)
            case .markNoShow:
                _ = try await workspace.api.markNoShow(workspace.id, appointment.id)
                toasts.show(L.Appointment.noShowMarked)
            case .undoNoShow:
                _ = try await workspace.api.undoNoShow(workspace.id, appointment.id)
                toasts.show(L.Appointment.noShowUndone)
            default:
                return
            }
            workspace.changed()
            await reload()
        } catch {
            toasts.error(error)
        }
    }

    private func reload(near date: Date? = nil) async {
        do {
            if let fresh = try await workspace.reloadAppointment(appointment.id, near: date ?? appointment.startsAt) {
                appointment = fresh
                gone = false
            } else {
                gone = true
            }
        } catch {}
    }

    private func loadNotes() async {
        guard workspace.isAdmin, !notesLoaded else { return }
        do {
            let detail = try await workspace.api.customer(workspace.id, appointment.customer.id)
            notes = detail.customer.notes
        } catch {}
        notesLoaded = true
    }

    private func saveNotes(_ text: String) async -> Bool {
        do {
            let customer = try await workspace.api.updateCustomer(workspace.id, appointment.customer.id, ["notes": .text(text)])
            notes = customer.notes
            toasts.show(L.Appointment.notesSaved)
            return true
        } catch {
            toasts.error(error)
            return false
        }
    }
}

/// A sheet to write the customer's private notes.
struct NotesEditor: View {
    let initial: String
    let save: (String) async -> Bool
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var busy = false
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L.Appointment.customerNotesHint, text: $text, axis: .vertical)
                        .lineLimit(5...12)
                        .focused($focused)
                } footer: {
                    Text(L.Appointment.customerNotesHint)
                }
            }
            .navigationTitle(L.Appointment.customerNotes)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy {
                        ProgressView()
                    } else {
                        Button(L.Common.save) {
                            busy = true
                            Task {
                                if await save(text) { dismiss() }
                                busy = false
                            }
                        }
                        .fontWeight(.semibold)
                        .disabled(text.count > 1000)
                    }
                }
            }
            .onAppear {
                text = initial
                focused = true
            }
        }
        .presentationDetents([.medium, .large])
    }
}
