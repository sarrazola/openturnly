import SwiftUI

// MARK: Surfaces

/// A raised card: 16pt corners, a whisper of shadow in light mode and a hairline
/// border in dark mode, where shadows don't read.
struct CardBackground: ViewModifier {
    var padding: CGFloat = 16
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
            .overlay {
                if scheme == .dark {
                    RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous).strokeBorder(Theme.line, lineWidth: 0.5)
                }
            }
            .shadow(color: .black.opacity(scheme == .dark ? 0 : 0.05), radius: 10, x: 0, y: 3)
    }
}

extension View {
    func card(padding: CGFloat = 16) -> some View { modifier(CardBackground(padding: padding)) }
}

/// A small status pill: tinted background, strong text.
struct Chip: View {
    let text: String
    var tone: Tone = .neutral
    var symbol: String?

    var body: some View {
        HStack(spacing: 4) {
            if let symbol { Image(systemName: symbol).font(.caption2.weight(.bold)) }
            Text(text).font(.caption.weight(.semibold)).lineLimit(1)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .foregroundStyle(tone.foreground)
        .background(tone.background, in: Capsule())
        .fixedSize()
    }
}

/// Round initials, or a photo when there is one.
struct Avatar: View {
    let name: String
    var colorHex: String?
    var size: CGFloat = 44
    var photoURL: URL?

    var body: some View {
        let color = Theme.professional(colorHex ?? TextRules.avatarColor(name))
        ZStack {
            Circle().fill(color.opacity(0.16))
            Text(TextRules.initials(name))
                .font(.system(size: size * 0.38, weight: .semibold, design: .rounded))
                .foregroundStyle(color)
            if let photoURL {
                AsyncImage(url: photoURL) { phase in
                    if let image = phase.image { image.resizable().scaledToFill() }
                }
                .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// A rounded, tinted square with an SF Symbol, like the system's settings rows.
struct IconTile: View {
    let symbol: String
    var color: Color = Theme.brand
    var size: CGFloat = 30

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.5, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(color.gradient, in: RoundedRectangle(cornerRadius: size * 0.28, style: .continuous))
            .accessibilityHidden(true)
    }
}

// MARK: Buttons

/// The main action of a screen: full width, filled with the brand colour.
struct PrimaryButton: View {
    let title: String
    var symbol: String?
    var busy = false
    var disabled = false
    var role: ButtonRole?
    let action: () -> Void

    var body: some View {
        Button(role: role) {
            Haptics.tap()
            action()
        } label: {
            ZStack {
                HStack(spacing: 8) {
                    if let symbol { Image(systemName: symbol) }
                    Text(title)
                }
                .opacity(busy ? 0 : 1)
                if busy { ProgressView().tint(Theme.onBrand) }
            }
            .font(.body.weight(.semibold))
            .foregroundStyle(role == .destructive ? .white : Theme.onBrand)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(
                (role == .destructive ? Theme.danger : Theme.brand).opacity(disabled ? 0.4 : 1),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .buttonStyle(PressableStyle())
        .disabled(disabled || busy)
    }
}

/// A quieter action: tinted background, brand text.
struct SecondaryButton: View {
    let title: String
    var symbol: String?
    var tint: Color = Theme.brand
    var busy = false
    let action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            action()
        } label: {
            ZStack {
                HStack(spacing: 7) {
                    if let symbol { Image(systemName: symbol) }
                    Text(title).lineLimit(1).minimumScaleFactor(0.8)
                }
                .opacity(busy ? 0 : 1)
                if busy { ProgressView().tint(tint) }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(PressableStyle())
        .disabled(busy)
    }
}

/// Shrinks a little while pressed.
struct PressableStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.85 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Call, WhatsApp and e-mail as round buttons with a caption.
struct ContactActions: View {
    let phone: String?
    let email: String?
    @Environment(\.openURL) private var openURL

    var body: some View {
        HStack(spacing: 10) {
            if let url = Phone.callURL(phone) {
                action(L.Common.call, "phone.fill", Theme.brand) { openURL(url) }
            }
            if let url = Phone.whatsAppURL(phone) {
                action(L.Common.whatsapp, "message.fill", Color(hex: "#16a34a")) { openURL(url) }
            }
            if let url = Phone.mailURL(email) {
                action(L.Common.email, "envelope.fill", Color(hex: "#0284c7")) { openURL(url) }
            }
        }
    }

    private func action(_ title: String, _ symbol: String, _ color: Color, _ run: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            run()
        } label: {
            VStack(spacing: 6) {
                Image(systemName: symbol).font(.system(size: 17, weight: .semibold))
                Text(title).font(.caption.weight(.medium)).lineLimit(1).minimumScaleFactor(0.8)
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, minHeight: 62)
            .background(color.opacity(0.11), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PressableStyle())
    }
}

// MARK: Content blocks

/// A label and its value on one line, the value trailing.
struct InfoRow: View {
    let label: String
    let value: String
    var symbol: String?
    var valueColor: Color = Theme.ink

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            if let symbol {
                Image(systemName: symbol).font(.subheadline).foregroundStyle(Theme.subtle).frame(width: 20)
            }
            Text(label).font(.subheadline).foregroundStyle(Theme.muted)
            Spacer(minLength: 12)
            Text(value).font(.subheadline.weight(.medium)).foregroundStyle(valueColor).multilineTextAlignment(.trailing)
        }
        .padding(.vertical, 10)
        .accessibilityElement(children: .combine)
    }
}

/// An uppercase caption over a group of cards.
struct SectionTitle: View {
    let text: String
    var trailing: String?

    var body: some View {
        HStack {
            Text(text).font(.footnote.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase).tracking(0.4)
            Spacer()
            if let trailing { Text(trailing).font(.footnote).foregroundStyle(Theme.subtle) }
        }
        .padding(.horizontal, 4)
        .accessibilityAddTraits(.isHeader)
    }
}

/// A friendly empty screen: an icon, a title, one line, maybe an action.
struct EmptyState: View {
    let symbol: String
    let title: String
    var message: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 30, weight: .medium))
                .foregroundStyle(Theme.brand)
                .frame(width: 72, height: 72)
                .background(Theme.brandSoft, in: Circle())
            Text(title).font(.headline).foregroundStyle(Theme.ink).multilineTextAlignment(.center)
            if let message {
                Text(message).font(.subheadline).foregroundStyle(Theme.muted).multilineTextAlignment(.center)
            }
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.brand)
                    .padding(.top, 4)
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 28)
        .frame(maxWidth: .infinity)
    }
}

