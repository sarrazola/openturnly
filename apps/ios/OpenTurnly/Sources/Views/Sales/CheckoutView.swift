import SwiftUI

/// Charging an appointment ("Cobrar" / "Cerrar venta") or a counter sale.
///
/// Top to bottom, like the web's checkout: the customer (and the location of a
/// counter sale), the lines, a discount or code, tips, the customer's deposits,
/// how they paid, the summary, a note. The bar at the bottom says whether the
/// payment matches and charges what is left to collect now. Payments must add up
/// to the total plus tips exactly, as the server requires.
struct CheckoutView: View {
    let appointment: Appointment?
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    @State private var customer: PickedCustomer?
    @State private var choosingCustomer = false
    @State private var locationId: String?
    @State private var lines: [CheckoutRules.Line] = []
    @State private var editingLine: CheckoutRules.Line.ID?
    @State private var pickingService = false
    @State private var pickingProduct = false
    @State private var products: [Product] = []
    @State private var isInternal = false
    @State private var manualDiscount: Int?
    @State private var codeText = ""
    @State private var codeError: String?
    @State private var discountCode: GiftCard?
    @State private var checkingCode = false
    @State private var tips: [Tip] = []
    @State private var deposits: [Deposit] = []
    @State private var usedDeposits: Set<String> = []
    @State private var depositsLoaded = false
    @State private var payments: [CheckoutRules.Payment] = [CheckoutRules.Payment(method: "cash", amount: 0)]
    @State private var paymentsEdited = false
    @State private var note = ""
    @State private var busy = false
    @State private var failure: String?

    struct Tip: Identifiable, Equatable {
        let id = UUID()
        var resourceId: String?
        var amount: Int?
    }

    init(appointment: Appointment? = nil) {
        self.appointment = appointment
        if let appointment {
            _customer = State(initialValue: PickedCustomer(id: appointment.customer.id, name: appointment.customer.fullName,
                                                           phone: appointment.customer.phone, email: appointment.customer.email))
            _locationId = State(initialValue: appointment.locationId)
            _lines = State(initialValue: [CheckoutRules.Line(
                kind: .service, serviceId: appointment.serviceId, appointmentId: appointment.id, name: appointment.serviceName,
                resourceId: appointment.resourceId, quantity: 1, unitPrice: appointment.priceAmount ?? 0,
                discountAmount: appointment.discountAmount
            )])
        }
    }

    // MARK: Arithmetic

    private var subtotal: Int { CheckoutRules.subtotal(lines) }
    private var discount: Int {
        if let code = discountCode { return CheckoutRules.codeDiscount(percent: code.percent, amount: code.amount, subtotal: subtotal) }
        return min(manualDiscount ?? 0, subtotal)
    }
    private var tipTotal: Int { tips.reduce(0) { $0 + ($1.amount ?? 0) } }
    private var available: [Deposit] { deposits.filter(\.isAvailable) }
    private var depositTotal: Int { available.filter { usedDeposits.contains($0.id) }.reduce(0) { $0 + $1.amount } }
    private var summary: CheckoutRules.Summary {
        CheckoutRules.summary(lines: lines, discount: discount, tips: tipTotal, deposits: depositTotal)
    }
    private var due: Int { CheckoutRules.due(total: summary.total, tips: tipTotal) }
    private var difference: Int { CheckoutRules.difference(due: due, deposits: depositTotal, payments: payments) }

    private var location: Location? {
        workspace.location(locationId) ?? workspace.activeLocations.first
    }
    private var offeredServices: [Service] { BookingRules.offered(workspace.services, location: location) }
    private var forSale: [Product] { products.filter { $0.active && $0.forSale } }
    private var team: [Professional] {
        workspace.professionals.filter { professional in
            guard professional.active || lines.contains(where: { $0.resourceId == professional.id }) else { return false }
            guard let ids = location?.resourceIds else { return true }
            return ids.contains(professional.id)
        }
    }

