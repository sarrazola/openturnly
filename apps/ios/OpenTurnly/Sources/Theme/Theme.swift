import SwiftUI
import UIKit

/// Colour.
///
/// Brand teal on neutral zinc greys, in light and dark. Every colour is dynamic,
/// so a screen follows the phone's appearance without reading the colour scheme.
enum Theme {
    static let brand = Color.dynamic(0x0f766e, 0x14b8a6)
    static let brandStrong = Color.dynamic(0x115e59, 0x2dd4bf)
    /// Soft brand wash behind selected chips and highlights.
    static let brandSoft = Color.dynamic(0xf0fdfa, 0x0b2622)
    static let brandSoftStrong = Color.dynamic(0xccfbf1, 0x123f3a)
    /// Text and icons on a filled brand surface.
    static let onBrand = Color.dynamic(0xffffff, 0x042f2e)

    static let canvas = Color.dynamic(0xf4f5f5, 0x0a0a0b)
    static let surface = Color.dynamic(0xffffff, 0x161618)
    static let fill = Color.dynamic(0xf1f2f3, 0x222225)
    static let ink = Color.dynamic(0x18181b, 0xfafafa)
    static let muted = Color.dynamic(0x6b6b74, 0xa1a1aa)
    static let subtle = Color.dynamic(0xa1a1aa, 0x6b6b74)
    static let line = Color.dynamic(0xe4e4e7, 0x2a2a2e)
    static let danger = Color.dynamic(0xdc2626, 0xf87171)
    static let success = Color.dynamic(0x15803d, 0x4ade80)
    static let warning = Color.dynamic(0xb45309, 0xfbbf24)

    static let cardRadius: CGFloat = 16

    /// A professional's calendar colour, lifted a little in dark mode.
    static func professional(_ hex: String) -> Color {
        let base = UIColor(hex: hex)
        return Color(UIColor { traits in
            traits.userInterfaceStyle == .dark ? base.lighter(by: 0.25) : base
        })
    }
}

/// A chip's colour pair: tinted background, strong text.
enum Tone {
    case brand, success, warning, danger, orange, info, neutral

    var background: Color {
        switch self {
        case .brand: return .dynamic(0xccfbf1, 0x123f3a)
        case .success: return .dynamic(0xdcfce7, 0x14351f)
        case .warning: return .dynamic(0xfef3c7, 0x3d2c08)
        case .danger: return .dynamic(0xfee2e2, 0x421616)
        case .orange: return .dynamic(0xffedd5, 0x40210c)
        case .info: return .dynamic(0xdbeafe, 0x172a4a)
        case .neutral: return .dynamic(0xf1f5f9, 0x262a31)
        }
    }

    var foreground: Color {
        switch self {
        case .brand: return .dynamic(0x115e59, 0x5eead4)
        case .success: return .dynamic(0x166534, 0x86efac)
        case .warning: return .dynamic(0x92400e, 0xfcd34d)
        case .danger: return .dynamic(0x991b1b, 0xfca5a5)
        case .orange: return .dynamic(0x9a3412, 0xfdba74)
        case .info: return .dynamic(0x1e40af, 0x93c5fd)
        case .neutral: return .dynamic(0x334155, 0xcbd5e1)
        }
    }

    static func of(_ status: AppointmentRules.DisplayStatus) -> Tone {
        switch status {
        case .upcoming: return .brand
        case .ongoing, .confirmed: return .success
        case .cancelled: return .danger
        case .noShow: return .orange
        case .pendingPayment: return .warning
        }
    }
}

extension UIColor {
    convenience init(hex: String) {
        let value = hex.replacingOccurrences(of: "#", with: "")
        let number = UInt32(value, radix: 16) ?? 0x777777
        self.init(rgb: value.count == 6 ? number : 0x777777)
    }

    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xff) / 255,
            green: CGFloat((rgb >> 8) & 0xff) / 255,
            blue: CGFloat(rgb & 0xff) / 255,
            alpha: 1
        )
    }

    func lighter(by amount: CGFloat) -> UIColor {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        getRed(&r, green: &g, blue: &b, alpha: &a)
        return UIColor(red: r + (1 - r) * amount, green: g + (1 - g) * amount, blue: b + (1 - b) * amount, alpha: a)
    }
}

extension Color {
    init(hex: String) { self.init(UIColor(hex: hex)) }

    static func dynamic(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

/// Haptic feedback on the actions that matter.
@MainActor
enum Haptics {
    static func success() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    static func warning() { UINotificationFeedbackGenerator().notificationOccurred(.warning) }
    static func error() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
    static func selection() { UISelectionFeedbackGenerator().selectionChanged() }
    static func tap() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
}
