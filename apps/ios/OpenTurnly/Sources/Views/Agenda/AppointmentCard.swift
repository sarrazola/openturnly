import SwiftUI

/// One appointment in the agenda: time range, customer, service, professional,
/// status and money, with the professional's colour down the side.
///
/// At accessibility text sizes the time moves above the rest and the status
/// gets its own line, so nothing is cut to an ellipsis.
struct AppointmentCard: View {
    let appointment: Appointment
    var now = Date()
    @Environment(Workspace.self) private var workspace
    @Environment(\.dynamicTypeSize) private var typeSize
    @ScaledMetric(relativeTo: .headline) private var timeWidth: CGFloat = 50

    private var dimmed: Bool { appointment.status == .cancelled || appointment.status == .noShow }
    private var ongoing: Bool { AgendaRules.isOngoing(appointment, now: now) }

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                stacked
            } else {
                regular
            }
        }
        .opacity(dimmed ? 0.62 : 1)
        .card(padding: 14)
        .overlay {
            if ongoing {
                RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous)
                    .strokeBorder(Theme.brand.opacity(0.55), lineWidth: 1.5)
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(L.Appointment.title)
    }

    private var regular: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(workspace.clock.time(of: appointment.startsAt))
                    .font(.headline)
                    .fontDesign(.rounded)
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                Text(workspace.clock.time(of: appointment.endsAt))
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(Theme.subtle)
            }
            .frame(width: timeWidth, alignment: .leading)
            accentBar
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    name
                    Spacer(minLength: 4)
                    statusChip
                }
                service
                footer
            }
        }
    }

    private var stacked: some View {
        HStack(alignment: .top, spacing: 12) {
            accentBar
            VStack(alignment: .leading, spacing: 8) {
                Text("\(workspace.clock.time(of: appointment.startsAt)) – \(workspace.clock.time(of: appointment.endsAt))")
                    .font(.headline)
                    .fontDesign(.rounded)
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                name
                service
                statusChip
                footer
            }
        }
    }

    private var accentBar: some View {
        RoundedRectangle(cornerRadius: 2, style: .continuous)
            .fill(dimmed ? Theme.subtle.opacity(0.5) : workspace.color(for: appointment.resourceId))
            .frame(width: 4)
            .frame(maxHeight: .infinity)
    }

    private var name: some View {
        Text(appointment.customer.fullName)
            .font(.body.weight(.semibold))
            .foregroundStyle(Theme.ink)
            .strikethrough(appointment.status == .cancelled, color: Theme.subtle)
            .lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
    }

    private var service: some View {
        let minutes = Int(appointment.endsAt.timeIntervalSince(appointment.startsAt) / 60)
        return Text("\(appointment.serviceName) · \(WallTime.duration(minutes))")
            .font(.subheadline)
            .foregroundStyle(Theme.muted)
            .lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
    }

    private var statusChip: some View {
        StatusChip(status: AppointmentRules.displayStatus(appointment, now: now))
    }

    private var footer: some View {
        HStack(spacing: 8) {
            HStack(spacing: 5) {
                if appointment.resourceName != nil {
                    Circle().fill(dimmed ? Theme.subtle : workspace.color(for: appointment.resourceId)).frame(width: 7, height: 7)
                }
                Text(whoAndWhere).font(.footnote).foregroundStyle(Theme.muted).lineLimit(typeSize.isAccessibilitySize ? 3 : 1)
            }
            Spacer(minLength: 4)
            MoneyBadge(appointment: appointment, now: now)
        }
    }

    /// «Camila · Centro»: the professional, and the location when there are several.
    private var whoAndWhere: String {
        var parts: [String] = []
        if let name = appointment.resourceName { parts.append(name) }
        if workspace.activeLocations.count > 1, let location = appointment.locationName { parts.append(location) }
        return parts.joined(separator: " · ")
    }
}

/// The money at the bottom right of a card (§2.3): the price before the start,
/// then what is left to do about it. A professional sees only the price.
struct MoneyBadge: View {
    let appointment: Appointment
    var now = Date()
    @Environment(Workspace.self) private var workspace

    var body: some View {
        switch AppointmentRules.badge(appointment, isAdmin: workspace.isAdmin, now: now) {
        case .price(let amount):
            Text(workspace.money(amount))
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(Theme.muted)
        case .notCharged:
            Chip(text: L.Checkout.unpaid, tone: .warning)
        case .deposited(let amount):
            Chip(text: L.Checkout.deposited(workspace.money(amount)), tone: .neutral)
        case .left(let amount):
            Chip(text: L.Checkout.left(workspace.money(amount)), tone: .warning)
        case .toClose:
            Chip(text: L.Checkout.toClose, tone: .warning)
        case .paid:
            Chip(text: L.Checkout.paid, tone: .info, symbol: "checkmark")
        case .none:
            EmptyView()
        }
    }
}
