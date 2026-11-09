import SwiftUI

/// Everything that isn't the agenda, customers or sales.
struct MoreView: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @State private var confirmingSignOut = false
    @State private var switching = false

    var body: some View {
        List {
            Section {
                Button {
                    switching = true
                } label: {
                    HStack(spacing: 14) {
                        BusinessLogo(business: workspace.business, server: workspace.server, size: 52)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(workspace.business.name).font(.headline).foregroundStyle(Theme.ink)
                            Text("\(L.Business.role(workspace.business.access)) · \(model.session?.user.name ?? "")")
                                .font(.subheadline).foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        if model.businesses.count > 1 {
                            Image(systemName: "arrow.left.arrow.right.circle.fill")
                                .font(.title2)
                                .foregroundStyle(Theme.brand)
                                .accessibilityLabel(L.Business.switchBusiness)
                        }
                    }
                    .padding(.vertical, 6)
                }
                .disabled(model.businesses.count < 2)
            }

            if workspace.isAdmin {
                Section(L.More.business) {
                    row(.businessInfo, L.More.businessInfo, "storefront.fill", "#0f766e")
                    row(.hours, L.More.hours, "clock.fill", "#0284c7")
                    row(.services, L.More.services, "scissors", "#7c3aed")
                    row(.professionals, L.More.professionals, "person.2.fill", "#db2777")
                    row(.team, L.More.team, "person.badge.key.fill", "#475569")
                }
                Section(L.More.money) {
                    row(.reports, L.More.reports, "chart.bar.xaxis", "#d97706")
                    row(.cash, L.More.cash, "banknote.fill", "#16a34a")
                    row(.inventory, L.More.inventory, "shippingbox.fill", "#0891b2")
                    row(.giftCards, L.More.giftCards, "giftcard.fill", "#e11d48")
                }
            } else {
                Section(L.More.business) {
                    row(.hours, L.More.hours, "clock.fill", "#0284c7")
                    row(.services, L.More.services, "scissors", "#7c3aed")
                }
            }

            Section {
                row(.account, L.More.account, "person.crop.circle.fill", "#52525b")
                Button(role: .destructive) {
                    confirmingSignOut = true
                } label: {
                    HStack(spacing: 14) {
                        IconTile(symbol: "rectangle.portrait.and.arrow.right", color: Theme.danger)
                        Text(L.More.signOut).foregroundStyle(Theme.danger)
                        Spacer()
                        if model.signingOut { ProgressView() }
                    }
                }
                .confirmationDialog(L.More.signOutTitle, isPresented: $confirmingSignOut, titleVisibility: .visible) {
                    Button(L.More.signOut, role: .destructive) { Task { await model.signOut() } }
                    Button(L.Common.cancel, role: .cancel) {}
                }
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    if workspace.isAdmin { Text(L.More.webOnly) }
                    Text("\(Brand.name) · \(L.More.version(Brand.version))")
                }
                .padding(.top, 8)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.More.title)
        .sheet(isPresented: $switching) {
            NavigationStack {
                BusinessPickerList(businesses: model.businesses, current: workspace.business.id) { business in
                    switching = false
                    if business.id != workspace.business.id { model.open(business) }
                }
                .navigationTitle(L.Business.switchBusiness)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.close) { switching = false } } }
            }
            .presentationDetents([.medium, .large])
        }
    }

    private func row(_ route: Route, _ title: String, _ symbol: String, _ color: String) -> some View {
        NavigationLink(value: route) {
            HStack(spacing: 14) {
                IconTile(symbol: symbol, color: Color(hex: color))
                Text(title).foregroundStyle(Theme.ink)
            }
        }
    }
}
