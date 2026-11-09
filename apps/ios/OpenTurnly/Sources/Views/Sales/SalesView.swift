import SwiftUI

/// The sales and deposits of a day, a week or a month, with what came in by
/// payment method.
struct SalesView: View {
    @Environment(Workspace.self) private var workspace
    @State private var anchor: LocalDate?
    @State private var span: SalesRules.Span = .day
    @State private var tab: Tab = .sales
    @State private var sales: [Sale] = []
    @State private var deposits: [Deposit] = []
    @State private var loading = true
    @State private var error: Error?
    @State private var picking = false
    @State private var selling = false

    enum Tab: Hashable { case sales, deposits }

    private struct Key: Hashable {
        let span: SalesRules.Span
        let anchor: LocalDate
    }

    var body: some View {
        let today = workspace.clock.today()
        let current = anchor ?? today
        List {
            Section {
                Picker("", selection: $span) {
                    Text(L.Sales.day).tag(SalesRules.Span.day)
                    Text(L.Sales.week).tag(SalesRules.Span.week)
                    Text(L.Sales.month).tag(SalesRules.Span.month)
                }
                .pickerStyle(.segmented)
                .plainRow(top: 4, bottom: 4)
                periodBar(current, today: today)
                    .plainRow(top: 4, bottom: 4)
                Picker("", selection: $tab) {
                    Text(L.Sales.salesTab).tag(Tab.sales)
                    Text(L.Sales.depositsTab).tag(Tab.deposits)
                }
                .pickerStyle(.segmented)
                .plainRow(top: 4, bottom: 8)
                if loading && sales.isEmpty && deposits.isEmpty {
                    CardsSkeleton(count: 3).plainRow()
                } else if let error {
                    ErrorState(message: ErrorText.message(error)) { Task { await load(current) } }.card().plainRow()
                } else if tab == .sales {
                    salesContent
                } else {
                    depositsContent
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(Theme.canvas)
        .navigationTitle(L.Sales.title)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    selling = true
                } label: {
                    Label(L.Checkout.newSale, systemImage: "plus")
                }
            }
        }
        .refreshable { await load(current) }
        .task(id: Key(span: span, anchor: current)) { await load(current) }
        .task(id: workspace.revision) { if workspace.revision > 0 { await load(current) } }
        .sheet(isPresented: $picking) {
            DatePickerSheet(selected: Binding(get: { anchor ?? today }, set: { anchor = $0 }), clock: workspace.clock)
        }
        .sheet(isPresented: $selling) { CheckoutView(appointment: nil) }
    }