    private var title: String { appointment == nil ? L.Checkout.newSale : L.Checkout.titleAppointment }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    customerBlock
                    linesBlock
                    discountBlock
                    tipsBlock
                    if !available.isEmpty { depositsBlock }
                    paymentsBlock
                    summaryCard
                    extrasBlock
                }
                .padding(16)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.canvas)
            .bottomBar { submitBar }
            .navigationTitle(title)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
            .task { await prepare() }
            .task(id: customer?.id) { await loadDeposits() }
            .onChange(of: summary.toCollect) { _, _ in syncPayment() }
            .sheet(isPresented: $choosingCustomer) {
                CustomerChooser { picked in customer = picked }
            }
            .sheet(isPresented: $pickingService) {
                ServicePicker(title: L.Checkout.chooseService, services: offeredServices) { addService($0) }
            }
            .sheet(isPresented: $pickingProduct) {
                ProductPicker(products: forSale, isInternal: isInternal) { addProduct($0) }
            }
            .sheet(item: Binding(get: { editingLine.map(LineID.init) }, set: { editingLine = $0?.id })) { item in
                if let index = lines.firstIndex(where: { $0.id == item.id }) {
                    LineEditor(line: $lines[index], team: team, currency: workspace.currency) {
                        let id = item.id
                        editingLine = nil
                        withAnimation { lines.removeAll { $0.id == id } }
                    }
                }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    // MARK: Customer and location

    private var customerBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.customer)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    if let customer {
                        Avatar(name: customer.name, size: 44)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(customer.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                            if let appointment {
                                Text("\(appointment.code) · \(workspace.clock.time(of: appointment.startsAt)) · \(appointment.serviceName)")
                                    .font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                            }
                        }
                        Spacer()
                        if appointment == nil {
                            Button(L.Common.remove) { self.customer = nil }.font(.footnote.weight(.semibold))
                        }
                    } else {
                        Button {
                            choosingCustomer = true
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "person.crop.circle.badge.plus").font(.title2).foregroundStyle(Theme.brand)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L.Checkout.pickCustomer).font(.body.weight(.semibold)).foregroundStyle(Theme.brand)
                                    Text("\(L.Checkout.customer) · \(L.Common.optional)").font(.footnote).foregroundStyle(Theme.muted)
                                }
                                Spacer()
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                if appointment == nil && workspace.activeLocations.count > 1 {
                    Divider().padding(.vertical, 10)
                    MenuRow(label: L.Checkout.location, symbol: "mappin.and.ellipse",
                            options: workspace.activeLocations.map { (value: Optional($0.id), title: $0.name, subtitle: $0.address) },
                            selection: Binding(get: { location?.id }, set: { locationId = $0 }))
                }
            }
            .card(padding: 14)
        }
    }

    // MARK: Lines

    private var linesBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.items)
            VStack(alignment: .leading, spacing: 0) {
                if lines.isEmpty {
                    Text(L.Checkout.noLines).font(.subheadline).foregroundStyle(Theme.muted).padding(.vertical, 6)
                }
                ForEach(Array(lines.enumerated()), id: \.element.id) { index, line in
                    if index > 0 { Divider() }
                    Button {
                        editingLine = line.id
                    } label: {
                        LineRow(line: line)
                    }
                    .buttonStyle(.plain)
                }
                HStack(spacing: 10) {
                    SecondaryButton(title: L.Checkout.addService, symbol: "plus") { pickingService = true }
                    if !forSale.isEmpty {
                        SecondaryButton(title: L.Checkout.addProduct, symbol: "shippingbox") { pickingProduct = true }
                    }
                }
                .padding(.top, 10)
            }
            .card(padding: 14)
        }
    }

    // MARK: Discount

    private var discountBlock: some View {
        FormBlock(title: L.Checkout.discount) {
            if let code = discountCode {
                HStack {
                    Chip(text: code.code, tone: .success, symbol: "ticket.fill")
                    Text("−\(workspace.money(discount))").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.success)
                    Spacer()
                    Button(L.Checkout.removeCode) { withAnimation { discountCode = nil } }.font(.footnote.weight(.semibold))
                }
                .frame(minHeight: 40)
                Divider().padding(.vertical, 6)
            }
            // The field stays: a gift card can still pay after a discount code.
            HStack(spacing: 8) {
                Image(systemName: "ticket").foregroundStyle(Theme.subtle)
                TextField(L.Checkout.code, text: $codeText)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .onSubmit { Task { await applyCode() } }
                    .onChange(of: codeText) { _, _ in codeError = nil }
                Button {
                    Task { await applyCode() }
                } label: {
                    if checkingCode { ProgressView() } else { Text(L.Checkout.apply).fontWeight(.semibold) }
                }
                .disabled(codeText.trimmingCharacters(in: .whitespaces).count < 3 || checkingCode)
            }
            .frame(minHeight: 40)
            FieldError(text: codeError)
            if discountCode == nil {
                Divider().padding(.vertical, 6)
                HStack {
                    Text(L.Checkout.discount).font(.subheadline).foregroundStyle(Theme.muted)
                    Spacer()
                    MoneyField(title: "0", amount: $manualDiscount, currency: workspace.currency)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 160)
                }
                .frame(minHeight: 40)
            }
        }
    }

    // MARK: Tips

    private var tipsBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.tips)
            VStack(alignment: .leading, spacing: 0) {
                ForEach($tips) { $tip in
                    HStack(spacing: 10) {
                        Menu {
                            ForEach(team) { professional in
                                Button(professional.name) { tip.resourceId = professional.id }
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Circle().fill(workspace.color(for: tip.resourceId)).frame(width: 8, height: 8)
                                Text(workspace.professional(tip.resourceId)?.name ?? L.Booking.professional)
                                    .foregroundStyle(Theme.ink)
                                Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(Theme.subtle)
                            }
                            .font(.subheadline)
                        }
                        Spacer()
                        MoneyField(title: "0", amount: $tip.amount, currency: workspace.currency)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: 130)
                        Button {
                            withAnimation { tips.removeAll { $0.id == tip.id } }
                        } label: {
                            Image(systemName: "minus.circle.fill").foregroundStyle(Theme.danger)
                        }
                        .accessibilityLabel(L.Common.remove)
                    }
                    .frame(minHeight: 44)
                    Divider()
                }
                Button {
                    let first = appointment?.resourceId.flatMap { id in team.first { $0.id == id } } ?? team.first
                    withAnimation { tips.append(Tip(resourceId: first?.id, amount: nil)) }
                } label: {
                    Label(L.Checkout.addTip, systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold))
                }
                .disabled(team.isEmpty)
                .frame(minHeight: 40)
                if tips.isEmpty {
                    Text(L.Checkout.tipsHint).font(.caption).foregroundStyle(Theme.subtle)
                }
            }
            .card(padding: 14)
        }
    }

    // MARK: Deposits

    private var depositsBlock: some View {
        FormBlock(title: L.Checkout.deposits) {
            ForEach(Array(available.enumerated()), id: \.element.id) { index, deposit in
                if index > 0 { Divider() }
                Toggle(isOn: Binding(
                    get: { usedDeposits.contains(deposit.id) },
                    set: { on in
                        if on { usedDeposits.insert(deposit.id) } else { usedDeposits.remove(deposit.id) }
                    }
                )) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(workspace.money(deposit.amount)).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                        Text([L.Checkout.useDeposit(Fmt.dayShort(workspace.clock.localDate(of: deposit.createdAt))),
                              L.Payments.method(deposit.method), deposit.appointmentCode.map(L.Sales.forAppointment)]
                            .compactMap { $0 }.joined(separator: " · "))
                            .font(.footnote).foregroundStyle(Theme.muted)
                    }
                }
                .tint(Theme.brand)
                .frame(minHeight: 44)
            }
        }
    }

    // MARK: Payments

    private var paymentsBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.payments)
            VStack(alignment: .leading, spacing: 0) {
                ForEach($payments) { $payment in
                    HStack(spacing: 10) {
                        if payment.method == "gift_card" {
                            Label("Gift card \(payment.giftCardCode ?? "")", systemImage: "giftcard").font(.subheadline).foregroundStyle(Theme.ink)
                                .lineLimit(1).truncationMode(.middle)
                        } else {
                            Menu {
                                ForEach(CheckoutRules.counterMethods, id: \.self) { method in
                                    Button {
                                        payment.method = method
                                    } label: {
                                        Label(L.Payments.method(method), systemImage: L.Payments.symbol(method))
                                    }
                                }
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: L.Payments.symbol(payment.method)).foregroundStyle(Theme.brand)
                                    Text(L.Payments.method(payment.method)).foregroundStyle(Theme.ink)
                                    Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(Theme.subtle)
                                }
                                .font(.subheadline)
                            }
                        }
                        Spacer()
                        MoneyField(title: "0", amount: Binding(get: { payment.amount }, set: { value in
                            if value != payment.amount { paymentsEdited = true }
                            payment.amount = value ?? 0
                        }), currency: workspace.currency)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 140)
                        if payments.count > 1 {
                            Button {
                                paymentsEdited = true
                                withAnimation { payments.removeAll { $0.id == payment.id } }
                            } label: {
                                Image(systemName: "minus.circle.fill").foregroundStyle(Theme.danger)
                            }
                            .accessibilityLabel(L.Common.remove)
                        }
                    }
                    .frame(minHeight: 44)
                    Divider()
                }
                Button {
                    paymentsEdited = true
                    withAnimation { payments = CheckoutRules.split(payments, difference: difference) }
                } label: {
                    Label(L.Checkout.split, systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold))
                }
                .disabled(payments.count >= CheckoutRules.maxPayments)
                .frame(minHeight: 40)
            }
            .card(padding: 14)
        }
    }

    // MARK: Summary and extras

    private var summaryCard: some View {
        let summary = self.summary
        return VStack(spacing: 0) {
            InfoRow(label: L.Checkout.subtotal, value: workspace.money(summary.subtotal))
            if summary.discount > 0 {
                InfoRow(label: L.Checkout.discount, value: "−\(workspace.money(summary.discount))", valueColor: Theme.success)
            }
            InfoRow(label: L.Common.total, value: workspace.money(summary.total))
            if summary.tips > 0 {
                InfoRow(label: L.Checkout.tips, value: "+\(workspace.money(summary.tips))")
            }
            if summary.deposits > 0 {
                InfoRow(label: L.Checkout.deposits, value: "−\(workspace.money(summary.deposits))")
            }
            Divider().padding(.vertical, 4)
            HStack {
                Text(L.Checkout.toCollect).font(.headline).foregroundStyle(Theme.ink)
                Spacer()
                Text(workspace.money(summary.toCollect)).font(.title2.weight(.bold)).fontDesign(.rounded).monospacedDigit().foregroundStyle(Theme.ink)
            }
            .padding(.vertical, 6)
        }
        .card(padding: 14)
    }

    private var extrasBlock: some View {
        FormBlock(title: L.Checkout.note) {
            TextField("\(L.Checkout.note) (\(L.Common.optional.lowercased()))", text: $note, axis: .vertical)
                .lineLimit(1...4)
                .frame(minHeight: 36)
            if appointment == nil {
                Divider().padding(.vertical, 6)
                Toggle(isOn: $isInternal) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L.Checkout.internalSale).font(.subheadline.weight(.medium))
                        Text(L.Checkout.internalHint).font(.caption).foregroundStyle(Theme.muted)
                    }
                }
                .tint(Theme.brand)
                .onChange(of: isInternal) { _, value in reprice(forTeam: value) }
            }
        }
    }

    private var submitBar: some View {
        let submit = CheckoutRules.submit(toCollect: summary.toCollect, forAppointment: appointment != nil)
        return VStack(spacing: 0) {
            Divider()
            VStack(spacing: 8) {
                statusLine
                PrimaryButton(title: submitTitle(submit), symbol: "banknote", busy: busy, disabled: lines.isEmpty || difference != 0) {
                    Task { await self.submit() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(.bar)
    }

    private func submitTitle(_ submit: CheckoutRules.Submit) -> String {
        switch submit {
        case .closeSale: return L.Checkout.closeSale
        case .charge(let amount): return L.Checkout.chargeAmount(workspace.money(amount))
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        Group {
            if let failure {
                Label(failure, systemImage: "exclamationmark.triangle.fill").foregroundStyle(Theme.danger)
            } else if lines.isEmpty {
                Label(L.Checkout.noLines, systemImage: "exclamationmark.circle.fill").foregroundStyle(Theme.warning)
            } else {
                switch CheckoutRules.balance(difference) {
                case .balanced:
                    Label(L.Checkout.balanced, systemImage: "checkmark.seal.fill").foregroundStyle(Theme.success)
                case .missing(let amount):
                    Label(L.Checkout.missing(workspace.money(amount)), systemImage: "exclamationmark.circle.fill").foregroundStyle(Theme.warning)
                case .over(let amount):
                    Label(L.Checkout.over(workspace.money(amount)), systemImage: "exclamationmark.circle.fill").foregroundStyle(Theme.warning)
                }
            }
        }
        .font(.footnote.weight(.semibold))
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }

    // MARK: Work

    private func prepare() async {
        await workspace.loadCatalog()
        if locationId == nil { locationId = workspace.activeLocations.first?.id }
        syncPayment()
        do { products = try await workspace.api.products(workspace.id) } catch {}
    }

    private func loadDeposits() async {
        guard let customer else {
            deposits = []
            usedDeposits = []
            return
        }
        do {
            deposits = try await workspace.api.deposits(workspace.id, customerId: customer.id)
            if !depositsLoaded, let appointment {
                usedDeposits = Set(deposits.filter { $0.isAvailable && $0.appointmentId == appointment.id }.map(\.id))
            }
            depositsLoaded = true
        } catch {
            deposits = []
        }
        syncPayment()
    }

    /// The first counter payment follows what is left to collect until someone edits an amount.
    private func syncPayment() {
        guard !paymentsEdited, let index = payments.firstIndex(where: { $0.method != "gift_card" }) else { return }
        let others = payments.enumerated().filter { $0.offset != index }.reduce(0) { $0 + $1.element.amount }
        payments[index].amount = max(0, summary.toCollect - others)
    }

    private func addService(_ service: Service) {
        let resource = appointment?.resourceId.flatMap { id in team.first { $0.id == id }?.id }
        withAnimation {
            lines.append(CheckoutRules.Line(kind: .service, serviceId: service.id, name: service.name,
                                            resourceId: resource, unitPrice: service.priceAmount ?? 0))
        }
    }

    private func addProduct(_ product: Product) {
        withAnimation {
            lines.append(CheckoutRules.Line(kind: .product, productId: product.id, name: product.name, resourceId: appointment?.resourceId,
                                            unitPrice: price(of: product, forTeam: isInternal)))
        }
    }

    private func price(of product: Product, forTeam: Bool) -> Int {
        forTeam ? (product.internalPriceAmount ?? product.priceAmount) : product.priceAmount
    }

    /// Switching to or from an internal sale reprices products still at their price.
    private func reprice(forTeam: Bool) {
        for index in lines.indices where lines[index].kind == .product {
            guard let product = products.first(where: { $0.id == lines[index].productId }) else { continue }
            if lines[index].unitPrice == price(of: product, forTeam: !forTeam) {
                lines[index].unitPrice = price(of: product, forTeam: forTeam)
            }
        }
    }

    private func applyCode() async {
        let code = codeText.trimmingCharacters(in: .whitespaces)
        guard code.count >= 3 else { return }
        checkingCode = true
        codeError = nil
        defer { checkingCode = false }
        do {
            let card = try await workspace.api.lookupGiftCard(workspace.id, code: code)
            guard card.state == "active" else {
                codeError = L.Checkout.codeState(L.GiftCards.state(card.state).lowercased())
                return
            }
            if card.kind == "discount" {
                withAnimation { discountCode = card }
            } else if !payments.contains(where: { $0.giftCardId == card.id }) {
                let gifts = payments.filter { $0.method == "gift_card" }.reduce(0) { $0 + $1.amount }
                let amount = CheckoutRules.giftCardAmount(balance: card.remaining ?? 0, due: summary.toCollect - gifts)
                let gift = CheckoutRules.Payment(method: "gift_card", amount: amount, giftCardId: card.id, giftCardCode: card.code)
                withAnimation { payments.insert(gift, at: 0) }
                syncPayment()
                toasts.show(L.Checkout.cardLeft(workspace.money(max(0, (card.remaining ?? 0) - amount))))
            }
            codeText = ""
        } catch {
            codeError = ErrorText.message(error)
        }
    }

    private func submit() async {
        failure = nil
        guard !lines.isEmpty else {
            failure = L.Checkout.noLines
            return
        }
        guard difference == 0 else {
            failure = L.Checkout.paymentsMismatch
            return
        }
        busy = true
        defer { busy = false }
        var body: [String: JSON] = [
            "customerId": .opt(customer?.id),
            "internal": .bool(appointment == nil && isInternal),
            "discountAmount": .int(discount),
            "items": .array(lines.map { line in
                var item: [String: JSON] = [
                    "kind": .string(line.kind.rawValue),
                    "serviceId": .opt(line.serviceId),
                    "resourceId": .opt(line.resourceId),
                    "quantity": .int(line.quantity),
                    "unitPrice": .int(line.unitPrice),
                    "discountAmount": .int(line.discountAmount),
                ]
                if let productId = line.productId { item["productId"] = .string(productId) }
                if let appointmentId = line.appointmentId { item["appointmentId"] = .string(appointmentId) }
                return .object(item)
            }),
            "tips": .array(tips.compactMap { tip in
                guard let resourceId = tip.resourceId, let amount = tip.amount, amount > 0 else { return nil }
                return ["resourceId": .string(resourceId), "amount": .int(amount)]
            }),
            "payments": .array(
                available.filter { usedDeposits.contains($0.id) }.map {
                    ["method": "deposit", "amount": .int($0.amount), "depositId": .string($0.id)]
                } + CheckoutRules.payable(payments).map { payment in
                    var entry: [String: JSON] = ["method": .string(payment.method), "amount": .int(payment.amount)]
                    if let id = payment.giftCardId { entry["giftCardId"] = .string(id) }
                    return .object(entry)
                }
            ),
        ]
        if let location { body["locationId"] = .string(location.id) }
        if let code = discountCode { body["giftCardId"] = .string(code.id) }
        if let text = TextRules.clean(note) { body["note"] = .string(text) }
        do {
            let sale = try await workspace.api.createSale(workspace.id, body)
            toasts.show(L.Checkout.done(sale.number))
            workspace.changed()
            dismiss()
        } catch {
            failure = ErrorText.message(error)
            Haptics.error()
        }
    }
}

/// Lets a line's id drive `.sheet(item:)`.
private struct LineID: Identifiable {
    let id: CheckoutRules.Line.ID
}

/// One line of a sale as a row: what, «1 × $ 25.000 · Camila», the booking
/// discount, the line total.
private struct LineRow: View {
    let line: CheckoutRules.Line
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: line.kind == .product ? "shippingbox.fill" : "scissors")
                .foregroundStyle(Theme.brand).frame(width: 22).padding(.top, 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(line.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(2)
                Text(["\(line.quantity) × \(workspace.money(line.unitPrice))", workspace.professional(line.resourceId)?.name]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.footnote).foregroundStyle(Theme.muted)
                if line.discountAmount > 0 {
                    Text("−\(workspace.money(line.discountAmount))").font(.footnote).foregroundStyle(Theme.success)
                }
            }
            Spacer(minLength: 8)
            Text(workspace.money(line.total)).font(.body.weight(.semibold)).monospacedDigit().foregroundStyle(Theme.ink)
            Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.subtle).padding(.top, 4)
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// A line's price, quantity and professional; «Quitar» unless it is the appointment's own.
private struct LineEditor: View {
    @Binding var line: CheckoutRules.Line
    let team: [Professional]
    let currency: String
    let remove: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack {
                        Text(L.Checkout.unitPrice)
                        MoneyField(title: "0", amount: Binding(get: { line.unitPrice }, set: { line.unitPrice = $0 ?? 0 }), currency: currency)
                            .multilineTextAlignment(.trailing)
                    }
                    if line.appointmentId == nil {
                        Stepper(value: $line.quantity, in: 1...999) {
                            HStack {
                                Text(L.Checkout.quantity)
                                Spacer()
                                Text("\(line.quantity)").monospacedDigit().foregroundStyle(Theme.muted)
                            }
                        }
                    }
                    Picker(L.Booking.professional, selection: $line.resourceId) {
                        Text(L.Checkout.nobody).tag(String?.none)
                        ForEach(team) { professional in
                            Text(professional.name).tag(Optional(professional.id))
                        }
                    }
                }
                if line.appointmentId == nil {
                    Section {
                        Button(L.Common.remove, role: .destructive, action: remove)
                    }
                }
            }
            .navigationTitle(line.name)
            .navigationBarTitleDisplayMode(.inline)
            .keyboardDoneButton()
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(L.Common.done) { dismiss() }.fontWeight(.semibold) }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Products for sale with their stock and price (the team's price for an internal sale).
private struct ProductPicker: View {
    let products: [Product]
    let isInternal: Bool
    let pick: (Product) -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(products) { product in
                Button {
                    Haptics.selection()
                    pick(product)
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(product.name).foregroundStyle(Theme.ink)
                            Text(L.Inventory.inStock(product.totalStock)).font(.footnote).foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        Text(workspace.money(isInternal ? (product.internalPriceAmount ?? product.priceAmount) : product.priceAmount))
                            .font(.subheadline.weight(.semibold)).monospacedDigit().foregroundStyle(Theme.ink)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .navigationTitle(L.Checkout.chooseProduct)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Search a customer for a counter sale.
struct CustomerChooser: View {
    let pick: (PickedCustomer) -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var results: [CustomerSummary] = []

    var body: some View {
        NavigationStack {
            List(results.prefix(8)) { found in
                Button {
                    pick(PickedCustomer(id: found.id, name: found.fullName, phone: found.phone, email: found.email))
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        Avatar(name: found.fullName, size: 36)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(found.fullName).foregroundStyle(Theme.ink)
                            Text(Phone.format(found.phone).isEmpty ? (found.email ?? "") : Phone.format(found.phone))
                                .font(.caption).foregroundStyle(Theme.muted)
                        }
                    }
                }
            }
            .overlay {
                if results.isEmpty {
                    Text(query.count < 2 ? L.Booking.typeToSearch : L.Booking.noCustomerFound)
                        .font(.subheadline).foregroundStyle(Theme.muted)
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: L.Booking.searchCustomer)
            .navigationTitle(L.Checkout.pickCustomer)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
            .task(id: query) {
                let trimmed = query.trimmingCharacters(in: .whitespaces)
                guard trimmed.count >= 2 else { results = []; return }
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled else { return }
                results = (try? await workspace.api.customers(workspace.id, query: trimmed)) ?? []
            }
        }
        .presentationDetents([.large])
    }
}
