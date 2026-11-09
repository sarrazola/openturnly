import SwiftUI

@main
struct OpenTurnlyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .environment(model.toasts)
                .tint(Theme.brand)
                .task { await model.restore() }
        }
    }
}

/// Picks the screen the model says is up and keeps toasts on top of everything.
struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        ZStack {
            Theme.canvas.ignoresSafeArea()
            content
                .transition(.opacity)
            ToastOverlay()
        }
        .animation(.easeInOut(duration: 0.25), value: model.screen)
    }

    @ViewBuilder
    private var content: some View {
        switch model.screen {
        case .loading:
            LaunchView()
        case .signIn:
            SignInView()
        case .reconnect:
            ReconnectView()
        case .noBusinesses:
            NoBusinessesView()
        case .pickBusiness:
            NavigationStack {
                BusinessPickerList(businesses: model.businesses, current: nil, subtitle: model.session?.user.email) { model.open($0) }
                    .navigationTitle(L.Business.choose)
                    .refreshable { await model.loadBusinesses() }
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button(L.More.signOut) { Task { await model.signOut() } }
                        }
                    }
            }
        case .main:
            if let workspace = model.workspace {
                MainTabView()
                    .environment(workspace)
                    .id(workspace.business.id)
            } else {
                LaunchView()
            }
        }
    }
}

/// While the saved session is checked and while a business loads: the brand
/// mark turning on the launch screen's background, centred where the launch
/// screen drew it.
struct LaunchView: View {
    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            ClockLoader()
        }
    }
}

struct ReconnectView: View {
    @Environment(AppModel.self) private var model
    @State private var busy = false

    var body: some View {
        VStack(spacing: 16) {
            EmptyState(symbol: "wifi.exclamationmark", title: L.Business.reconnectTitle, message: L.Business.reconnectBody)
            PrimaryButton(title: L.Common.retry, busy: busy) {
                busy = true
                Task {
                    await model.restore()
                    busy = false
                }
            }
            .padding(.horizontal, 32)
            Button(L.More.signOut) { Task { await model.signOut() } }
                .foregroundStyle(Theme.muted)
                .frame(minHeight: 44)
        }
        .frame(maxWidth: 480)
    }
}

struct NoBusinessesView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        VStack(spacing: 16) {
            EmptyState(symbol: "building.2", title: L.Business.noneTitle, message: L.Business.noneBody,
                       actionTitle: L.Common.retry) { Task { await model.loadBusinesses() } }
            Button(L.More.signOut) { Task { await model.signOut() } }
                .foregroundStyle(Theme.muted)
                .frame(minHeight: 44)
        }
        .frame(maxWidth: 480)
    }
}

/// The businesses a person can open, as a list to pick from.
struct BusinessPickerList: View {
    let businesses: [Business]
    let current: String?
    /// Under the title when picking after sign-in: who is signed in.
    var subtitle: String?
    let pick: (Business) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Section {
                ForEach(businesses) { business in
                    Button {
                        Haptics.selection()
                        pick(business)
                    } label: {
                        HStack(spacing: 14) {
                            BusinessLogo(business: business, server: model.session?.server ?? "", size: 44)
                            VStack(alignment: .leading, spacing: 5) {
                                Text(business.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                                BusinessChips(business: business)
                            }
                            Spacer()
                            if business.id == current {
                                Image(systemName: "checkmark.circle.fill").font(.title3).foregroundStyle(Theme.brand)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
            } header: {
                if let subtitle { Text(subtitle).textCase(nil) }
            }
        }
        .listStyle(.insetGrouped)
    }
}

/// The role in a business, and «Ejemplo» for a sample one.
struct BusinessChips: View {
    let business: Business

    var body: some View {
        HStack(spacing: 6) {
            Chip(text: L.Business.role(business.access), tone: .brand)
            if business.sample == true { Chip(text: L.Business.sample, tone: .neutral) }
        }
    }
}

/// The business's logo, or its initials on the brand colour.
struct BusinessLogo: View {
    let business: Business
    let server: String
    var size: CGFloat = 36

    var body: some View {
        let url = business.logoUrl.flatMap { URL(string: $0.hasPrefix("http") ? $0 : server + $0) }
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(Theme.brand.gradient)
            Text(TextRules.initials(business.name))
                .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                .foregroundStyle(Theme.onBrand)
            if let url {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                            .frame(width: size, height: size)
                            .background(Color.white)
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}