/// A failed load with a way to try again.
struct ErrorState: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        EmptyState(symbol: "wifi.exclamationmark", title: L.Common.couldNotLoad, message: message, actionTitle: L.Common.retry, action: retry)
    }
}

/// A big number with its label, for summaries.
struct StatTile: View {
    let label: String
    let value: String
    var detail: String?
    var symbol: String?
    var tint: Color = Theme.brand
    var valueColor: Color = Theme.ink

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                if let symbol { Image(systemName: symbol).font(.caption.weight(.bold)).foregroundStyle(tint) }
                Text(label).font(.caption.weight(.medium)).foregroundStyle(Theme.muted).lineLimit(1).minimumScaleFactor(0.8)
            }
            Text(value)
                .font(.title3.weight(.bold))
                .fontDesign(.rounded)
                .foregroundStyle(valueColor)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            if let detail {
                Text(detail).font(.caption).foregroundStyle(Theme.subtle).lineLimit(2)
            }
        }
        .card(padding: 14)
        .accessibilityElement(children: .combine)
    }
}

// MARK: Feedback

/// A short confirmation that slides in from the top and leaves on its own.
@MainActor
@Observable
final class ToastCenter {
    struct Toast: Equatable, Identifiable {
        let id = UUID()
        let text: String
        let isError: Bool
    }

    var current: Toast?

    func show(_ text: String, error: Bool = false) {
        let toast = Toast(text: text, isError: error)
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) { current = toast }
        if error { Haptics.error() } else { Haptics.success() }
        Task {
            try? await Task.sleep(for: .seconds(error ? 3.5 : 2.4))
            if current?.id == toast.id {
                withAnimation(.easeOut(duration: 0.25)) { current = nil }
            }
        }
    }

    func error(_ error: Error) { show(ErrorText.message(error), error: true) }
}

