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

/// Days to pick from: a calendar for anything further first, then 60 days from
/// today labelled «Hoy», «Mañana», «Mié 11 nov».
struct DayScroller: View {
    @Binding var selected: LocalDate
    let today: LocalDate
    let clock: BusinessClock
    @State private var picking = false

    var body: some View {
        HStack(spacing: 8) {
            Button {
                picking = true
            } label: {
                Image(systemName: "calendar")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Theme.brand)
                    .frame(width: 44, height: 38)
                    .background(Theme.brandSoft, in: Capsule())
            }
            .accessibilityLabel(L.Agenda.pickDate)
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
                                Text(Fmt.relativeName(day, today: today) ?? Fmt.dayMedium(day))
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(on ? Theme.onBrand : Theme.ink)
                                    .padding(.horizontal, 14)
                                    .frame(height: 38)
                                    .background {
                                        Capsule().fill(on ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.fill))
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
        }
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        .sheet(isPresented: $picking) { DatePickerSheet(selected: $selected, clock: clock, minimum: today) }
    }
}

/// One numbered step of a booking form; the number turns into a check once the
/// step has what it needs.
struct StepBlock<Content: View>: View {
    let number: Int
    let title: String
    var trailing: String?
    let done: Bool
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                ZStack {
                    Circle().fill(done ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Theme.fill))
                    if done {
                        Image(systemName: "checkmark").font(.caption.weight(.bold)).foregroundStyle(Theme.onBrand)
                    } else {
                        Text("\(number)").font(.caption.weight(.bold)).foregroundStyle(Theme.muted)
                    }
                }
                .frame(width: 24, height: 24)
                Text(title).font(.headline).foregroundStyle(Theme.ink)
                Spacer(minLength: 8)
                if let trailing {
                    Text(trailing).font(.footnote).foregroundStyle(Theme.muted).lineLimit(1)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 0) { content }
                .card(padding: 14)
        }
    }
}

/// The service a booking is for: tap to choose it from the catalog by category.
struct ServiceField: View {
    let services: [Service]
    @Binding var selection: String?
    @Environment(Workspace.self) private var workspace
    @State private var picking = false

    var body: some View {
        let current = services.first { $0.id == selection } ?? workspace.service(selection)
        Button {
            picking = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "scissors").foregroundStyle(Theme.brand).frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(L.Booking.service).font(.caption).foregroundStyle(Theme.muted)
                    Text(current?.name ?? L.Booking.pickService)
                        .font(.body.weight(.medium))
                        .foregroundStyle(current == nil ? Theme.subtle : Theme.ink)
                        .lineLimit(1)
                    if let current {
                        Text(current.summary(workspace.business)).font(.footnote).foregroundStyle(Theme.muted)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Theme.subtle)
            }
            .contentShape(Rectangle())
            .padding(.vertical, 4)
        }
        .buttonStyle(.plain)
        .sheet(isPresented: $picking) {
            ServicePicker(title: L.Checkout.chooseService, services: services, selected: selection) { service in
                selection = service.id
            }
        }
    }
}

/// The catalog grouped by category: name, duration and price.
struct ServicePicker: View {
    let title: String
    let services: [Service]
    var selected: String?
    let pick: (Service) -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(BookingRules.grouped(services), id: \.category) { group in
                    Section {
                        ForEach(group.services) { service in
                            Button {
                                Haptics.selection()
                                pick(service)
                                dismiss()
                            } label: {
                                HStack(spacing: 12) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(service.name).foregroundStyle(Theme.ink)
                                        Text(WallTime.duration(service.durationMin)).font(.footnote).foregroundStyle(Theme.muted)
                                    }
                                    Spacer()
                                    Text(workspace.business.price(service.priceAmount))
                                        .font(.subheadline.weight(.semibold)).monospacedDigit()
                                        .foregroundStyle(service.priceAmount == nil ? Theme.muted : Theme.ink)
                                    if service.id == selected {
                                        Image(systemName: "checkmark").foregroundStyle(Theme.brand)
                                    }
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        if let category = group.category { Text(category) }
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Free times by part of the day, or why there are none.
struct GroupedSlots: View {
    let serviceChosen: Bool
    let slots: [Slot]
    @Binding var selected: String?
    var current: String?
    var loading = false
    var error: Error?

    var body: some View {
        if !serviceChosen {
            Text(L.Booking.pickServiceFirst).font(.subheadline).foregroundStyle(Theme.subtle)
        } else if let error, slots.isEmpty, !loading {
            Text(ErrorText.message(error)).font(.subheadline).foregroundStyle(Theme.danger)
        } else if loading || slots.isEmpty {
            SlotGrid(slots: slots, selected: $selected, current: current, loading: loading)
        } else {
            VStack(alignment: .leading, spacing: 14) {
                ForEach(BookingRules.timesBySegment(slots), id: \.segment) { group in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(L.Agenda.segment(group.segment))
                            .font(.footnote.weight(.semibold)).foregroundStyle(Theme.muted)
                            .textCase(.uppercase).tracking(0.4)
                        SlotGrid(slots: group.slots, selected: $selected, current: current)
                    }
                }
            }
        }
    }
}

/// The bar under a booking form: the first thing missing or the server's error
/// in red, else a summary of the choice; then the main button.
struct BookingBar: View {
    let message: String?
    let summary: String?
    let title: String
    var symbol: String?
    var busy = false
    var disabled = false
    let action: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            VStack(spacing: 8) {
                if let message {
                    Label(message, systemImage: "exclamationmark.circle.fill")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Theme.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                } else if let summary {
                    Text(summary)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                PrimaryButton(title: title, symbol: symbol, busy: busy, disabled: disabled, action: action)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(.bar)
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
