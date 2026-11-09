import SwiftUI

/// Charging an appointment ("Cobrar" / "Cerrar venta") or a counter sale.
///
/// The services done, a discount or code, tips for the professionals and how the
/// customer paid, split across methods if needed. Deposits the customer left are
/// offered first, and the appointment's own deposits come ticked. Payments must
/// add up to the total plus tips exactly, as the server requires.
struct CheckoutView: View {
    let appointment: Appointment?
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    @State private var customer: PickedCustomer?
    @State private var choosingCustomer = false
    @State private var locationId: String?
    @State private var lines: [CheckoutRules.Line] = []
    @State private var products: [Product] = []
    @State private var isInternal = false
    @State private var manualDiscount: Int?
    @State private var codeText = ""
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
    private var total: Int { CheckoutRules.total(subtotal: subtotal, discount: discount) }
    private var tipTotal: Int { tips.reduce(0) { $0 + ($1.amount ?? 0) } }
    private var due: Int { CheckoutRules.due(total: total, tips: tipTotal) }
    private var available: [Deposit] { deposits.filter(\.isAvailable) }
    private var depositTotal: Int { available.filter { usedDeposits.contains($0.id) }.reduce(0) { $0 + $1.amount } }
    private var remaining: Int { CheckoutRules.remaining(due: due, deposits: depositTotal) }
    private var difference: Int { CheckoutRules.difference(due: due, deposits: depositTotal, payments: payments) }

    private var location: Location? {
        workspace.location(locationId) ?? workspace.activeLocations.first
    }
    private var offeredServices: [Service] { BookingRules.offered(workspace.services, location: location) }
    private var forSale: [Product] { products.filter { $0.active && $0.forSale } }
    private var team: [Professional] {
        workspace.professionals.filter { professional in
            guard let ids = location?.resourceIds else { return true }
            return ids.contains(professional.id)
        }
    }

    private var title: String {
        if let appointment { return L.Checkout.titleAppointment(appointment.customer.firstName) }
        return L.Checkout.newSale
    }

