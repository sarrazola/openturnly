import SwiftUI

/// One sale: lines, discount, tips, how it was paid; voiding it.
struct SaleDetailView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @State private var sale: Sale?
    @State private var error: Error?
    @State private var confirmingVoid = false
    @State private var busy = false
    private let saleId: String

    init(initial: Sale) {
        saleId = initial.id
        _sale = State(initialValue: initial)
    }

    init(saleId: String) {
        self.saleId = saleId
    }

    var body: some View {
        Group {
            if let sale {
                content(sale)
            } else if let error {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }
            } else {
                CardsSkeleton(count: 2).padding(16)
                Spacer()
            }
        }
        .background(Theme.canvas)
        .navigationTitle(sale.map { L.Sales.saleTitle($0.number) } ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .task { if sale == nil { await load() } }
    }

    private func content(_ sale: Sale) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Chip(text: sale.isVoid ? L.Sales.void : L.Sales.paid, tone: sale.isVoid ? .danger : .info,
                             symbol: sale.isVoid ? nil : "checkmark")
                        if sale.isInternal { Chip(text: L.Sales.internal, tone: .neutral) }
                    }
                    Text(workspace.money(sale.collected))
                        .font(.system(.largeTitle, design: .rounded).weight(.bold))
                        .monospacedDigit()
                        .foregroundStyle(sale.isVoid ? Theme.subtle : Theme.ink)
                        .strikethrough(sale.isVoid)
                    Text(Fmt.dateTime(sale.createdAt, clock: workspace.clock)).font(.footnote).foregroundStyle(Theme.muted)
                }
                .frame(maxWidth: .infinity)
                .card()

                if sale.customer != nil || sale.soldByName != nil || (sale.locationName != nil && workspace.activeLocations.count > 1) {
                    VStack(spacing: 0) {
                        if let customer = sale.customer {
                            NavigationLink(value: Route.customer(id: customer.id, name: customer.fullName)) {
                                HStack(spacing: 12) {
                                    Text(L.Sales.customer).font(.subheadline).foregroundStyle(Theme.muted)
                                    Spacer(minLength: 12)
                                    Text(customer.fullName).font(.subheadline.weight(.medium)).foregroundStyle(Theme.brand)
                                    Image(systemName: "chevron.right").font(.caption.weight(.bold)).foregroundStyle(Theme.subtle)
                                }
                                .padding(.vertical, 10)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                        if let location = sale.locationName, workspace.activeLocations.count > 1 {
                            if sale.customer != nil { Divider() }
                            InfoRow(label: L.Appointment.location, value: location)
                        }
                        if let who = sale.soldByName {
                            Divider()
                            InfoRow(label: L.Sales.soldBy, value: who)
                        }
                    }
                    .card(padding: 14)
                }

                VStack(alignment: .leading, spacing: 8) {
                    SectionTitle(text: L.Sales.items)
                    VStack(spacing: 0) {
                        ForEach(sale.items) { item in
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.name).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                                    Text(["\(item.quantity) × \(workspace.money(item.unitPrice))", item.resourceName].compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(Theme.muted)
                                    if item.discountAmount > 0 {
                                        Text("\(L.Checkout.discount) −\(workspace.money(item.discountAmount))").font(.caption).foregroundStyle(Theme.success)
                                    }
                                }
                                Spacer()
                                Text(workspace.money(item.total)).font(.subheadline.weight(.semibold)).monospacedDigit()
                            }
                            .padding(.vertical, 9)
                            Divider()
                        }
                        InfoRow(label: L.Checkout.subtotal, value: workspace.money(sale.subtotal))
                        if sale.discountAmount > 0 {
                            InfoRow(label: L.Checkout.discount, value: "−\(workspace.money(sale.discountAmount))", valueColor: Theme.success)
                        }
                        InfoRow(label: L.Common.total, value: workspace.money(sale.total))
                        ForEach(sale.tips ?? []) { tip in
                            InfoRow(label: L.Sales.tipFor(tip.resourceName ?? "—"), value: workspace.money(tip.amount))
                        }
                    }
                    .card(padding: 14)
                }

                VStack(alignment: .leading, spacing: 8) {
                    SectionTitle(text: L.Checkout.payments)
                    VStack(spacing: 0) {
                        ForEach(sale.payments) { payment in
                            HStack(spacing: 10) {
                                Image(systemName: L.Payments.symbol(payment.method)).foregroundStyle(Theme.brand).frame(width: 22)
                                Text(L.Payments.method(payment.method)).font(.subheadline)
                                Spacer()
                                Text(workspace.money(payment.amount)).font(.subheadline.weight(.semibold)).monospacedDigit()
                            }
                            .padding(.vertical, 8)
                        }
                    }
                    .card(padding: 14)
                }

                if let note = sale.note, !note.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        SectionTitle(text: L.Checkout.note)
                        Text(note).font(.subheadline).foregroundStyle(Theme.ink).card(padding: 14)
                    }
                }

                if !sale.isVoid {
                    SecondaryButton(title: L.Sales.voidAction, symbol: "arrow.uturn.backward.circle", tint: Theme.danger, busy: busy) {
                        confirmingVoid = true
                    }
                    .alert(L.Sales.voidTitle(sale.number), isPresented: $confirmingVoid) {
                        Button(L.Sales.voidAction, role: .destructive) { Task { await void() } }
                        Button(L.Common.goBack, role: .cancel) {}
                    } message: {
                        Text(L.Sales.voidBody)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            sale = try await workspace.api.sale(workspace.id, saleId)
            error = nil
        } catch {
            self.error = error
        }
    }

    private func void() async {
        busy = true
        defer { busy = false }
        do {
            sale = try await workspace.api.voidSale(workspace.id, saleId)
            toasts.show(L.Sales.voided)
            workspace.changed()
        } catch {
            toasts.error(error)
        }
    }
}
