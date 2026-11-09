import SwiftUI

// MARK: - Cash register

/// Money in and out for a period, cash in the drawer, and income or expense entries.
struct CashView: View {
    @Environment(Workspace.self) private var workspace
    @State private var period: ReportsView.Period = .today
    @State private var summary: CashSummary?
    @State private var error: Error?
    @State private var adding = false

    var body: some View {
        List {
            Section {
                Picker(L.Common.period, selection: $period) {
                    ForEach([ReportsView.Period.today, .week, .month, .lastMonth]) { Text($0.title).tag($0) }
                }
                .pickerStyle(.menu)
            }
            if let summary {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Label(L.Cash.inDrawer, systemImage: "tray.full.fill").font(.subheadline.weight(.medium)).foregroundStyle(Theme.muted)
                        Text(workspace.money(summary.cashInDrawer))
                            .font(.system(.largeTitle, design: .rounded).weight(.bold)).monospacedDigit()
                    }
                    .padding(.vertical, 6)
                }
                Section(L.Cash.flows) {
                    flow(L.Cash.sales, summary.flows.sales)
                    flow(L.Cash.deposits, summary.flows.deposits)
                    flow(L.Cash.internal, summary.flows.internal)
                    flow(L.Cash.giftCards, summary.flows.giftCards)
                    flow(L.Cash.otherIncome, summary.flows.otherIncome)
                    flow(L.Cash.expenses, -abs(summary.flows.expenses))
                    HStack {
                        Text(L.Cash.result).fontWeight(.semibold)
                        Spacer()
                        Text(workspace.money(summary.flows.result)).fontWeight(.bold).monospacedDigit()
                            .foregroundStyle(summary.flows.result < 0 ? Theme.danger : Theme.ink)
                    }
                }
                Section(L.Sales.byMethod) {
                    method("cash", summary.byMethod.cash)
                    method("card", summary.byMethod.card)
                    method("transfer", summary.byMethod.transfer)
                    method("other", summary.byMethod.other)
                    method("online", summary.byMethod.online)
                }
                Section(L.Cash.movements) {
                    if summary.movements.isEmpty {
                        Text(L.Cash.noMovements).foregroundStyle(Theme.muted)
                    } else {
                        ForEach(summary.movements) { movement in
                            HStack(spacing: 12) {
                                IconTile(symbol: movement.kind == "expense" || movement.kind == "transfer_out" ? "arrow.up.right" : "arrow.down.left",
                                         color: movement.kind == "expense" || movement.kind == "transfer_out" ? Theme.danger : Theme.success, size: 32)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(movement.description ?? movement.category ?? L.Cash.kind(movement.kind)).font(.subheadline.weight(.medium))
                                    Text([L.Cash.kind(movement.kind), movement.method.map(L.Payments.method), Fmt.dateTime(movement.createdAt, clock: workspace.clock)]
                                        .compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(Theme.muted)
                                }
                                Spacer()
                                let out = movement.kind == "expense" || movement.kind == "transfer_out"
                                Text((out ? "−" : "") + workspace.money(abs(movement.amount)))
                                    .font(.subheadline.weight(.semibold)).monospacedDigit()
                                    .foregroundStyle(out ? Theme.danger : Theme.ink)
                            }
                        }
                    }
                }
            } else if let error {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else {
                RowsSkeleton(count: 6).listRowInsets(EdgeInsets())
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.Cash.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { adding = true } label: { Label(L.Cash.newMovement, systemImage: "plus") }
            }
        }
        .refreshable { await load() }
        .task(id: period) { await load() }
        .sheet(isPresented: $adding) { CashMovementForm { await load() } }
    }

    private func flow(_ label: String, _ amount: Int) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(workspace.money(amount)).monospacedDigit().foregroundStyle(amount == 0 ? Theme.subtle : amount < 0 ? Theme.danger : Theme.ink)
        }
    }

    private func method(_ raw: String, _ amount: Int) -> some View {
        HStack(spacing: 10) {
            Image(systemName: L.Payments.symbol(raw)).foregroundStyle(Theme.brand).frame(width: 22)
            Text(L.Payments.method(raw))
            Spacer()
            Text(workspace.money(amount)).monospacedDigit().foregroundStyle(amount == 0 ? Theme.subtle : Theme.ink)
        }
    }

    private func load() async {
        let (from, to) = period.range(today: workspace.clock.today())
        let range = workspace.clock.range(from, through: to)
        do {
            summary = try await workspace.api.cash(workspace.id, from: range.from, to: range.to)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
}

/// An income or an expense outside sales.
private struct CashMovementForm: View {
    let onSaved: () async -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var kind = "expense"
    @State private var amount: Int?
    @State private var method = "cash"
    @State private var category = ""
    @State private var description = ""
    @State private var locationId: String?
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Picker("", selection: $kind) {
                    Text(L.Cash.expense).tag("expense")
                    Text(L.Cash.income).tag("income")
                }
                .pickerStyle(.segmented)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
                Section(L.Checkout.amount) {
                    MoneyField(title: "0", amount: $amount, currency: workspace.currency).font(.title2.weight(.semibold))
                }
                Section {
                    Picker(L.Checkout.method, selection: $method) {
                        ForEach(CheckoutRules.counterMethods, id: \.self) { Text(L.Payments.method($0)).tag($0) }
                    }
                    if workspace.activeLocations.count > 1 {
                        Picker(L.Agenda.location, selection: $locationId) {
                            ForEach(workspace.activeLocations) { Text($0.name).tag(Optional($0.id)) }
                        }
                    }
                    TextField(L.Cash.category, text: $category)
                    TextField(L.Common.description, text: $description, axis: .vertical)
                }
                if let error { Section { FieldError(text: error) } }
            }
            .navigationTitle(L.Cash.newMovement)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ProgressView() } else { Button(L.Common.save) { Task { await save() } }.fontWeight(.semibold) }
                }
            }
            .onAppear { if locationId == nil { locationId = workspace.activeLocations.first?.id } }
        }
        .presentationDetents([.large])
    }

    private func save() async {
        guard let amount, amount > 0 else {
            error = L.Checkout.amountRequired
            return
        }
        guard let locationId else { return }
        busy = true
        defer { busy = false }
        var body: [String: JSON] = ["kind": .string(kind), "locationId": .string(locationId), "amount": .int(amount), "method": .string(method)]
        if let text = TextRules.clean(category) { body["category"] = .string(text) }
        if let text = TextRules.clean(description) { body["description"] = .string(text) }
        do {
            try await workspace.api.addCashMovement(workspace.id, body)
            toasts.show(L.Cash.saved)
            await onSaved()
            dismiss()
        } catch {
            self.error = ErrorText.message(error)
        }
    }
}

