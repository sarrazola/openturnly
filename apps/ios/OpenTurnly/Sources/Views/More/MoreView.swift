import SwiftUI

/// The business, the booking link, the numbers, the account; everything else is
/// in the web panel.
struct MoreView: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @Environment(\.openURL) private var openURL
    @State private var confirmingSignOut = false
    @State private var switching = false

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    BusinessLogo(business: workspace.business, server: workspace.server, size: 52)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(workspace.business.name).font(.headline).foregroundStyle(Theme.ink)
                        BusinessChips(business: workspace.business)
                    }
                }
                .padding(.vertical, 6)
                .accessibilityElement(children: .combine)
                if model.businesses.count > 1 {
                    Button {
                        switching = true
                    } label: {
                        HStack(spacing: 14) {
                            IconTile(symbol: "arrow.left.arrow.right", color: Theme.brand)
                            Text(L.Business.switchBusiness).foregroundStyle(Theme.ink)
                            Spacer()
                            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.subtle)
                        }
                    }
                }
            }

            Section {
                row(.bookingLink, L.More.bookingLink, "qrcode", "#0f766e")
                if workspace.isAdmin {
                    row(.reports, L.More.reports, "chart.bar.xaxis", "#d97706")
                }
            }

            if workspace.isAdmin, let panel = workspace.webPanelURL {
                Section {
                    Button {
                        openURL(panel)
                    } label: {
                        HStack(spacing: 14) {
                            IconTile(symbol: "safari.fill", color: Color(hex: "#475569"))
                            Text(L.More.webPanel).foregroundStyle(Theme.ink)
                            Spacer()
                            Image(systemName: "arrow.up.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.subtle)
                        }
                    }
                } footer: {
                    Text(L.More.webPanelHint)
                }
            }

            Section {
                NavigationLink(value: Route.account) {
                    HStack(spacing: 14) {
                        IconTile(symbol: "person.crop.circle.fill", color: Color(hex: "#52525b"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L.More.account).foregroundStyle(Theme.ink)
                            Text([model.session?.user.name, model.session?.user.email].compactMap { $0 }.joined(separator: " · "))
                                .font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                        }
                    }
                }
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
            } header: {
                Text(L.More.you)
            } footer: {
                VStack(spacing: 6) {
                    Text("\(Brand.name) · \(L.More.version(Brand.marketingVersion))")
                    BrandLinks()
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.More.title)
        .alert(L.More.signOutTitle, isPresented: $confirmingSignOut) {
            Button(L.More.signOut, role: .destructive) { Task { await model.signOut() } }
            Button(L.Common.cancel, role: .cancel) {}
        } message: {
            Text(L.More.signOutBody)
        }
        .sheet(isPresented: $switching) {
            NavigationStack {
                BusinessPickerList(businesses: model.businesses, current: workspace.business.id) { business in
                    switching = false
                    if business.id != workspace.business.id { model.open(business) }
                }
                .navigationTitle(L.Business.switchBusiness)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.close) { switching = false } } }
                .refreshable { await model.loadBusinesses() }
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
