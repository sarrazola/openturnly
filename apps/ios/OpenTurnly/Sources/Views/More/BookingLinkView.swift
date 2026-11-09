import CoreImage.CIFilterBuiltins
import SwiftUI

/// The booking page to share with customers: its address, a QR code, and
/// buttons to share, copy or open it.
struct BookingLinkView: View {
    @Environment(Workspace.self) private var workspace

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if let url = workspace.bookingURL {
                    BookingLinkCard(url: url, name: workspace.business.name)
                    Text(L.Info.bookingHint)
                        .font(.footnote)
                        .foregroundStyle(Theme.muted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
            }
            .padding(16)
        }
        .background(Theme.canvas)
        .navigationTitle(L.More.bookingLink)
        .navigationBarTitleDisplayMode(.inline)
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
