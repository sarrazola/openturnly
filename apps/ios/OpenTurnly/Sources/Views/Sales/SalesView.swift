import SwiftUI

/// The day's sales and deposits, with what came in by payment method.
struct SalesView: View {
    @Environment(Workspace.self) private var workspace
    @State private var day: LocalDate?
    @State private var tab: Tab = .sales
    @State private var sales: [Sale] = []
    @State private var deposits: [Deposit] = []
    @State private var loading = true
    @State private var error: Error?
    @State private var picking = false
    @State private var selling = false

    enum Tab: Hashable { case sales, deposits }

    var body: some View {
        let today = workspace.clock.today()
        let current = day ?? today
        List {
            Section {
                dayBar(current, today: today)
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
        .task(id: current) { await load(current) }
        .task(id: workspace.revision) { if workspace.revision > 0 { await load(current) } }
        .sheet(isPresented: $picking) {
            DatePickerSheet(selected: Binding(get: { day ?? today }, set: { day = $0 }), clock: workspace.clock)
        }
        .sheet(isPresented: $selling) { CheckoutView(appointment: nil) }
    }

    private func dayBar(_ current: LocalDate, today: LocalDate) -> some View {
        HStack(spacing: 12) {
            Button {
                Haptics.selection()
                day = current.adding(days: -1)
            } label: {
                Image(systemName: "chevron.left").frame(width: 40, height: 40).background(Theme.fill, in: Circle())
            }
            .accessibilityLabel(L.Common.previous)
            Button {
                picking = true
            } label: {
                VStack(spacing: 1) {
                    Text(Fmt.relativeDay(current, today: today)).font(.headline).foregroundStyle(Theme.ink)
                    if current != today {
                        Button(L.Common.today) { day = today }.font(.caption.weight(.semibold))
                    } else {
                        Text(Fmt.dayMedium(current)).font(.caption).foregroundStyle(Theme.muted)
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            Button {
                Haptics.selection()
                day = current.adding(days: 1)
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
        let paid = sales.filter { !$0.isVoid }
        let collected = paid.reduce(0) { $0 + $1.collected }
        let tips = paid.reduce(0) { $0 + $1.tipAmount }
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                StatTile(label: L.Sales.collected, value: workspace.money(collected),
                         detail: tips > 0 ? "\(workspace.money(tips)) \(L.Sales.tipsShort)" : nil, symbol: "banknote")
                StatTile(label: L.Sales.title, value: "\(paid.count)", detail: paid.isEmpty ? nil : L.Sales.average(workspace.money(paid.reduce(0) { $0 + $1.total } / paid.count)), symbol: "receipt", tint: Color(hex: "#0284c7"))
            }
            let methods = byMethod(paid)
            if !methods.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    Text(L.Sales.byMethod).font(.caption.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase)
                        .padding(.bottom, 4)
                    ForEach(methods, id: \.method) { entry in
                        HStack(spacing: 10) {
                            Image(systemName: L.Payments.symbol(entry.method)).foregroundStyle(Theme.brand).frame(width: 22)
                            Text(L.Payments.method(entry.method)).font(.subheadline).foregroundStyle(Theme.ink)
                            Spacer()
                            Text(workspace.money(entry.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                        }
                        .padding(.vertical, 7)
                    }
                }
                .card(padding: 14)
            }
        }
        .plainRow()
        if sales.isEmpty {
            EmptyState(symbol: "banknote", title: L.Sales.empty, actionTitle: L.Checkout.newSale) { selling = true }
                .card()
                .plainRow()
        } else {
            ForEach(sales) { sale in
                SaleRow(sale: sale)
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
                HStack(spacing: 12) {
                    IconTile(symbol: L.Payments.symbol(deposit.method), color: deposit.status == "active" ? Theme.brand : Theme.subtle, size: 38)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(deposit.customer.map { "\($0.firstName) \($0.lastName)" } ?? "—").font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                        Text([L.Payments.method(deposit.method), deposit.appointmentCode.map(L.Sales.forAppointment), workspace.clock.time(of: deposit.createdAt)]
                            .compactMap { $0 }.joined(separator: " · "))
                            .font(.footnote).foregroundStyle(Theme.muted)
                    }
                    Spacer()
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
                .plainRow(top: 4, bottom: 4)
            }
        }
    }

    private func byMethod(_ sales: [Sale]) -> [(method: String, amount: Int)] {
        var totals: [String: Int] = [:]
        for sale in sales { for payment in sale.payments { totals[payment.method, default: 0] += payment.amount } }
        return totals.map { ($0.key, $0.value) }.sorted { $0.amount > $1.amount }
    }

    private func load(_ current: LocalDate) async {
        loading = true
        defer { loading = false }
        let range = workspace.clock.range(current, through: current)
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

struct SaleRow: View {
    let sale: Sale
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(spacing: 12) {
            VStack(spacing: 2) {
                Text("#\(sale.number)").font(.subheadline.weight(.bold)).fontDesign(.rounded).foregroundStyle(Theme.brand)
                Text(workspace.clock.time(of: sale.createdAt)).font(.caption2).monospacedDigit().foregroundStyle(Theme.subtle)
            }
            .frame(width: 52, height: 48)
            .background(Theme.brandSoft, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                Text(sale.customer?.fullName ?? L.Checkout.noCustomer).font(.body.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(1)
                Text(sale.items.map(\.name).joined(separator: ", ")).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                HStack(spacing: 4) {
                    ForEach(Array(Set(sale.payments.map(\.method))).sorted(), id: \.self) { method in
                        Image(systemName: L.Payments.symbol(method)).font(.caption2).foregroundStyle(Theme.subtle)
                    }
                }
            }
            Spacer()
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
        .card(padding: 12)
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
                    HStack {
                        Avatar(name: appointment.customer.fullName, size: 36)
                        VStack(alignment: .leading) {
                            Text(appointment.customer.fullName).font(.body.weight(.semibold))
                            Text("\(appointment.serviceName) · \(appointment.code)").font(.footnote).foregroundStyle(Theme.muted)
                        }
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
                    TextField(L.Checkout.note, text: $note, axis: .vertical).lineLimit(1...3)
                }
                if let error {
                    Section { FieldError(text: error) }
                }
            }
            .navigationTitle(L.Checkout.depositTitle)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ProgressView() } else {
                        Button(L.Common.save) { Task { await save() } }.fontWeight(.semibold)
                    }
                }
            }
            .onAppear {
                if amount == nil {
                    let owed = AppointmentRules.owed(appointment)
                    amount = owed > 0 ? owed : nil
                }
            }
        }
        .presentationDetents([.large])
    }

    private func save() async {
        guard let amount, amount > 0 else {
            error = L.Checkout.amountRequired
            return
        }
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
