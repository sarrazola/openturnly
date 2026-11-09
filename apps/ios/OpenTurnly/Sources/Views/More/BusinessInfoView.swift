import CoreImage.CIFilterBuiltins
import SwiftUI

/// The business's name and contact, and its booking page to share as a link or a QR code.
struct BusinessInfoView: View {
    @Environment(AppModel.self) private var model
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.openURL) private var openURL
    @State private var name = ""
    @State private var description = ""
    @State private var phone = ""
    @State private var email = ""
    @State private var address = ""
    @State private var busy = false
    @State private var prepared = false
    @State private var failure: Error?

    private var business: Business { workspace.business }

    private var dirty: Bool {
        name != business.name || description != (business.description ?? "") || phone != (business.phone ?? "")
            || email != (business.email ?? "") || address != (business.address ?? "")
    }

    var body: some View {
        Form {
            if let url = workspace.bookingURL {
                Section {
                    BookingLinkCard(url: url, name: business.name)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                } header: {
                    Text(L.Info.bookingLink)
                } footer: {
                    Text(L.Info.bookingHint)
                }
            }
            Section(L.Info.name) {
                TextField(L.Info.name, text: $name)
                TextField(L.Info.description, text: $description, axis: .vertical).lineLimit(2...5)
            }
            Section(L.Info.contact) {
                LabeledField(symbol: "phone", placeholder: L.Info.phone, text: $phone, keyboard: .phonePad)
                LabeledField(symbol: "envelope", placeholder: L.Info.email, text: $email, keyboard: .emailAddress)
                LabeledField(symbol: "mappin.and.ellipse", placeholder: L.Info.address, text: $address, keyboard: .default)
            }
            Section(L.Info.region) {
                LabeledContent(L.Info.timezone, value: business.timezone.replacingOccurrences(of: "_", with: " "))
                LabeledContent(L.Info.currency, value: business.currency)
            }
            if let failure {
                Section { FieldError(text: ErrorText.message(failure)) }
            }
        }
        .navigationTitle(L.Info.title)
        .keyboardDoneButton()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if busy { ProgressView() } else {
                    Button(L.Common.save) { Task { await save() } }
                        .fontWeight(.semibold)
                        .disabled(!dirty || name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .onAppear(perform: prepare)
    }

    private func prepare() {
        guard !prepared else { return }
        prepared = true
        name = business.name
        description = business.description ?? ""
        phone = business.phone ?? ""
        email = business.email ?? ""
        address = business.address ?? ""
    }

    private func save() async {
        busy = true
        failure = nil
        defer { busy = false }
        do {
            let updated = try await workspace.api.updateBusiness(business.id, [
                "name": .string(name.trimmingCharacters(in: .whitespaces)),
                "description": .text(description),
                "phone": .text(phone),
                "email": .string(email.trimmingCharacters(in: .whitespaces)),
                "address": .text(address),
            ])
            workspace.business = updated
            toasts.show(L.Common.saved)
            await model.loadBusinesses()
        } catch {
            failure = error
            Haptics.error()
        }
    }
}

private struct LabeledField: View {
    let symbol: String
    let placeholder: String
    @Binding var text: String
    let keyboard: UIKeyboardType

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(Theme.subtle).frame(width: 20)
            TextField(placeholder, text: $text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(keyboard == .emailAddress ? .never : .sentences)
                .autocorrectionDisabled(keyboard != .default)
        }
    }
}

/// The booking page: its address, share, copy, open, and a QR code.
struct BookingLinkCard: View {
    let url: URL
    let name: String
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.openURL) private var openURL
    @State private var qr: UIImage?

    var body: some View {
        VStack(spacing: 16) {
            if let qr {
                Image(uiImage: qr)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 190, height: 190)
                    .padding(14)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
                    .accessibilityLabel(L.Info.qr)
            }
            Text(url.absoluteString.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: ""))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.brand)
                .multilineTextAlignment(.center)
                .textSelection(.enabled)
            HStack(spacing: 10) {
                ShareLink(item: url, subject: Text(name), message: Text(L.Info.shareText(name))) {
                    pill(L.Common.share, "square.and.arrow.up", filled: true)
                }
                Button {
                    UIPasteboard.general.url = url
                    toasts.show(L.Common.copied)
                } label: {
                    pill(L.Common.copy, "doc.on.doc", filled: false)
                }
                Button {
                    openURL(url)
                } label: {
                    pill(L.Common.open, "safari", filled: false)
                }
            }
            .buttonStyle(PressableStyle())
            if let qr {
                ShareLink(item: Image(uiImage: qr), preview: SharePreview(name, image: Image(uiImage: qr))) {
                    Label(L.Info.shareQR, systemImage: "qrcode").font(.subheadline.weight(.semibold))
                }
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: [Theme.brandSoft, Theme.surface], startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
        )
        .task(id: url) { qr = QRCode.image(for: url.absoluteString) }
    }

    private func pill(_ title: String, _ symbol: String, filled: Bool) -> some View {
        Label(title, systemImage: symbol)
            .font(.subheadline.weight(.semibold))
            .labelStyle(.titleAndIcon)
            .lineLimit(1)
            .minimumScaleFactor(0.8)
            .foregroundStyle(filled ? Theme.onBrand : Theme.brand)
            .frame(maxWidth: .infinity, minHeight: 42)
            .background(filled ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.brand.opacity(0.12)),
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

/// QR codes drawn with Core Image: sharp, no network, no library.
enum QRCode {
    static func image(for text: String, scale: CGFloat = 12) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(text.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: scale, y: scale)) else { return nil }
        let context = CIContext()
        guard let cgImage = context.createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