struct ToastOverlay: View {
    @Environment(ToastCenter.self) private var toasts

    var body: some View {
        VStack {
            if let toast = toasts.current {
                HStack(spacing: 10) {
                    Image(systemName: toast.isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                        .foregroundStyle(toast.isError ? Theme.danger : Theme.brand)
                    Text(toast.text).font(.subheadline.weight(.medium)).foregroundStyle(Theme.ink)
                        .multilineTextAlignment(.leading)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(.regularMaterial, in: Capsule())
                .overlay(Capsule().strokeBorder(Theme.line, lineWidth: 0.5))
                .shadow(color: .black.opacity(0.12), radius: 16, y: 6)
                .padding(.horizontal, 20)
                .transition(.move(edge: .top).combined(with: .opacity))
                .onTapGesture { withAnimation { toasts.current = nil } }
                .accessibilityAddTraits(.isStaticText)
            }
            Spacer()
        }
        .padding(.top, 8)
        .allowsHitTesting(toasts.current != nil)
    }
}

// MARK: Skeletons

/// Placeholders in the shape of what is on its way, with a slow sheen.
struct SkeletonBlock: View {
    var width: CGFloat?
    var height: CGFloat = 12
    var radius: CGFloat = 6

    var body: some View {
        RoundedRectangle(cornerRadius: radius, style: .continuous)
            .fill(Theme.line)
            .frame(width: width, height: height)
    }
}

private struct Sheen: ViewModifier {
    @State private var dim = false

    func body(content: Content) -> some View {
        content
            .opacity(dim ? 0.45 : 1)
            .onAppear { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { dim = true } }
    }
}

extension View {
    func skeleton() -> some View {
        modifier(Sheen())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L.Common.loading)
    }
}

/// Cards the size of agenda appointments.
struct CardsSkeleton: View {
    var count = 4

