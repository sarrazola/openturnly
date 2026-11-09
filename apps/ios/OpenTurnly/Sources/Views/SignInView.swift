import SwiftUI

/// Signing in.
///
/// A build with an account directory (Brand.hostedSignInURL) asks for an e-mail
/// and a password and nothing else; the directory answers with the accounts that
/// person can open, each with its own server. A build with a hosted template asks
/// for the account name first. Every build can "Use another address" to type a
/// server, and a build with no hosted values starts there.
struct SignInView: View {
    @State private var path: [Route] = []

    enum Route: Hashable {
        case server
        case login(String)
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if Brand.hostedSignInURL != nil {
                    LoginForm(target: .directory, path: $path)
                } else if Brand.hosted != nil {
                    AccountEntry(path: $path)
                } else {
                    ServerEntry(path: $path)
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .server: ServerEntry(path: $path)
                case .login(let server): LoginForm(target: .server(server), path: $path)
                }
            }
        }
        .tint(Brand.primaryColor)
    }
}

/// The logo, a title and a line under it.
private struct SignInHeader: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 0) {
            Image("BrandLogoWhite")
                .resizable().scaledToFit()
                .frame(width: 38, height: 38)
                .frame(width: 64, height: 64)
                .background(
                    LinearGradient(colors: [Color(hex: "#14b8a6"), Brand.primaryColor], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                )
                .shadow(color: Brand.primaryColor.opacity(0.35), radius: 14, y: 6)
                .accessibilityLabel(Brand.name)
                .padding(.bottom, 24)
            Text(title)
                .font(.largeTitle.weight(.bold))
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            Text(subtitle)
                .font(.body)
                .foregroundStyle(Theme.muted)
                .padding(.top, 8)
                .fixedSize(horizontal: false, vertical: true)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity)
    }
}

/// A soft wash of the brand colour behind the sign-in screens.
private struct SignInBackground: View {
    var body: some View {
        ZStack(alignment: .top) {
            Theme.canvas
            LinearGradient(colors: [Brand.primaryColor.opacity(0.16), Brand.primaryColor.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(height: 360)
        }
        .ignoresSafeArea()
    }
}

/// The frame of every sign-in screen: the form in the middle, the brand's links
/// at the bottom, and all of it scrolling when the keyboard needs the room.
private struct SignInScaffold<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Spacer(minLength: 16)
                    content.frame(maxWidth: 460)
                    Spacer(minLength: 24)
                    BrandLinks()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
        }
        .background(SignInBackground())
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// A labelled input on a filled, rounded background.
struct InputBox<Content: View>: View {
    let label: String
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(label).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
            content
                .padding(.horizontal, 14)
                .frame(minHeight: 52)
                .background(Theme.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
        }
    }
}

private struct BrandLinks: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        if Brand.privacyPolicyURL != nil || Brand.supportURL != nil {
            HStack(spacing: 24) {
                if let url = Brand.privacyPolicyURL { Button(L.SignIn.privacy) { openURL(url) } }
                if let url = Brand.supportURL { Button(L.SignIn.support) { openURL(url) } }
            }
            .font(.footnote)
            .foregroundStyle(Theme.muted)
            .frame(maxWidth: .infinity, minHeight: 36)
        }
    }
}

/// The address of a server somebody runs themselves.
private struct ServerEntry: View {
    @Binding var path: [SignInView.Route]
    @Environment(AppModel.self) private var model
    @State private var draft = Preferences.lastServer.map(ServerAddress.displayHost) ?? Brand.defaultServer
    @State private var busy = false
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        SignInScaffold {
            VStack(spacing: 24) {
                SignInHeader(title: L.SignIn.serverTitle, subtitle: L.SignIn.serverHint(Brand.name))
                if let notice = model.signInNotice {
                    NoticeBanner(text: notice)
                }
                InputBox(label: L.SignIn.serverLabel) {
                    TextField(L.SignIn.serverPlaceholder, text: $draft)
                        .keyboardType(.URL)
                        .textContentType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.continue)
                        .focused($focused)
                        .onSubmit { Task { await proceed() } }
                }
                FieldError(text: error)
                PrimaryButton(title: L.SignIn.continue, busy: busy, disabled: draft.trimmingCharacters(in: .whitespaces).isEmpty) {
                    Task { await proceed() }
                }
                if Brand.hosted != nil {
                    Button(L.SignIn.useHosted(Brand.hosted?.label ?? Brand.name)) { path.removeAll() }
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
        }
        .onAppear { if draft.isEmpty { focused = true } }
    }

    private func proceed() async {
        error = nil
        guard let server = ServerAddress.normalize(draft) else {
            error = L.SignIn.invalidServer
            Haptics.error()
            return
        }
        busy = true
        defer { busy = false }
        do {
            try await OpenTurnlyAPI.health(server)
        } catch let failure as APIError {
            error = failure.status == 0 ? L.SignIn.unreachable : L.SignIn.notOpenTurnly
            Haptics.error()
            return
        } catch {
            return
        }
        if path.last == .server { path.removeLast() }
        path.append(.login(server))
    }
}

/// An account name on the publisher's hosted service.
private struct AccountEntry: View {
    @Binding var path: [SignInView.Route]
    @State private var draft = ""
    @State private var error: String?