    /// ‹ «Hoy» / «2 nov – 8 nov» / «Noviembre 2026» ›, the label opens a calendar.
    private func periodBar(_ current: LocalDate, today: LocalDate) -> some View {
        HStack(spacing: 12) {
            Button {
                Haptics.selection()
                anchor = SalesRules.step(span, anchor: current, by: -1)
            } label: {
                Image(systemName: "chevron.left").frame(width: 40, height: 40).background(Theme.fill, in: Circle())
            }
            .accessibilityLabel(L.Common.previous)
            Button {
                picking = true
            } label: {
                Text(SalesRules.label(span, anchor: current, today: today))
                    .font(.headline).foregroundStyle(Theme.ink)
                    .frame(maxWidth: .infinity)
            }
            .accessibilityHint(L.Agenda.pickDate)
            if !SalesRules.includes(span, anchor: current, day: today) {
                Button(L.Common.today) { anchor = today }
                    .font(.subheadline.weight(.semibold))
            }
            Button {
                Haptics.selection()
                anchor = SalesRules.step(span, anchor: current, by: 1)
            } label: {
                Image(systemName: "chevron.right").frame(width: 40, height: 40).background(Theme.fill, in: Circle())
            }
            .accessibilityLabel(L.Common.next)
        }
        .font(.body.weight(.semibold))
        .foregroundStyle(Theme.brand)
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var salesContent: some View {
        let summary = SalesRules.summary(sales)
        VStack(alignment: .leading, spacing: 10) {
            Text(L.Sales.collected).font(.subheadline.weight(.medium)).foregroundStyle(Theme.muted)
            Text(workspace.money(summary.collected))
                .font(.system(.largeTitle, design: .rounded).weight(.bold)).monospacedDigit().foregroundStyle(Theme.ink)
                .lineLimit(1).minimumScaleFactor(0.6)
            VStack(alignment: .leading, spacing: 2) {
                Text(summary.count == 0 ? L.Sales.count(0)
                     : "\(L.Sales.count(summary.count)) · \(L.Sales.average(workspace.money(summary.average)))")
                if summary.tips > 0 {
                    Text(L.Sales.includesTips(workspace.money(summary.tips)))
                }
            }
            .font(.footnote).foregroundStyle(Theme.muted)
            if !summary.byMethod.isEmpty {
                Divider().padding(.vertical, 4)
                Text(L.Sales.byMethod).font(.caption.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase)
                ForEach(summary.byMethod, id: \.method) { entry in
                    HStack(spacing: 10) {
                        Image(systemName: L.Payments.symbol(entry.method)).foregroundStyle(Theme.brand).frame(width: 22)
                        Text(L.Payments.method(entry.method)).font(.subheadline).foregroundStyle(Theme.ink)
                        Spacer()
                        Text(workspace.money(entry.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                    }
                    .padding(.vertical, 3)
                }
            }
        }
        .card(padding: 16)
        .plainRow()
        if sales.isEmpty {
            EmptyState(symbol: "banknote", title: L.Sales.empty, actionTitle: L.Checkout.newSale) { selling = true }
                .card()
                .plainRow()
        } else {
            ForEach(sales) { sale in
                SaleRow(sale: sale, withDay: span != .day)
                    .background { NavigationLink(value: Route.sale(sale)) { EmptyView() }.opacity(0) }
                    .plainRow(top: 4, bottom: 4)
            }
        }
    }

    @ViewBuilder
    private var depositsContent: some View {
        if deposits.isEmpty {
            EmptyState(symbol: "tray", title: L.Sales.emptyDeposits).card().plainRow()
        } else {
            ForEach(deposits) { deposit in
                DepositRow(deposit: deposit)
                    .plainRow(top: 4, bottom: 4)
            }
        }
    }

    private func load(_ current: LocalDate) async {
        loading = true
        defer { loading = false }
        let days = SalesRules.range(span, anchor: current)
        let range = workspace.clock.range(days.from, through: days.to)
        do {
            async let s = workspace.api.sales(workspace.id, from: range.from, to: range.to)
            async let d = workspace.api.deposits(workspace.id, from: range.from, to: range.to)
            (sales, deposits) = try await (s, d)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
}

/// One sale: customer, items, «#12 · 10:20 · Efectivo, Tarjeta», what came in.
struct SaleRow: View {
    let sale: Sale
    var withDay = false
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(sale.customer?.fullName ?? L.Checkout.noCustomer).font(.body.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(1)
                Text(SalesRules.items(sale)).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                Text(meta).font(.footnote).foregroundStyle(Theme.subtle).lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Text(workspace.money(sale.collected))
                    .font(.body.weight(.semibold)).monospacedDigit()
                    .foregroundStyle(sale.isVoid ? Theme.subtle : Theme.ink)
                    .strikethrough(sale.isVoid)
                if sale.isVoid {
                    Chip(text: L.Sales.void, tone: .danger)
                } else if sale.isInternal {
                    Chip(text: L.Sales.internal, tone: .neutral)
                }
            }
        }
        .card(padding: 14)
        .accessibilityElement(children: .combine)
    }

    private var meta: String {
        let clock = workspace.clock
        let time = clock.time(of: sale.createdAt)
        let when = withDay ? "\(Fmt.dayMedium(clock.localDate(of: sale.createdAt))) \(time)" : time
        let methods = SalesRules.methods(sale).map(L.Payments.method).joined(separator: ", ")
        return ["#\(sale.number)", when, methods.isEmpty ? nil : methods].compactMap { $0 }.joined(separator: " · ")
    }
}

/// One deposit: customer, «Lun 9 nov · 10:20 · Transferencia · Cita NLGN62», amount and state.
private struct DepositRow: View {
    let deposit: Deposit
    @Environment(Workspace.self) private var workspace

    var body: some View {
        let clock = workspace.clock
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(deposit.customer.map { "\($0.firstName) \($0.lastName)" } ?? "—").font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                Text([Fmt.dayMedium(clock.localDate(of: deposit.createdAt)), clock.time(of: deposit.createdAt),
                      L.Payments.method(deposit.method), deposit.appointmentCode.map(L.Sales.forAppointment)]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.footnote).foregroundStyle(Theme.muted)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Text(workspace.money(deposit.amount)).font(.body.weight(.semibold)).monospacedDigit()
                if deposit.status != "active" {
                    Chip(text: L.Sales.depositReturned, tone: .neutral)
                } else if let sale = deposit.appliedSale {
                    Chip(text: L.Sales.depositUsed(sale.number), tone: .info)
                } else {
                    Chip(text: L.Sales.depositAvailable, tone: .success)
                }
            }
        }
        .card(padding: 14)
        .accessibilityElement(children: .combine)
    }
}

/// Recording money the customer leaves in advance for an appointment.
struct DepositView: View {
    let appointment: Appointment
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var amount: Int?
    @State private var method = "transfer"
    @State private var note = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Avatar(name: appointment.customer.fullName, size: 36)
                        Text("\(appointment.customer.fullName) · \(appointment.serviceName) · \(appointment.code)")
                            .font(.subheadline.weight(.medium))
                    }
                } footer: {
                    Text(L.Checkout.depositHint)
                }
                Section(L.Checkout.amount) {
                    MoneyField(title: "0", amount: $amount, currency: workspace.currency)
                        .font(.title2.weight(.semibold))
                }
                Section(L.Checkout.method) {
                    Picker(L.Checkout.method, selection: $method) {
                        ForEach(CheckoutRules.counterMethods, id: \.self) { method in
                            Label(L.Payments.method(method), systemImage: L.Payments.symbol(method)).tag(method)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section(L.Checkout.note) {
                    TextField("\(L.Checkout.note) (\(L.Common.optional.lowercased()))", text: $note, axis: .vertical).lineLimit(1...3)
                }
                if let error {
                    Section { FieldError(text: error) }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 0) {
                    Divider()
                    PrimaryButton(title: L.Checkout.depositSave, symbol: "tray.and.arrow.down", busy: busy) {
                        Task { await save() }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                }
                .background(.bar)
            }
            .navigationTitle(L.Checkout.depositTitle)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
            }
        }
        .presentationDetents([.large])
        .interactiveDismissDisabled(busy)
    }

    private func save() async {
        guard let amount, amount > 0 else {
            error = L.Checkout.amountRequired
            Haptics.warning()
            return
        }
        error = nil
        busy = true
        defer { busy = false }
        var body: [String: JSON] = [
            "customerId": .string(appointment.customer.id),
            "appointmentId": .string(appointment.id),
            "amount": .int(amount),
            "method": .string(method),
        ]
        if let locationId = appointment.locationId { body["locationId"] = .string(locationId) }
        if let text = TextRules.clean(note) { body["note"] = .string(text) }
        do {
            try await workspace.api.createDeposit(workspace.id, body)
            toasts.show(L.Checkout.depositCreated)
            workspace.changed()
            dismiss()
        } catch {
            self.error = ErrorText.message(error)
        }
    }
}
