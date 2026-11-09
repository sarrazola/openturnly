import SwiftUI

/// The open business's notifications, newest first.
///
/// Opening it reads them all: the bell and the icon's number drop at once, and
/// the ones that were unread keep a dot while the screen stays open.
struct NotificationsView: View {
    @Environment(Workspace.self) private var workspace
    @State private var items: [AppNotification] = []
    @State private var loaded = false
    @State private var error: Error?
    /// Unread when they were shown here.
    @State private var fresh: Set<String> = []
    /// The row whose appointment is being looked up.
    @State private var opening: String?
    @State private var now = Date()

    var body: some View {
        List {
            if !loaded {
                RowsSkeleton(count: 6).listRowInsets(EdgeInsets()).listRowSeparator(.hidden)
            } else if let error, items.isEmpty {
                ErrorState(message: ErrorText.message(error)) { Task { await load() } }.listRowSeparator(.hidden)
            } else if items.isEmpty {
                EmptyState(symbol: "bell", title: L.Notifications.emptyTitle, message: L.Notifications.emptyBody)
                    .listRowSeparator(.hidden)
            } else {
                ForEach(items) { item in
                    row(item)
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle(L.Notifications.title)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
        .task {
            // Keep "5 min ago" true while the screen stays open.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                now = Date()
            }
        }
    }

    private func row(_ item: AppNotification) -> some View {
        Button {
            open(item)
        } label: {
            NotificationRow(item: item, unread: fresh.contains(item.id), busy: opening == item.id,
                            time: Fmt.ago(item.createdAt, now: now, clock: workspace.clock))
        }
        .buttonStyle(.plain)
        .disabled(item.appointmentId == nil || opening != nil)
        .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
    }

    private func open(_ item: AppNotification) {
        guard let appointmentId = item.appointmentId else { return }
        Haptics.selection()
        opening = item.id
        Task {
            await workspace.openAppointment(appointmentId, near: item.appointmentStartsAt, onTop: true)
            opening = nil
        }
    }

    /// Fetch the list, then mark what is unread as read.
    private func load() async {
        do {
            let page = try await workspace.api.notifications(workspace.id)
            fresh.formUnion(page.notifications.filter { $0.readAt == nil }.map(\.id))
            items = page.notifications
            error = nil
            loaded = true
            if page.unread > 0 {
                let unreadTotal = try await workspace.api.markNotificationsRead(business: workspace.id)
                workspace.showCounts(unread: 0, unreadTotal: unreadTotal)
            } else {
                workspace.showCounts(unread: 0, unreadTotal: page.unreadTotal)
            }
        } catch is CancellationError {
        } catch {
            self.error = error
            loaded = true
        }
    }
}

private struct NotificationRow: View {
    let item: AppNotification
    let unread: Bool
    let busy: Bool
    let time: String

    private var kind: NotificationKind { NotificationKind(item.kind) }

    /// Green for a booking, blue for a move (like the agenda's edit action), red for a cancellation.
    private var tint: Color {
        switch kind {
        case .booked: return Theme.brand
        case .rescheduled: return Color(hex: "#0284c7")
        case .cancelled: return Theme.danger
        case .other: return Theme.muted
        }
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: kind.symbol)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint)
                .frame(width: 40, height: 40)
                .background(tint.opacity(0.12), in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Theme.ink)
                        .lineLimit(1)
                    Spacer(minLength: 4)
                    Text(time)
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .layoutPriority(1)
                }
                Text(item.body)
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            ZStack {
                if busy {
                    ProgressView().controlSize(.small)
                } else if unread {
                    Circle().fill(Theme.brand).frame(width: 9, height: 9)
                }
            }
            .frame(width: 16, height: 20)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel(unread ? "\(L.Notifications.unreadLabel), \(item.title), \(item.body), \(time)" : "\(item.title), \(item.body), \(time)")
        .accessibilityAddTraits(item.appointmentId == nil ? [] : .isButton)
    }
}

/// The agenda's bell: how many notifications this business has unread.
struct NotificationBell: View {
    @Environment(Workspace.self) private var workspace

    var body: some View {
        NavigationLink(value: Route.notifications) {
            Image(systemName: "bell")
                .overlay(alignment: .topTrailing) {
                    if let text = NotificationRules.badgeText(workspace.unread) {
                        Text(text)
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .monospacedDigit()
                            .foregroundStyle(.white)
                            .padding(.horizontal, 4)
                            .frame(minWidth: 17, minHeight: 17)
                            .background(Theme.danger, in: Capsule())
                            .offset(x: 9, y: -8)
                            .fixedSize()
                    }
                }
        }
        .accessibilityLabel(L.Notifications.bell(workspace.unread))
    }
}