    var body: some View {
        SignInScaffold {
            VStack(spacing: 24) {
                SignInHeader(title: L.SignIn.title, subtitle: L.SignIn.accountHint)
                InputBox(label: L.SignIn.accountLabel) {
                    TextField(L.SignIn.accountPlaceholder, text: $draft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.continue)
                        .onSubmit(proceed)
                }
                FieldError(text: error)
                PrimaryButton(title: L.SignIn.continue, disabled: draft.trimmingCharacters(in: .whitespaces).isEmpty, action: proceed)
                Button(L.SignIn.otherAddress) { path.append(.server) }
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
    }

    private func proceed() {
        guard let server = Brand.hostedServer(for: draft) else {
            error = L.SignIn.invalidAccount
            return
        }
        error = nil
        path.append(.login(server))
    }
}

/// E-mail and password against one target: the hosted directory, or a server.
private struct LoginForm: View {
    enum Target: Equatable {
        case directory
        case server(String)
    }

    let target: Target
    @Binding var path: [SignInView.Route]
    @Environment(AppModel.self) private var model
    @State private var email = ""
    @State private var password = ""
    @State private var reveal = false
    @State private var busy = false
    @State private var error: String?
    @State private var forgot = false
    @State private var accounts: [HostedAccount] = []
    @FocusState private var focus: Field?

    private enum Field { case email, password }

    private var canSubmit: Bool { !email.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty && !busy }

    private var subtitle: String {
        switch target {
        case .directory: return L.SignIn.subtitle
        case .server(let server): return L.SignIn.connectedTo(ServerAddress.displayHost(server))
        }
    }

    var body: some View {
        SignInScaffold {
            VStack(spacing: 20) {
                SignInHeader(title: L.SignIn.title, subtitle: subtitle)
                    .padding(.bottom, 4)
                if let notice = model.signInNotice {
                    NoticeBanner(text: notice)
                }
                InputBox(label: L.SignIn.emailLabel) {
                    TextField(L.SignIn.emailPlaceholder, text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .focused($focus, equals: .email)
                        .onSubmit { focus = .password }
                }
                InputBox(label: L.SignIn.passwordLabel) {
                    HStack {
                        Group {
                            if reveal {
                                TextField("", text: $password)
                            } else {
                                SecureField("", text: $password)
                            }
                        }
                        .textContentType(.password)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.go)
                        .focused($focus, equals: .password)
                        .onSubmit { Task { await submit() } }
                        Button {
                            reveal.toggle()
                        } label: {
                            Image(systemName: reveal ? "eye.slash" : "eye").foregroundStyle(Theme.subtle)
                        }
                        .accessibilityLabel(reveal ? L.SignIn.hidePassword : L.SignIn.showPassword)
                    }
                }
                FieldError(text: error)
                PrimaryButton(title: L.SignIn.submit, busy: busy, disabled: !canSubmit) { Task { await submit() } }
                Button(L.SignIn.forgot) { forgot = true }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Brand.primaryColor)
                    .frame(minHeight: 32)
                VStack(spacing: 4) {
                    switch target {
                    case .directory:
                        Button(L.SignIn.otherAddress) { path.append(.server) }
                    case .server:
                        Button(Brand.hosted != nil ? L.SignIn.useHosted(Brand.hosted?.label ?? Brand.name) : L.SignIn.changeServer) {
                            path.removeAll()
                        }
                    }
                }
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, minHeight: 44)
            }
        }
        .alert(L.SignIn.forgotTitle, isPresented: $forgot) {
            Button(L.Common.ok, role: .cancel) {}
        } message: { Text(L.SignIn.forgotBody) }
        .sheet(isPresented: Binding(get: { accounts.count > 1 }, set: { if !$0 { accounts = [] } })) { chooser }
        .onAppear { if email.isEmpty { focus = .email } }
    }

    /// The same e-mail opens more than one account: pick one.
    private var chooser: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(accounts) { account in
                        Button {
                            Task { await finish(account) }
                        } label: {
                            HStack(spacing: 12) {
                                IconTile(symbol: "building.2.fill")
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(account.name ?? ServerAddress.displayHost(account.server)).foregroundStyle(Theme.ink)
                                    Text(ServerAddress.displayHost(account.server)).font(.footnote).foregroundStyle(Theme.muted)
                                }
                            }
                        }
                    }
                } footer: {
                    Text(L.SignIn.chooseAccountBody)
                }
            }
            .navigationTitle(L.SignIn.chooseAccount)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { accounts = [] } } }
        }
        .presentationDetents([.medium, .large])
    }

    private func finish(_ account: HostedAccount) async {
        guard let server = ServerAddress.normalize(account.server) else { return }
        accounts = []
        await model.signedIn(server: server, token: account.token, user: account.user)
    }

    private func submit() async {
        guard canSubmit else { return }
        busy = true
        error = nil
        model.signInNotice = nil
        defer { busy = false }
        let address = email.trimmingCharacters(in: .whitespaces)
        do {
            switch target {
            case .directory:
                guard let directory = Brand.hostedSignInURL else { return }
                let found = try await OpenTurnlyAPI.hostedSignIn(directory, email: address, password: password)
                guard let first = found.first else {
                    error = L.SignIn.invalidCredentials
                    return
                }
                if found.count == 1 { await finish(first) } else { accounts = found }
            case .server(let server):
                let response = try await OpenTurnlyAPI.signIn(server, email: address, password: password)
                await model.signedIn(server: server, token: response.token, user: response.user)
            }
        } catch let failure as APIError {
            error = failure.status == 401 ? L.SignIn.invalidCredentials : ErrorText.message(failure)
            Haptics.error()
        } catch {
            self.error = L.Errors.generic
        }
    }
}

/// A one-line notice on a tinted background.
struct NoticeBanner: View {
    let text: String
    var symbol = "info.circle.fill"
    var tone: Tone = .warning

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: symbol)
            Text(text).font(.subheadline).fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(tone.foreground)
        .padding(14)
        .background(tone.background, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
