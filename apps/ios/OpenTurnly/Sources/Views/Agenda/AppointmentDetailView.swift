import SwiftUI
import UIKit

/// Everything about one appointment, and what can be done with it.
///
/// Top to bottom: the header (customer, status, code, when, what, who, where),
/// the customer's contact, notes, the payment, the booking answers, the details
/// and a list of the secondary actions; the main actions sit in a bar pinned to
/// the bottom. There is no route for a single appointment, so the screen starts
/// from the agenda's copy and reloads the appointment's day after each change.
struct AppointmentDetailView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var appointment: Appointment
    @State private var gone = false
    @State private var busy: AppointmentRules.Action?
    @State private var confirmingCancel = false
    @State private var confirmingNoShow = false
    @State private var rescheduling = false
    @State private var charging = false
    @State private var depositing = false
    @State private var showingSale = false
    @State private var editingNotes = false
    @State private var notes: String?
    @State private var notesLoaded = false

    init(initial: Appointment) {
        _appointment = State(initialValue: initial)
    }

    private var isAdmin: Bool { workspace.isAdmin }

    var body: some View {
        let actions = gone ? [] : AppointmentRules.actions(for: appointment, isAdmin: isAdmin)
        let bar = AppointmentRules.bar(actions)
        ScrollView {
            VStack(spacing: 16) {
                if gone {
                    NoticeBanner(text: L.Appointment.gone)
                }
                header
                if appointment.status == .pendingPayment {
                    NoticeBanner(text: L.Status.pendingPaymentHint, symbol: "clock.badge.exclamationmark")
                }
                customerCard
                if isAdmin {
                    notesCard
                    paymentCard
                }
                if let answers = appointment.answers, !answers.isEmpty {
                    answersCard(answers)
                }
                detailsCard
                if isAdmin, !AppointmentRules.listed(actions).isEmpty {
                    actionsList(AppointmentRules.listed(actions))
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.canvas)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if !bar.isEmpty { bottomBar(bar) }
        }
        .navigationTitle(L.Appointment.title)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await reload() }
        .task(id: workspace.revision) {
            if workspace.revision > 0 { await reload() }
        }
        .task { await loadNotes() }
        .navigationDestination(isPresented: $showingSale) {
            if let sale = appointment.sale { SaleDetailView(saleId: sale.id) }
        }
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
        // Alerts rather than confirmation dialogs: both buttons stay visible.
        .alert(L.Appointment.cancelTitle, isPresented: $confirmingCancel) {
            Button(L.Appointment.cancel, role: .destructive) { Task { await run(.cancel) } }
            Button(L.Appointment.keep, role: .cancel) {}
        } message: {
            Text(L.Appointment.cancelBody)
        }
        .alert(L.Appointment.noShowTitle, isPresented: $confirmingNoShow) {
            Button(L.Appointment.markNoShow, role: .destructive) { Task { await run(.markNoShow) } }
            Button(L.Common.goBack, role: .cancel) {}
        } message: {
            Text(L.Appointment.noShowBody)
        }
    }

    // MARK: Header

    private var header: some View {
        let clock = workspace.clock
        let day = clock.localDate(of: appointment.startsAt)
        let today = clock.today()
        let status = AppointmentRules.displayStatus(appointment)
        let minutes = Int(appointment.endsAt.timeIntervalSince(appointment.startsAt) / 60)
        let dayText = [Fmt.relativeName(day, today: today), Fmt.dayLong(day, thisYear: today.year)].compactMap { $0 }.joined(separator: " · ")
        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 14) {
                Avatar(name: appointment.customer.fullName, size: 56)
                VStack(alignment: .leading, spacing: 6) {
                    Text(appointment.customer.fullName)
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 8) {
                        StatusChip(status: status)
                        Button {
                            UIPasteboard.general.string = appointment.code
                            toasts.show(L.Common.copied)
                        } label: {
                            Text(appointment.code).font(.caption.weight(.semibold)).monospaced().foregroundStyle(Theme.muted)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(L.Appointment.code) \(appointment.code)")
                        .accessibilityHint(L.Common.copy)
                    }
                }
                Spacer(minLength: 0)
            }
            Divider()
            VStack(alignment: .leading, spacing: 10) {
                headerRow("calendar", dayText)
                headerRow("clock", "\(clock.time(of: appointment.startsAt)) – \(clock.time(of: appointment.endsAt)) · \(WallTime.duration(minutes))")
                HStack(spacing: 12) {
                    Image(systemName: "scissors").font(.subheadline).foregroundStyle(Theme.brand).frame(width: 20)
                    Text(appointment.serviceName).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                    if !isAdmin, let price = AppointmentRules.price(appointment) {
                        Spacer(minLength: 8)
                        Text(workspace.money(price)).font(.subheadline).monospacedDigit().foregroundStyle(Theme.muted)
                    }
                }
                HStack(spacing: 12) {
                    Circle().fill(workspace.color(for: appointment.resourceId)).frame(width: 9, height: 9).frame(width: 20)
                    Text(appointment.resourceName ?? L.Appointment.noProfessional).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                }
                if workspace.activeLocations.count > 1, let location = appointment.locationName {
                    headerRow("mappin.and.ellipse", location)
                }
            }
        }
        .card()
    }

    private func headerRow(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.subheadline).foregroundStyle(Theme.brand).frame(width: 20)
            Text(text).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink).fixedSize(horizontal: false, vertical: true)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Customer

    private var customerCard: some View {
        let customer = appointment.customer
        let phone = Phone.format(customer.phone)
        return VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.customer)
            VStack(alignment: .leading, spacing: 12) {
                if phone.isEmpty && customer.email == nil {
                    Text(L.Appointment.noContact).font(.subheadline).foregroundStyle(Theme.muted)
                } else {
                    VStack(alignment: .leading, spacing: 6) {
                        if !phone.isEmpty {
                            Label(phone, systemImage: "phone").font(.subheadline).foregroundStyle(Theme.ink).textSelection(.enabled)
                        }
                        if let email = customer.email {
                            Label(email, systemImage: "envelope").font(.subheadline).foregroundStyle(Theme.ink).textSelection(.enabled)
                        }
                    }
                    .labelStyle(ContactLabelStyle())
                    ContactActions(phone: customer.phone, email: customer.email)
                }
                if isAdmin {
                    NavigationLink(value: Route.customer(id: customer.id, name: customer.fullName)) {
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

    // MARK: Payment

    private var paymentCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.payment)
            VStack(spacing: 0) {
                InfoRow(label: L.Appointment.price, value: workspace.business.price(appointment.priceAmount))
                if appointment.discountAmount > 0 {
                    InfoRow(label: L.Appointment.discountCode(appointment.giftCode), value: "−\(workspace.money(appointment.discountAmount))",
                            valueColor: Theme.success)
                }
                if appointment.depositAmount > 0 {
                    InfoRow(label: L.Appointment.deposits, value: "−\(workspace.money(appointment.depositAmount))")
                }
                if appointment.status == .confirmed {
                    Divider().padding(.vertical, 6)
                    paymentLine
                        .padding(.vertical, 4)
                }
            }
            .card(padding: 14)
        }
    }

    @ViewBuilder
    private var paymentLine: some View {
        switch AppointmentRules.paymentState(appointment) {
        case .paid(let number):
            HStack(spacing: 8) {
                Chip(text: L.Checkout.paid, tone: .info, symbol: "checkmark")
                Text(L.Checkout.paidIn(number)).font(.subheadline).foregroundStyle(Theme.muted)
                Spacer()
            }
        case .covered:
            VStack(alignment: .leading, spacing: 6) {
                Chip(text: L.Checkout.toClose, tone: .warning)
                Text(L.Checkout.coveredHint).font(.footnote).foregroundStyle(Theme.muted).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .partly(let owed, let deposit):
            VStack(alignment: .leading, spacing: 2) {
                Text(L.Checkout.dueLeft(workspace.money(owed))).font(.subheadline.weight(.bold)).foregroundStyle(Theme.ink)
                Text(L.Checkout.deposited(workspace.money(deposit))).font(.footnote).foregroundStyle(Theme.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        case .unpaid, .none:
            HStack {
                Chip(text: L.Checkout.unpaid, tone: .warning)
                Spacer()
                if let owed = AppointmentRules.chargeAmount(appointment) {
                    Text(workspace.money(owed)).font(.title3.weight(.bold)).fontDesign(.rounded).monospacedDigit().foregroundStyle(Theme.ink)
                }
            }
        }
    }

    // MARK: Answers and details

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

    private var detailsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.details)
            VStack(spacing: 0) {
                InfoRow(label: L.Appointment.bookedVia, value: sourceText)
                if let created = appointment.createdAt {
                    Divider()
                    InfoRow(label: L.Appointment.bookedOn, value: Fmt.dateTime(created, clock: workspace.clock))
                }
            }
            .card(padding: 14)
        }
    }

    private var sourceText: String {
        switch AppointmentRules.source(appointment) {
        case .online: return L.Appointment.sourceOnline
        case .admin: return L.Appointment.sourceAdmin
        case .agent(let label): return [L.Appointment.sourceAgent, TextRules.clean(label)].compactMap { $0 }.joined(separator: " · ")
        case .other: return appointment.source ?? "—"
        }
    }

    // MARK: Actions

    private func actionsList(_ rows: [AppointmentRules.Action]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Appointment.actions)
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element) { index, action in
                    if index > 0 { Divider().padding(.leading, 44) }
                    Button {
                        perform(action)
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: symbol(action)).font(.body.weight(.semibold)).frame(width: 30)
                            Text(label(action)).font(.body)
                            Spacer()
                            if busy == action { ProgressView() }
                        }
                        .foregroundStyle(tint(action))
                        .frame(minHeight: 48)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(busy != nil)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 2)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        }
    }

    private func bottomBar(_ bar: AppointmentRules.Bar) -> some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 10) {
                if let secondary = bar.secondary {
                    SecondaryButton(title: label(secondary), symbol: symbol(secondary), busy: busy == secondary) { perform(secondary) }
                        .frame(height: 52)
                }
                if let primary = bar.primary {
                    PrimaryButton(title: label(primary), symbol: symbol(primary), busy: busy == primary) { perform(primary) }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(.bar)
    }

    private func label(_ action: AppointmentRules.Action) -> String {
        switch action {
        case .charge:
            return AppointmentRules.chargeAmount(appointment).map { L.Checkout.chargeAmount(workspace.money($0)) } ?? L.Checkout.charge
        case .closeSale: return L.Checkout.closeSale
        case .viewSale: return L.Checkout.viewSale
        case .reschedule: return L.Appointment.reschedule
        case .deposit: return L.Checkout.deposit
        case .markNoShow: return L.Appointment.markNoShow
        case .undoNoShow: return L.Appointment.undoNoShow
        case .cancel: return L.Appointment.cancel
        case .viewCustomer: return L.Appointment.viewCustomer
        }
    }

    private func symbol(_ action: AppointmentRules.Action) -> String {
        switch action {
        case .charge, .closeSale: return "banknote"
        case .viewSale: return "doc.text"
        case .reschedule: return "calendar.badge.clock"
        case .deposit: return "tray.and.arrow.down"
        case .markNoShow: return "person.fill.xmark"
        case .undoNoShow: return "person.fill.checkmark"
        case .cancel: return "xmark.circle"
        case .viewCustomer: return "person.crop.circle"
        }
    }

    private func tint(_ action: AppointmentRules.Action) -> Color {
        switch action {
        case .markNoShow: return Color(hex: "#ea580c")
        case .cancel: return Theme.danger
        default: return Theme.brand
        }
    }

    private func perform(_ action: AppointmentRules.Action) {
        switch action {
        case .charge, .closeSale: charging = true
        case .viewSale: showingSale = true
        case .reschedule: rescheduling = true
        case .deposit: depositing = true
        case .markNoShow: confirmingNoShow = true
        case .cancel: confirmingCancel = true
        case .undoNoShow: Task { await run(.undoNoShow) }
        case .viewCustomer: break
        }
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
                workspace.changed()
                dismiss()
                return
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
        guard isAdmin, !notesLoaded else { return }
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

/// A contact line: a small muted icon and the value.
private struct ContactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 10) {
            configuration.icon.foregroundStyle(Theme.subtle).frame(width: 20)
            configuration.title
        }
    }
}

/// The status chip of §2.2: «Ahora» carries a dot.
struct StatusChip: View {
    let status: AppointmentRules.DisplayStatus

    var body: some View {
        Chip(text: L.Status.label(status), tone: Tone.of(status), symbol: status == .ongoing ? "circle.fill" : nil)
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
        .interactiveDismissDisabled(busy)
    }
}
