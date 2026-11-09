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

    @ViewBuilder
    private var statusChip: some View {
        if ongoing {
            Chip(text: L.Agenda.now, tone: .success, symbol: "circle.fill")
        } else {
            let status = AppointmentRules.displayStatus(appointment.status, startsAt: appointment.startsAt, now: now)
            Chip(text: L.Status.label(status), tone: Tone.of(status))
        }
    }

    private var footer: some View {
        HStack(spacing: 8) {
            if let name = appointment.resourceName {
                HStack(spacing: 5) {
                    Circle().fill(workspace.color(for: appointment.resourceId)).frame(width: 7, height: 7)
                    Text(name).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 4)
            MoneyBadge(appointment: appointment)
        }
    }
}

/// Paid, deposit left, or the price.
struct MoneyBadge: View {
    let appointment: Appointment
    @Environment(Workspace.self) private var workspace

    var body: some View {
        switch AppointmentRules.paymentState(appointment) {
        case .paid(let number):
            Chip(text: L.Checkout.paidIn(number), tone: .info, symbol: "checkmark")
        case .covered:
            Chip(text: L.Checkout.paid, tone: .info, symbol: "checkmark")
        case .partly(let owed, _):
            Chip(text: workspace.money(owed), tone: .warning, symbol: "tray.and.arrow.down.fill")
        case .none:
            EmptyView()
        case .unpaid:
            if workspace.isAdmin, let price = appointment.priceAmount {
                Text(workspace.money(max(0, price - appointment.discountAmount)))
                    .font(.footnote.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(Theme.muted)
            }
        }
    }
}