// MARK: - Inventory

/// Products with their stock, low ones first.
struct InventoryView: View {
    @Environment(Workspace.self) private var workspace
    @State private var products: [Product] = []
    @State private var loaded = false
    @State private var error: Error?
    @State private var query = ""

    var body: some View {
        let list = products
            .filter { query.isEmpty || TextRules.matches(query, [$0.name, $0.sku]) }
            .sorted { ($0.isLow ? 0 : 1, $0.name) < ($1.isLow ? 0 : 1, $1.name) }
        List {
            if !loaded {
                RowsSkeleton(count: 6).listRowInsets(EdgeInsets())
            } else if let error, products.isEmpty {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else if products.isEmpty {
                EmptyState(symbol: "shippingbox", title: L.Inventory.emptyTitle, message: L.Inventory.emptyBody)
            } else {
                ForEach(list) { product in
                    NavigationLink(value: Route.product(product)) {
                        HStack(spacing: 12) {
                            IconTile(symbol: "shippingbox.fill", color: product.isLow ? Theme.warning : Color(hex: "#0891b2"), size: 38)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(product.name).font(.body.weight(.medium)).foregroundStyle(product.active ? Theme.ink : Theme.muted)
                                Text([product.sku, workspace.money(product.priceAmount)].compactMap { $0 }.joined(separator: " · "))
                                    .font(.footnote).foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 3) {
                                Text("\(product.totalStock)").font(.headline).monospacedDigit()
                                    .foregroundStyle(product.isLow ? Theme.warning : Theme.ink)
                                if product.isLow { Chip(text: L.Inventory.lowStock, tone: .warning) }
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .searchable(text: $query, prompt: L.Common.search)
        .navigationTitle(L.Inventory.title)
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        do {
            products = try await workspace.api.products(workspace.id)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
        loaded = true
    }
}

struct ProductDetailView: View {
    let product: Product
    @Environment(Workspace.self) private var workspace

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text(product.name).font(.title3.weight(.bold))
                    if let sku = product.sku { Text("SKU \(sku)").font(.footnote).foregroundStyle(Theme.muted) }
                    HStack(spacing: 6) {
                        if !product.active { Chip(text: L.Common.inactive, tone: .neutral) }
                        if !product.forSale { Chip(text: L.Inventory.notForSale, tone: .neutral) }
                        if product.isLow { Chip(text: L.Inventory.lowStock, tone: .warning) }
                    }
                }
                .padding(.vertical, 4)
            }
            Section {
                LabeledContent(L.Inventory.price, value: workspace.money(product.priceAmount))
                if let internalPrice = product.internalPriceAmount {
                    LabeledContent(L.Inventory.internalPrice, value: workspace.money(internalPrice))
                }
                if let cost = product.costAmount { LabeledContent(L.Inventory.cost, value: workspace.money(cost)) }
                if let tax = product.taxRate, tax > 0 { LabeledContent(L.Inventory.taxIncluded, value: "\(Int(tax)) %") }
            }
            Section(L.Inventory.stock) {
                ForEach(workspace.locations) { location in
                    HStack {
                        Text(location.name)
                        Spacer()
                        if let alert = product.alerts?[location.id] {
                            Text("\(L.Inventory.alertAt) \(alert)").font(.caption).foregroundStyle(Theme.subtle)
                        }
                        Text("\(product.stock?[location.id] ?? 0)").fontWeight(.semibold).monospacedDigit()
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(product.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Gift cards

/// Discount codes and gift cards with their state; switch one on or off.
struct GiftCardsView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var cards: [GiftCard] = []
    @State private var loaded = false
    @State private var error: Error?

    var body: some View {
        List {
            if !loaded {
                RowsSkeleton(count: 5).listRowInsets(EdgeInsets())
            } else if let error, cards.isEmpty {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else if cards.isEmpty {
                EmptyState(symbol: "giftcard", title: L.GiftCards.emptyTitle, message: L.GiftCards.emptyBody)
            } else {
                ForEach(cards) { card in
                    HStack(spacing: 12) {
                        IconTile(symbol: card.kind == "discount" ? "percent" : "giftcard.fill",
                                 color: card.kind == "discount" ? Color(hex: "#7c3aed") : Color(hex: "#e11d48"), size: 38)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(card.code).font(.body.weight(.semibold)).monospaced()
                            Text(detail(card)).font(.footnote).foregroundStyle(Theme.muted).lineLimit(2)
                        }
                        Spacer()
                        Chip(text: L.GiftCards.state(card.state), tone: tone(card.state))
                    }
                    .swipeActions {
                        Button {
                            Task { await toggle(card) }
                        } label: {
                            Label(card.active ? L.GiftCards.deactivate : L.GiftCards.activate, systemImage: card.active ? "pause.circle" : "play.circle")
                        }
                        .tint(card.active ? .gray : Theme.brand)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.GiftCards.title)
        .refreshable { await load() }
        .task { await load() }
    }

    private func detail(_ card: GiftCard) -> String {
        var parts: [String] = []
        if card.kind == "discount" {
            if let percent = card.percent { parts.append("−\(percent) %") } else if let amount = card.amount { parts.append("−\(workspace.money(amount))") }
            parts.append(L.GiftCards.uses(card.uses ?? 0, card.maxUses))
        } else {
            if let amount = card.amount { parts.append(workspace.money(amount)) }
            if let remaining = card.remaining { parts.append(L.GiftCards.left(workspace.money(remaining))) }
        }
        if let until = card.validUntil.flatMap(LocalDate.init) { parts.append(L.GiftCards.until(Fmt.dayShort(until))) }
        if let customer = card.customer { parts.append("\(customer.firstName) \(customer.lastName)") }
        return parts.joined(separator: " · ")
    }

    private func tone(_ state: String) -> Tone {
        switch state {
        case "active": return .success
        case "scheduled": return .info
        case "expired", "used_up", "spent": return .warning
        default: return .neutral
        }
    }

    private func toggle(_ card: GiftCard) async {
        do {
            let updated = try await workspace.api.setGiftCardActive(workspace.id, card.id, active: !card.active)
            if let index = cards.firstIndex(where: { $0.id == card.id }) { cards[index] = updated }
            Haptics.success()
        } catch {
            toasts.error(error)
        }
    }

    private func load() async {
        do {
            cards = try await workspace.api.giftCards(workspace.id)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
        loaded = true
    }
}

// MARK: - Team

/// People with access to the business, pending invitations, and inviting by e-mail.
struct TeamView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var access: AccessResponse?
    @State private var error: Error?
    @State private var inviting = false
    @State private var link: URL?

    var body: some View {
        List {
            Section {
                Text(L.Team.hint).font(.subheadline).foregroundStyle(Theme.muted)
            }
            if let link {
                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L.Team.linkOnce).font(.subheadline.weight(.semibold))
                        Text(link.absoluteString).font(.footnote).monospaced().foregroundStyle(Theme.muted).textSelection(.enabled)
                        ShareLink(item: link) {
                            Label(L.Team.shareLink, systemImage: "square.and.arrow.up").font(.subheadline.weight(.semibold))
                        }
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text(L.Team.inviteLink)
                }
            }
            if let access {
                Section(L.Team.members) {
                    if access.members.isEmpty {
                        Text(L.Team.empty).foregroundStyle(Theme.muted)
                    }
                    ForEach(access.members) { member in
                        HStack(spacing: 12) {
                            Avatar(name: member.name, colorHex: member.resourceId.map { workspace.colorHex(for: $0) }, size: 40)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(member.name).font(.body.weight(.medium))
                                Text(member.email).font(.footnote).foregroundStyle(Theme.muted)
                                if let profile = member.resourceName {
                                    Text(L.Team.linked(profile)).font(.caption).foregroundStyle(Theme.subtle)
                                }
                            }
                            Spacer()
                            Chip(text: member.role == "professional" ? L.Business.professional : L.Business.admin,
                                 tone: member.role == "professional" ? .neutral : .brand)
                        }
                    }
                }
                if !access.invitations.isEmpty {
                    Section(L.Team.invitations) {
                        ForEach(access.invitations) { invitation in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(invitation.email).font(.body.weight(.medium))
                                    if let expires = invitation.expiresAt {
                                        Text(L.Team.expires(Fmt.dayShort(workspace.clock.localDate(of: expires))))
                                            .font(.footnote).foregroundStyle(Theme.muted)
                                    }
                                }
                                Spacer()
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    Task { await revoke(invitation) }
                                } label: {
                                    Label(L.Team.revoke, systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            } else if let error {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else {
                RowsSkeleton(count: 3).listRowInsets(EdgeInsets())
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.Team.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { inviting = true } label: { Label(L.Team.invite, systemImage: "person.badge.plus") }
            }
        }
        .refreshable { await load() }
        .task { await load() }
        .sheet(isPresented: $inviting) {
            InviteForm { url in
                link = url
                await load()
            }
        }
    }

    private func load() async {
        do {
            access = try await workspace.api.access(workspace.id)
            error = nil
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }

    private func revoke(_ invitation: Invitation) async {
        do {
            try await workspace.api.deleteInvitation(workspace.id, invitation.id)
            await load()
        } catch {
            toasts.error(error)
        }
    }
}

private struct InviteForm: View {
    let onDone: (URL?) async -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L.Common.email, text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .focused($focused)
                        .onAppear { focused = true }
                        .onSubmit { Task { await send() } }
                } footer: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L.Team.inviteHint)
                        FieldError(text: error)
                    }
                }
            }
            .navigationTitle(L.Team.invite)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ProgressView() } else {
                        Button(L.Team.send) { Task { await send() } }
                            .fontWeight(.semibold)
                            .disabled(email.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func send() async {
        let address = email.trimmingCharacters(in: .whitespaces).lowercased()
        guard TextRules.isEmail(address) else {
            error = L.Team.invalidEmail
            return
        }
        busy = true
        defer { busy = false }
        do {
            let response = try await workspace.api.invite(workspace.id, email: address)
            toasts.show(response.status == "added" ? L.Team.added : L.Team.invited)
            await onDone(response.inviteUrl.flatMap(URL.init(string:)))
            dismiss()
        } catch {
            self.error = ErrorText.message(error)
        }
    }
}