    var body: some View {
        VStack(spacing: 12) {
            ForEach(0..<count, id: \.self) { index in
                HStack(alignment: .top, spacing: 14) {
                    VStack(alignment: .leading, spacing: 6) {
                        SkeletonBlock(width: 44, height: 14)
                        SkeletonBlock(width: 34, height: 10)
                    }
                    RoundedRectangle(cornerRadius: 2).fill(Theme.line).frame(width: 4, height: 64)
                    VStack(alignment: .leading, spacing: 9) {
                        SkeletonBlock(width: index.isMultiple(of: 2) ? 150 : 120, height: 15)
                        SkeletonBlock(width: 180, height: 11)
                        HStack {
                            SkeletonBlock(width: 70, height: 18, radius: 9)
                            SkeletonBlock(width: 60, height: 18, radius: 9)
                        }
                    }
                    Spacer()
                }
                .card()
            }
        }
        .skeleton()
    }
}

/// Rows the size of list items with an avatar.
struct RowsSkeleton: View {
    var count = 8

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<count, id: \.self) { index in
                HStack(spacing: 12) {
                    SkeletonBlock(width: 44, height: 44, radius: 22)
                    VStack(alignment: .leading, spacing: 8) {
                        SkeletonBlock(width: index.isMultiple(of: 2) ? 150 : 120, height: 14)
                        SkeletonBlock(width: 190, height: 11)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
        .skeleton()
    }
}

// MARK: Inputs

/// An amount typed in the currency's major units, bound to minor units.
struct MoneyField: View {
    let title: String
    @Binding var amount: Int?
    let currency: String
    @State private var text: String = ""
    @FocusState private var focused: Bool
    @Environment(\.multilineTextAlignment) private var alignment

    var body: some View {
        HStack(spacing: 4) {
            // Trailing amounts keep the symbol next to the digits.
            if alignment == .trailing { Spacer(minLength: 0) }
            Text(Money.symbol(currency)).foregroundStyle(Theme.muted)
            field.fixedSize(horizontal: alignment == .trailing, vertical: false)
        }
        .contentShape(Rectangle())
        .onTapGesture { focused = true }
        .onAppear { text = amount.map { Money.editable($0, currency: currency) } ?? "" }
        .onChange(of: amount) { _, value in
            // Reflect changes made elsewhere (a total that follows the lines) while not typing.
            guard !focused else { return }
            let shown = Money.parse(text, currency: currency)
            if shown != value { text = value.map { Money.editable($0, currency: currency) } ?? "" }
        }
        .onChange(of: focused) { _, isFocused in
            if !isFocused, let amount { text = Money.editable(amount, currency: currency) }
        }
    }

    private var field: some View {
        TextField(title, text: $text)
            .keyboardType(Money.digits(currency) == 0 ? .numberPad : .decimalPad)
            .focused($focused)
            .monospacedDigit()
            .onChange(of: text) { _, value in
                amount = Money.parse(value, currency: currency)
            }
    }
}

/// A phone number with its country: flag and dial code, then the national number.
struct PhoneField: View {
    @Binding var iso: String
    @Binding var national: String
    var placeholder: String = ""
    @State private var picking = false

    var body: some View {
        HStack(spacing: 10) {
            Button {
                picking = true
            } label: {
                HStack(spacing: 4) {
                    Text(Phone.flag(iso))
                    Text("+\(Phone.dialCode(iso))").foregroundStyle(Theme.ink).monospacedDigit()
                    Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundStyle(Theme.subtle)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Phone.name(iso))
            Divider().frame(height: 22)
            TextField(placeholder, text: $national)
                .keyboardType(.phonePad)
                .textContentType(.telephoneNumber)
                .onChange(of: national) { _, value in
                    let cleaned = value.filter { $0.isNumber || $0 == " " }
                    if cleaned != value { national = cleaned }
                }
        }
        .sheet(isPresented: $picking) { CountryPicker(selected: $iso) }
    }
}

struct CountryPicker: View {
    @Binding var selected: String
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private var rows: [Phone.Country] {
        let all = Phone.sortedByName
        let needle = query.trimmingCharacters(in: .whitespaces)
        guard !needle.isEmpty else { return all }
        let digits = needle.replacingOccurrences(of: "+", with: "")
        return all.filter { TextRules.matches(needle, [$0.name]) || $0.code.hasPrefix(digits) || $0.iso.lowercased() == needle.lowercased() }
    }

    var body: some View {
        NavigationStack {
            List(rows) { country in
                Button {
                    selected = country.iso
                    Haptics.selection()
                    dismiss()
                } label: {
                    HStack {
                        Text(country.flag)
                        Text(country.name).foregroundStyle(Theme.ink)
                        Spacer()
                        Text("+\(country.code)").foregroundStyle(Theme.muted).monospacedDigit()
                        if country.iso == selected { Image(systemName: "checkmark").foregroundStyle(Theme.brand) }
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always))
            .navigationTitle(L.Common.phone)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
        }
        .presentationDetents([.large])
    }
}

/// A validation message under a field.
struct FieldError: View {
    let text: String?

    var body: some View {
        if let text {
            Label(text, systemImage: "exclamationmark.circle.fill")
                .font(.footnote)
                .foregroundStyle(Theme.danger)
        }
    }
}

// MARK: Formatting helpers for views

extension Business {
    var clock: BusinessClock { BusinessClock(identifier: timezone) }
    func money(_ amount: Int) -> String { Money.format(amount, currency: currency) }
    func price(_ amount: Int?) -> String { amount.map(money) ?? L.Appointment.priceOnRequest }
}

extension View {
    /// A "Done" button above the keyboard: number and phone pads have no return key.
    func keyboardDoneButton() -> some View {
        toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(L.Common.done) {
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
                .fontWeight(.semibold)
            }
        }
    }
}

/// A bar pinned to the bottom that steps aside while the keyboard is up, so it
/// never covers what is being typed or the results under a search field.
private struct BottomBar<Bar: View>: ViewModifier {
    @ViewBuilder let bar: () -> Bar
    @State private var typing = false

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom) {
                if !typing { bar() }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in typing = true }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in typing = false }
    }
}

extension View {
    func bottomBar<Bar: View>(@ViewBuilder _ bar: @escaping () -> Bar) -> some View {
        modifier(BottomBar(bar: bar))
    }
}