    private var closingOnly: Bool {
        appointment.map(AppointmentRules.covered) ?? false
    }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    customerCard
                    linesBlock
                    discountBlock
                    tipsBlock
                    if !available.isEmpty { depositsBlock }
                    paymentsBlock
                    summaryCard
                    extrasBlock
                    if let failure {
                        NoticeBanner(text: failure, symbol: "exclamationmark.triangle.fill", tone: .danger)
                    }
                }
                .padding(16)
                .padding(.bottom, 90)
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
            .onChange(of: remaining) { _, value in syncPayment(value) }
            .sheet(isPresented: $choosingCustomer) {
                CustomerChooser { picked in customer = picked }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    // MARK: Blocks

    private var customerCard: some View {
        HStack(spacing: 12) {
            if let customer {
                Avatar(name: customer.name, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(customer.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                    if let appointment {
                        Text("\(appointment.code) · \(workspace.clock.time(of: appointment.startsAt))")
                            .font(.footnote).foregroundStyle(Theme.muted)
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
        .card(padding: 14)
    }

    private var linesBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.items)
            VStack(spacing: 10) {
                ForEach($lines) { $line in
                    LineEditor(line: $line, services: offeredServices, products: forSale, team: team, currency: workspace.currency,
                               removable: line.appointmentId == nil) {
                        withAnimation { lines.removeAll { $0.id == line.id } }
                    }
                }
                HStack(spacing: 10) {
                    SecondaryButton(title: L.Checkout.addService, symbol: "plus") { addService() }
                    if !forSale.isEmpty {
                        SecondaryButton(title: L.Checkout.addProduct, symbol: "shippingbox") { addProduct() }
                    }
                }
            }
        }
    }

    private var discountBlock: some View {
        FormBlock(title: L.Checkout.discount) {
            if let code = discountCode {
                HStack {
                    Chip(text: code.code, tone: .success, symbol: "ticket.fill")
                    Text("−\(workspace.money(discount))").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.success)
                    Spacer()
                    Button(L.Checkout.removeCode) { withAnimation { discountCode = nil } }.font(.footnote.weight(.semibold))
                }
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "ticket").foregroundStyle(Theme.subtle)
                    TextField(L.Checkout.code, text: $codeText)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .onSubmit { Task { await applyCode() } }
                    Button {
                        Task { await applyCode() }
                    } label: {
                        if checkingCode { ProgressView() } else { Text(L.Checkout.apply).fontWeight(.semibold) }
                    }
                    .disabled(codeText.trimmingCharacters(in: .whitespaces).count < 3 || checkingCode)
                }
                .frame(minHeight: 40)
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
                    withAnimation {
                        tips.append(Tip(resourceId: lines.first?.resourceId ?? team.first?.id, amount: nil))
                    }
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
                        Text(L.Checkout.useDeposit(Fmt.dayShort(workspace.clock.localDate(of: deposit.createdAt))) + " · " + L.Payments.method(deposit.method))
                            .font(.footnote).foregroundStyle(Theme.muted)
                    }
                }
                .tint(Theme.brand)
                .frame(minHeight: 44)
            }
        }
    }

    private var paymentsBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Checkout.payments)
            VStack(alignment: .leading, spacing: 0) {
                ForEach($payments) { $payment in
                    HStack(spacing: 10) {
                        if payment.method == "gift_card" {
                            Label(payment.giftCardCode ?? "Gift card", systemImage: "giftcard").font(.subheadline).foregroundStyle(Theme.ink)
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
                    let missing = max(0, -difference)
                    withAnimation { payments.append(CheckoutRules.Payment(method: "card", amount: missing)) }
                } label: {
                    Label(L.Checkout.addPayment, systemImage: "plus.circle.fill").font(.subheadline.weight(.semibold))
                }
                .frame(minHeight: 40)
            }
            .card(padding: 14)
        }
    }

    private var summaryCard: some View {
        VStack(spacing: 0) {
            InfoRow(label: L.Checkout.subtotal, value: workspace.money(subtotal))
            if discount > 0 {
                InfoRow(label: L.Checkout.discount, value: "−\(workspace.money(discount))", valueColor: Theme.success)
            }
            if tipTotal > 0 {
                InfoRow(label: L.Checkout.tips, value: workspace.money(tipTotal))
            }
            Divider().padding(.vertical, 4)
            HStack {
                Text(L.Common.total).font(.headline).foregroundStyle(Theme.ink)
                Spacer()
                Text(workspace.money(due)).font(.title2.weight(.bold)).fontDesign(.rounded).monospacedDigit().foregroundStyle(Theme.ink)
            }
            .padding(.vertical, 6)
            if depositTotal > 0 {
                InfoRow(label: L.Checkout.deposits, value: "−\(workspace.money(depositTotal))")
                InfoRow(label: L.Checkout.toCollect, value: workspace.money(remaining))
            }
            balanceLine
        }
        .card(padding: 14)
    }

    @ViewBuilder
    private var balanceLine: some View {
        if difference == 0 {
            Label(L.Checkout.balanced, systemImage: "checkmark.seal.fill")
                .font(.footnote.weight(.semibold)).foregroundStyle(Theme.success)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else if difference < 0 {
            Label(L.Checkout.missing(workspace.money(-difference)), systemImage: "exclamationmark.circle.fill")
                .font(.footnote.weight(.semibold)).foregroundStyle(Theme.warning)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Label(L.Checkout.over(workspace.money(difference)), systemImage: "exclamationmark.circle.fill")
                .font(.footnote.weight(.semibold)).foregroundStyle(Theme.warning)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var extrasBlock: some View {
        FormBlock(title: L.Checkout.note) {
            TextField(L.Checkout.note, text: $note, axis: .vertical)
                .lineLimit(1...4)
                .frame(minHeight: 36)
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

    private var submitBar: some View {
        VStack(spacing: 0) {
            Divider()
            PrimaryButton(title: closingOnly && remaining == 0 ? L.Checkout.closeSale : L.Checkout.chargeAmount(workspace.money(due)),
                          symbol: "banknote", busy: busy, disabled: lines.isEmpty || difference != 0) {
                Task { await submit() }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(.bar)
    }

    // MARK: Work

    private func prepare() async {
        await workspace.loadCatalog()
        if locationId == nil { locationId = workspace.activeLocations.first?.id }
        syncPayment(remaining)
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
        syncPayment(remaining)
    }

    /// The single payment follows what is left to pay until someone edits it.
    private func syncPayment(_ value: Int) {
        guard !paymentsEdited, payments.count == 1, payments[0].method != "gift_card" else { return }
        payments[0].amount = value
    }

    private func addService() {
        guard let service = offeredServices.first else { return }
        withAnimation {
            lines.append(CheckoutRules.Line(kind: .service, serviceId: service.id, name: service.name,
                                            resourceId: appointment?.resourceId, unitPrice: service.priceAmount ?? 0))
        }
    }

    private func addProduct() {
        guard let product = forSale.first else { return }
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
        defer { checkingCode = false }
        do {
            let card = try await workspace.api.lookupGiftCard(workspace.id, code: code)
            guard card.state == "active" else {
                toasts.show(L.Checkout.codeState(L.GiftCards.state(card.state).lowercased()), error: true)
                return
            }
            if card.kind == "discount" {
                withAnimation { discountCode = card }
            } else if !payments.contains(where: { $0.giftCardId == card.id }) {
                let amount = min(card.remaining ?? 0, remaining)
                paymentsEdited = true
                var next = [CheckoutRules.Payment(method: "gift_card", amount: amount, giftCardId: card.id, giftCardCode: card.code)]
                if payments.count == 1 {
                    var rest = payments[0]
                    rest.amount = max(0, remaining - amount)
                    next.append(rest)
                } else {
                    next.append(contentsOf: payments)
                }
                withAnimation { payments = next }
                toasts.show(L.Checkout.cardLeft(workspace.money(card.remaining ?? 0)))
            }
            codeText = ""
        } catch {
            toasts.error(error)
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
            "internal": .bool(isInternal),
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

/// One line of a sale: what, who did it, how many and at what price.
private struct LineEditor: View {
    @Binding var line: CheckoutRules.Line
    let services: [Service]
    let products: [Product]
    let team: [Professional]
    let currency: String
    let removable: Bool
    let remove: () -> Void
    @Environment(Workspace.self) private var workspace

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Image(systemName: line.kind == .product ? "shippingbox.fill" : "scissors").foregroundStyle(Theme.brand)
                if line.appointmentId != nil {
                    Text(line.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                } else if line.kind == .service {
                    Menu {
                        ForEach(services) { service in
                            Button(service.name) {
                                line.serviceId = service.id
                                line.name = service.name
                                line.unitPrice = service.priceAmount ?? 0
                            }
                        }
                    } label: { menuLabel(line.name) }
                } else {
                    Menu {
                        ForEach(products) { product in
                            Button("\(product.name) · \(L.Inventory.inStock(product.totalStock))") {
                                line.productId = product.id
                                line.name = product.name
                                line.unitPrice = product.priceAmount
                            }
                        }
                    } label: { menuLabel(line.name) }
                }
                Spacer()
                Text(workspace.money(line.total)).font(.body.weight(.semibold)).monospacedDigit().foregroundStyle(Theme.ink)
                if removable {
                    Button(action: remove) {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(Theme.subtle)
                    }
                    .accessibilityLabel(L.Common.remove)
                }
            }
            HStack(spacing: 10) {
                Menu {
                    Button(L.Checkout.nobody) { line.resourceId = nil }
                    ForEach(team) { professional in
                        Button(professional.name) { line.resourceId = professional.id }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Circle().fill(workspace.color(for: line.resourceId)).frame(width: 7, height: 7)
                        Text(workspace.professional(line.resourceId)?.name ?? L.Checkout.nobody).lineLimit(1)
                        Image(systemName: "chevron.up.chevron.down").font(.caption2)
                    }
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 32)
                    .background(Theme.fill, in: Capsule())
                }
                if line.kind == .product {
                    Stepper(value: $line.quantity, in: 1...999) {
                        Text("× \(line.quantity)").font(.footnote.weight(.semibold)).monospacedDigit()
                    }
                    .fixedSize()
                }
                Spacer()
                MoneyField(title: L.Checkout.unitPrice, amount: Binding(get: { line.unitPrice }, set: { line.unitPrice = $0 ?? 0 }), currency: currency)
                    .font(.footnote)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 110)
                    .padding(.horizontal, 10)
                    .frame(minHeight: 32)
                    .background(Theme.fill, in: Capsule())
            }
            if line.discountAmount > 0 {
                Text("−\(workspace.money(line.discountAmount))").font(.caption).foregroundStyle(Theme.success)
            }
        }
        .card(padding: 14)
    }

    private func menuLabel(_ text: String) -> some View {
        HStack(spacing: 4) {
            Text(text).font(.body.weight(.semibold)).foregroundStyle(Theme.ink).lineLimit(1)
            Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(Theme.subtle)
        }
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
            List(results) { found in
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
