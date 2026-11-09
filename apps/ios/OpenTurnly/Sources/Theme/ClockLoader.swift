import SwiftUI

/// What the app shows while it restores the session or loads a business, and the
/// sign-in header while signing in: the brand mark on its gradient tile, with the
/// brand's name under it.
///
/// When the brand's logo is the example's clock (`Brand.animatedMark`), the mark
/// is drawn here and its hands turn: the long one once every 1.6 s, the short one
/// twelve times slower, while the square dot breathes. Otherwise the brand's own
/// white logo pulses gently. With Reduce Motion on, it holds still. The Android
/// app draws the same mark with the same timings and proportions.
struct ClockLoader: View {
    /// Off: the mark at rest (the sign-in header when nothing is happening).
    var animating = true
    var showsName = true
    var size: CGFloat = 88
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()

    private var moving: Bool { animating && !reduceMotion }

    var body: some View {
        // The name hangs under the tile without moving it, so a centred loader
        // keeps the tile where the launch screen drew it.
        TimelineView(.animation(minimumInterval: nil, paused: !moving)) { context in
            let elapsed = moving ? max(0, context.date.timeIntervalSince(start)) : 0
            tile(elapsed)
        }
        .overlay(alignment: .top) {
            if showsName {
                Text(Brand.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.muted)
                    .fixedSize()
                    .offset(y: size + 16)
            }
        }
        .onAppear { start = Date() }
        .onChange(of: animating) { _, on in if on { start = Date() } }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(animating ? "\(Brand.name), \(L.Common.loading)" : Brand.name)
    }

    private func tile(_ elapsed: TimeInterval) -> some View {
        let corner = 18 * size / 88
        return ZStack {
            RoundedRectangle(cornerRadius: corner, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: "#14b8a6"), Brand.primaryColor], startPoint: .topLeading, endPoint: .bottomTrailing))
            if Brand.animatedMark {
                ClockMark(elapsed: elapsed)
                    .frame(width: size * 0.6, height: size * 0.6)
            } else {
                Image("BrandLogoWhite")
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * 0.6, height: size * 0.6)
            }
        }
        .frame(width: size, height: size)
        .scaleEffect(Brand.animatedMark ? 1 : ClockMark.pulse(elapsed))
        .shadow(color: Brand.primaryColor.opacity(0.35), radius: 14 * size / 88, y: 6 * size / 88)
    }
}

/// The clock mark of `docs/brand/openturnly-mark.svg`, drawn in its 880 × 880 box:
/// an open circle, an "L" of two hands and a rounded square dot.
struct ClockMark: View {
    /// Seconds since the hands started turning; 0 is the mark at rest.
    var elapsed: TimeInterval = 0
    var color: Color = .white

    /// One turn of the long hand.
    static let minuteTurn: TimeInterval = 1.6
    /// One turn of the short hand: twelve times slower.
    static let hourTurn: TimeInterval = 1.6 * 12
    /// One breath of the dot: opacity 1 → 0.55 → 1.
    static let dotBreath: TimeInterval = 1.6

    /// The dot's opacity at a moment.
    static func dotOpacity(_ elapsed: TimeInterval) -> Double {
        1 - 0.225 * (1 - cos(2 * .pi * elapsed / dotBreath))
    }

    /// The still logo's scale when the mark isn't the clock: 1 → 1.04 → 1 every 1.2 s.
    static func pulse(_ elapsed: TimeInterval) -> CGFloat {
        CGFloat(1 + 0.02 * (1 - cos(2 * .pi * elapsed / 1.2)))
    }

    var body: some View {
        Canvas { context, size in
            let scale = min(size.width, size.height) / 880
            context.translateBy(x: (size.width - 880 * scale) / 2, y: (size.height - 880 * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            let stroke = StrokeStyle(lineWidth: 96, lineCap: .round, lineJoin: .round)

            context.stroke(Self.ring, with: .color(color), style: stroke)

            let pivot = CGPoint(x: 441, y: 469)
            let minute = -Double.pi / 2 + 2 * .pi * elapsed / Self.minuteTurn
            let hour = 2 * .pi * elapsed / Self.hourTurn
            var hands = Path()
            hands.move(to: Self.point(pivot, length: 187, angle: minute))
            hands.addLine(to: pivot)
            hands.addLine(to: Self.point(pivot, length: 132, angle: hour))
            context.stroke(hands, with: .color(color), style: stroke)

            let dot = Path(roundedRect: CGRect(x: 652, y: 124, width: 151, height: 152), cornerRadius: 44, style: .circular)
            context.fill(dot, with: .color(color.opacity(Self.dotOpacity(elapsed))))
        }
        .accessibilityHidden(true)
    }

    private static func point(_ center: CGPoint, length: Double, angle: Double) -> CGPoint {
        CGPoint(x: center.x + length * cos(angle), y: center.y + length * sin(angle))
    }

    /// The circle, open at the top right: from (528.5, 81) round the left and the
    /// bottom to (805.2, 387.2), radius 370 around (441, 441).
    private static let ring: Path = {
        let center = CGPoint(x: 441, y: 441)
        let from = atan2(81 - 441.0, 528.5 - 441)
        let to = atan2(387.2 - 441.0, 805.2 - 441)
        // Going the long way, through the top, the left and the bottom.
        let sweep = from - (to - 2 * .pi)
        var path = Path()
        let steps = 120
        for step in 0...steps {
            let angle = from - sweep * Double(step) / Double(steps)
            let point = CGPoint(x: center.x + 370 * cos(angle), y: center.y + 370 * sin(angle))
            if step == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        return path
    }()
}
