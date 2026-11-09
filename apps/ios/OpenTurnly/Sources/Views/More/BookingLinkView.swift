import CoreImage.CIFilterBuiltins
import SwiftUI

/// The booking page to share with customers: who it is for, its address with
/// buttons to share, copy or open it, and a QR code for the counter.
struct BookingLinkView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.openURL) private var openURL
    @State private var qr: UIImage?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                header
                if let url = workspace.bookingURL {
                    linkSection(url)
                    qrSection
                }
            }
            .padding(16)
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
        }
        .background(Theme.canvas)
        .navigationTitle(L.More.bookingLink)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: workspace.bookingURL) { qr = workspace.bookingURL.flatMap { QRCode.image(for: $0.absoluteString) } }
    }

    private var header: some View {
        HStack(spacing: 14) {
            BusinessLogo(business: workspace.business, server: workspace.server, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                Text(workspace.business.name).font(.headline).foregroundStyle(Theme.ink)
                if let description = TextRules.clean(workspace.business.description) {
                    Text(description).font(.subheadline).foregroundStyle(Theme.muted).lineLimit(2)
                }
            }
            Spacer(minLength: 0)
        }
        .card()
    }

    private func linkSection(_ url: URL) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Info.bookingLink)
            VStack(alignment: .leading, spacing: 14) {
                Text(L.Info.bookingHint).font(.subheadline).foregroundStyle(Theme.muted)
                Text(url.absoluteString.replacingOccurrences(of: "https://", with: "").replacingOccurrences(of: "http://", with: ""))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.brand)
                    .textSelection(.enabled)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                HStack(spacing: 10) {
                    ShareLink(item: url, subject: Text(workspace.business.name), message: Text(L.Info.shareText(workspace.business.name))) {
                        pill(L.Common.share, "square.and.arrow.up", filled: true)
                    }
                    Button {
                        UIPasteboard.general.url = url
                        toasts.show(L.Info.linkCopied)
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
            }
            .card()
        }
    }

    private var qrSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Info.qr)
            VStack(spacing: 14) {
                Text(L.Info.qrHint).font(.subheadline).foregroundStyle(Theme.muted).frame(maxWidth: .infinity, alignment: .leading)
                if let qr {
                    Image(uiImage: qr)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 200, height: 200)
                        .padding(14)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.line, lineWidth: 1))
                        .accessibilityLabel(L.Info.qr)
                    Text(workspace.business.name).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                    let image = Image(uiImage: QRCode.withQuietZone(qr))
                    ShareLink(item: image, preview: SharePreview(workspace.business.name, image: image)) {
                        Label(L.Info.shareQR, systemImage: "qrcode").font(.subheadline.weight(.semibold))
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .card()
        }
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

    /// The code on white with a margin around it, so a scanner finds its edges
    /// whatever it is printed on.
    static func withQuietZone(_ code: UIImage) -> UIImage {
        let margin = code.size.width / 8
        let size = CGSize(width: code.size.width + margin * 2, height: code.size.height + margin * 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = code.scale
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            UIColor.white.setFill()
            context.fill(CGRect(origin: .zero, size: size))
            context.cgContext.interpolationQuality = .none
            code.draw(in: CGRect(x: margin, y: margin, width: code.size.width, height: code.size.height))
        }
    }
}
