import SwiftUI

/// The signed-in person: name, e-mail and password.
struct AccountView: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.openURL) private var openURL
    @State private var name = ""
    @State private var email = ""
    @State private var currentForEmail = ""
    @State private var currentPassword = ""
    @State private var newPassword = ""
    @State private var savingDetails = false
    @State private var savingPassword = false
    @State private var detailsError: String?
    @State private var passwordError: String?
    @State private var prepared = false

    private var user: User? { model.session?.user }
    private var emailChanged: Bool { email.trimmingCharacters(in: .whitespaces).lowercased() != (user?.email ?? "").lowercased() }
    private var detailsDirty: Bool { name.trimmingCharacters(in: .whitespaces) != (user?.name ?? "") || emailChanged }

    var body: some View {
        Form {
            Section {
                HStack(spacing: 14) {
                    Avatar(name: user?.name ?? "", colorHex: "#0f766e", size: 56)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(user?.name ?? "").font(.headline)
                        Text(user?.email ?? "").font(.subheadline).foregroundStyle(Theme.muted)
                    }
                }
                .padding(.vertical, 4)
                LabeledContent(L.Account.role, value: user?.isInstallOwner == true ? L.Account.installOwner : L.Business.role(workspace.business.access))
                LabeledContent(L.Account.server, value: ServerAddress.displayHost(workspace.server))
            }

            Section {
                TextField(L.Common.name, text: $name).textContentType(.name)
                TextField(L.Common.email, text: $email)
                    .keyboardType(.emailAddress)
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if emailChanged {
                    SecureField(L.Account.currentPassword, text: $currentForEmail).textContentType(.password)
                }
                Button {
                    Task { await saveDetails() }
                } label: {
                    HStack {
                        Text(L.Common.saveChanges).fontWeight(.semibold)
                        if savingDetails { Spacer(); ProgressView() }
                    }
                }
                .disabled(!detailsDirty || savingDetails || (emailChanged && currentForEmail.isEmpty))
            } header: {
                Text(L.Account.details)
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    if emailChanged { Text(L.Account.emailNeedsPassword) }
                    FieldError(text: detailsError)
                }
            }

            Section {
                SecureField(L.Account.currentPassword, text: $currentPassword).textContentType(.password)
                SecureField(L.Account.newPassword, text: $newPassword).textContentType(.newPassword)
                Button {
                    Task { await changePassword() }
                } label: {
                    HStack {
                        Text(L.Account.changePassword).fontWeight(.semibold)
                        if savingPassword { Spacer(); ProgressView() }
                    }
                }
                .disabled(currentPassword.isEmpty || newPassword.count < 10 || savingPassword)
            } header: {
                Text(L.Account.password)
            } footer: {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(L.Account.newPasswordHint) \(L.Account.passwordHint)")
                    FieldError(text: passwordError)
                }
            }

            if model.push.available {
                notificationsSection
            }

            Section {
                Text(L.Account.appearance).font(.footnote).foregroundStyle(Theme.muted)
                if let url = Brand.privacyPolicyURL {
                    Button(L.SignIn.privacy) { openURL(url) }
                }
                if let url = Brand.supportURL {
                    Button(L.SignIn.support) { openURL(url) }
                }
            }
        }
        .navigationTitle(L.Account.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            guard !prepared, let user else { return }
            prepared = true
            name = user.name
            email = user.email
        }
        .task {
            await model.push.loadSettings()
            await model.push.refreshPermission()
        }
    }

    /// Whether this phone gets notifications, and why not when Settings block them.
    private var notificationsSection: some View {
        let push = model.push
        let blocked = !push.switchedOff && push.permission == .denied
        return Section {
            Toggle(L.Notifications.onThisPhone, isOn: Binding(
                get: { !push.switchedOff },
                set: { push.setOn($0) }
            ))
            if blocked, let url = URL(string: UIApplication.openNotificationSettingsURLString) {
                Button(L.Notifications.openSettings) { openURL(url) }
            }
        } header: {
            Text(L.Notifications.title)
        } footer: {
            Text(blocked ? L.Notifications.deniedHint(Brand.name) : L.Notifications.onThisPhoneHint)
        }
    }

    private func saveDetails() async {
        savingDetails = true
        detailsError = nil
        defer { savingDetails = false }
        do {
            let updated = try await workspace.api.updateMe(
                name: name.trimmingCharacters(in: .whitespaces),
                email: email.trimmingCharacters(in: .whitespaces).lowercased(),
                currentPassword: emailChanged ? currentForEmail : nil
            )
            model.updateUser(updated)
            name = updated.name
            email = updated.email
            currentForEmail = ""
            toasts.show(L.Common.saved)
        } catch {
            detailsError = ErrorText.field(error, "currentPassword") ?? ErrorText.message(error)
            Haptics.error()
        }
    }

    private func changePassword() async {
        savingPassword = true
        passwordError = nil
        defer { savingPassword = false }
        do {
            try await workspace.api.changePassword(current: currentPassword, new: newPassword)
            currentPassword = ""
            newPassword = ""
            toasts.show(L.Account.passwordChanged)
        } catch {
            passwordError = ErrorText.field(error, "currentPassword") ?? ErrorText.field(error, "newPassword") ?? ErrorText.message(error)
            Haptics.error()
        }
    }
}
