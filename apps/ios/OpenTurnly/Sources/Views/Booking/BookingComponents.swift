import SwiftUI

/// A titled block in a booking form.
struct FormBlock<Content: View>: View {
    let title: String
    var trailing: String?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: title, trailing: trailing)
            VStack(alignment: .leading, spacing: 0) { content }
                .card(padding: 14)
        }
    }
}

/// A row that opens a menu of choices.
struct MenuRow<Value: Hashable>: View {
    let label: String
    let symbol: String
    let options: [(value: Value, title: String, subtitle: String?)]
    @Binding var selection: Value
    var placeholder: String = ""

    var body: some View {
        Menu {
            ForEach(options, id: \.value) { option in
                Button {
                    Haptics.selection()
                    selection = option.value
                } label: {
                    if let subtitle = option.subtitle {
                        Text(option.title)
                        Text(subtitle)
                    } else {
                        Text(option.title)
                    }
                    if option.value == selection { Image(systemName: "checkmark") }
                }
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: symbol).foregroundStyle(Theme.brand).frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.caption).foregroundStyle(Theme.muted)
                    let current = options.first { $0.value == selection }
                    Text(current?.title ?? placeholder)
                        .font(.body.weight(.medium))
                        .foregroundStyle(current == nil ? Theme.subtle : Theme.ink)
                        .lineLimit(1)
                    if let subtitle = current?.subtitle {
                        Text(subtitle).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                    }
                }
                Spacer()
                Image(systemName: "chevron.up.chevron.down").font(.footnote.weight(.semibold)).foregroundStyle(Theme.subtle)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
    }
}

/// Free times as a grid of buttons; the current time of a moved appointment is dashed.
struct SlotGrid: View {
    let slots: [Slot]
    @Binding var selected: String?
    var current: String?
    var loading = false

    var body: some View {
        if loading {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 74), spacing: 8)], spacing: 8) {
                ForEach(0..<8, id: \.self) { _ in SkeletonBlock(height: 42, radius: 12) }
            }
            .skeleton()
        } else if slots.isEmpty {
            HStack(spacing: 10) {
                Image(systemName: "calendar.badge.exclamationmark").foregroundStyle(Theme.warning)
                Text(L.Booking.noFreeTimes).font(.subheadline).foregroundStyle(Theme.muted)
            }
            .padding(.vertical, 6)
        } else {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 74), spacing: 8)], spacing: 8) {
                ForEach(slots, id: \.time) { slot in
                    let on = slot.time == selected
                    Button {
                        Haptics.selection()
                        withAnimation(.snappy(duration: 0.2)) { selected = slot.time }
                    } label: {
                        Text(slot.time)
                            .font(.callout.weight(.semibold))
                            .monospacedDigit()
                            .frame(maxWidth: .infinity, minHeight: 42)
                            .foregroundStyle(on ? Theme.onBrand : Theme.ink)
                            .background {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(on ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.fill))
                            }
                            .overlay {
                                if slot.time == current && !on {
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .strokeBorder(Theme.brand, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                                }
                            }
                    }
                    .buttonStyle(PressableStyle())
                    .accessibilityAddTraits(on ? .isSelected : [])
                    .accessibilityHint(slot.time == current ? L.Booking.currentTime : "")
                }
            }
        }
    }
}

/// Days to pick from, today onwards, with a calendar for anything further.
struct DayScroller: View {
    @Binding var selected: LocalDate
    let today: LocalDate
    let clock: BusinessClock
    @State private var picking = false

    var body: some View {
        HStack(spacing: 8) {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(0..<60, id: \.self) { offset in
                            let day = today.adding(days: offset)
                            let on = day == selected
                            Button {
                                Haptics.selection()
                                withAnimation(.snappy) { selected = day }
                            } label: {
                                VStack(spacing: 3) {
                                    Text(Fmt.weekdayShort(day).prefix(3).uppercased())
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(on ? Theme.onBrand.opacity(0.85) : Theme.muted)
                                    Text("\(day.day)")
                                        .font(.body.weight(.bold)).fontDesign(.rounded)
                                        .foregroundStyle(on ? Theme.onBrand : Theme.ink)
                                    Text(Fmt.monthShort(day).prefix(3).lowercased())
                                        .font(.caption2)
                                        .foregroundStyle(on ? Theme.onBrand.opacity(0.85) : Theme.subtle)
                                }
                                .frame(width: 50, height: 66)
                                .background {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(on ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.fill))
                                }
                            }
                            .buttonStyle(.plain)
                            .id(day)
                            .accessibilityLabel(Fmt.dayLong(day))
                            .accessibilityAddTraits(on ? .isSelected : [])
                        }
                    }
                }
                .onAppear { proxy.scrollTo(selected, anchor: .center) }
                .onChange(of: selected) { _, day in withAnimation { proxy.scrollTo(day, anchor: .center) } }
            }
            Button {
                picking = true
            } label: {
                Image(systemName: "calendar")
                    .font(.title3)
                    .foregroundStyle(Theme.brand)
                    .frame(width: 44, height: 66)
                    .background(Theme.brandSoft, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .accessibilityLabel(L.Agenda.pickDate)
        }
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        .sheet(isPresented: $picking) { DatePickerSheet(selected: $selected, clock: clock, minimum: today) }
    }
}

/// Looks up the free times whenever what they depend on changes.
@MainActor
@Observable
final class SlotLoader {
    var slots: [Slot] = []
    var loading = false
    var error: Error?

    struct Key: Hashable {
        let serviceId: String?
        let date: LocalDate
        let locationId: String?
        let appointmentId: String?
    }

    func load(_ key: Key, workspace: Workspace) async {
        guard let serviceId = key.serviceId else {
            slots = []
            return
        }
        loading = true
        defer { loading = false }
        do {
            let found: [Slot]
            if let appointmentId = key.appointmentId {
                found = try await workspace.api.rescheduleSlots(workspace.id, appointmentId: appointmentId, serviceId: serviceId,
                                                                date: key.date, locationId: key.locationId)
            } else {
                found = try await workspace.api.slots(workspace.id, serviceId: serviceId, date: key.date, locationId: key.locationId)
            }
            try Task.checkCancellation()
            slots = found
            error = nil
        } catch is CancellationError {
        } catch {
            slots = []
            self.error = error
        }
    }
}

extension Service {
    /// "45 min · $ 35.000" for pickers.
    func summary(_ business: Business) -> String {
        "\(WallTime.duration(durationMin)) · \(business.price(priceAmount))"
    }
}
