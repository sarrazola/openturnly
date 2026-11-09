import Foundation
import SwiftUI

/// The identity this build was compiled with.
///
/// `Brand/Brand.xcconfig` is read by Xcode at build time and its values land in
/// Info.plist under the `Brand` key. Nothing here is fetched or configurable at
/// runtime: an app's identity is decided when it is built.
enum Brand {
    private static let values: [String: String] = {
        (Bundle.main.object(forInfoDictionaryKey: "Brand") as? [String: String]) ?? [:]
    }()

    private static func value(_ key: String) -> String {
        (values[key] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Addresses in the brand file carry no scheme (see Brand.xcconfig).
    static func httpsURL(_ raw: String) -> URL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let candidate = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard let url = URL(string: candidate), url.scheme == "https", url.host() != nil,
              url.user == nil, url.password == nil else { return nil }
        return url
    }

    static let name: String = {
        let display = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String
        let bundle = Bundle.main.object(forInfoDictionaryKey: "CFBundleName") as? String
        return [display, bundle].compactMap { $0 }.first { !$0.isEmpty } ?? "OpenTurnly"
    }()

    static let version: String = {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(short) (\(build))"
    }()

    /// The sign-in screen's colour.
    static let primaryColor: Color = {
        let raw = value("PrimaryColor").replacingOccurrences(of: "#", with: "")
        return raw.count == 6 ? Color(hex: raw) : Theme.brand
    }()

    /// Pre-fills the server field.
    static let defaultServer: String = {
        let raw = value("DefaultServer")
        guard !raw.isEmpty else { return "" }
        return ServerAddress.normalize(raw) ?? ""
    }()

    /// A preset for a service whoever published this build runs.
    ///
    /// There is none in this repository, on purpose: an open-source build should
    /// not point at somebody's hosted product. A build that has one offers it
    /// first; a build that does not just asks for the server address, which is
    /// what a self-hosted install wants anyway.
    struct HostedPreset {
        let label: String
        /// `https://{workspace}.example.com`, or empty when only a directory is set.
        let serverTemplate: String
    }

    static let hosted: HostedPreset? = {
        let label = value("HostedLabel")
        let template = value("HostedServerTemplate")
        let directory = value("HostedSignIn")
        guard !label.isEmpty || !template.isEmpty || !directory.isEmpty else { return nil }
        var withScheme = ""
        if template.contains("{workspace}") {
            withScheme = template.contains("://") ? template : "https://\(template)"
            if !withScheme.hasPrefix("https://") { withScheme = "" }
        }
        guard !withScheme.isEmpty || !directory.isEmpty else { return nil }
        return HostedPreset(label: label.isEmpty ? name : label, serverTemplate: withScheme)
    }()

    /// The account directory: e-mail and password in, the accounts that opens out.
    static let hostedSignInURL: URL? = {
        let raw = value("HostedSignIn")
        guard !raw.isEmpty else { return nil }
        // Plain HTTP only for a development directory on the local network.
        if let local = ServerAddress.normalize(raw), local.hasPrefix("http://") { return URL(string: local) }
        return httpsURL(raw)
    }()

    /// Turn an account name into the address it lives at.
    static func hostedServer(for account: String) -> String? {
        guard let hosted, !hosted.serverTemplate.isEmpty else { return nil }
        return HostedServer.resolve(account, template: hosted.serverTemplate)
    }

    private static func localized(_ key: String) -> URL? {
        let preferred = value("\(key)_\(AppLocale.languageCode)")
        let fallback = value("\(key)_en")
        return httpsURL(preferred.isEmpty ? fallback : preferred)
    }

    static var privacyPolicyURL: URL? { localized("PrivacyPolicyURL") }
    static var supportURL: URL? { localized("SupportURL") }

    /// The push gateway this build is entitled to: `development`, `production`,
    /// or empty when the build has no push notifications at all.
    static let apsEnvironment: String = value("ApsEnvironment").lowercased()

    /// Whether this build may ask for notification permission and register the
    /// phone. Without it the app never mentions push.
    static var hasPush: Bool { !apsEnvironment.isEmpty }
}
