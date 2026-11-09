import SwiftUI

/// Opening hours per location, public holidays, closures and the online booking rules.
struct HoursView: View {
    @Environment(Workspace.self) private var workspace
    @State private var locationId: String?
    @State private var hours: HoursResponse?
    @State private var holidays: [Holiday] = []
    @State private var closures: [Closure] = []
    @State private var error: Error?
    @State private var editing = false

    private var location: Location? { workspace.location(locationId) ?? workspace.locations.first }

    var body: some View {
        List {
            if workspace.locations.count > 1 {
                Section {
                    Picker(L.Agenda.location, selection: Binding(get: { location?.id }, set: { locationId = $0 })) {
                        ForEach(workspace.locations) { Text($0.name).tag(Optional($0.id)) }
                    }
                }
            }
            Section {
                if let hours {
                    WeeklyHoursList(ranges: hours.business)
                } else if let error {
                    FieldError(text: ErrorText.message(error))
                } else {
                    ForEach(0..<7, id: \.self) { _ in SkeletonBlock(height: 14).skeleton() }
                }
            } header: {
                Text(L.Hours.title)
            } footer: {
                if !workspace.isAdmin { Text(L.Common.readOnly) }
            }

            Section(L.Hours.bookingRules) {
                if let interval = workspace.business.slotIntervalMin {
                    LabeledContent(L.Hours.slotInterval, value: WallTime.duration(interval))
                }
                if let notice = workspace.business.minNoticeMin {
                    LabeledContent(L.Hours.minNotice, value: notice >= 1440 ? L.Hours.days(notice / 1440) : WallTime.duration(notice))
                }
                if let window = workspace.business.maxAdvanceDays {
                    LabeledContent(L.Hours.maxAdvance, value: L.Hours.days(window))
                }
            }

            if workspace.isAdmin {
                Section(L.Hours.closures) {
                    if closures.isEmpty {
                        Text(L.Hours.noClosures).foregroundStyle(Theme.muted)
                    } else {
                        ForEach(closures) { closure in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(closureDates(closure)).font(.subheadline.weight(.medium))
                                let who = [closure.resourceId.flatMap { workspace.professional($0)?.name },
                                           closure.locationId.flatMap { workspace.location($0)?.name }, closure.note].compactMap { $0 }
                                if !who.isEmpty { Text(who.joined(separator: " · ")).font(.footnote).foregroundStyle(Theme.muted) }
                            }
                        }
                    }
                }
                if !holidays.isEmpty {
                    Section(L.Hours.holidays) {
                        ForEach(holidays.prefix(6), id: \.date) { holiday in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(holiday.name).font(.subheadline.weight(.medium))
                                    if let date = LocalDate(holiday.date) {
                                        Text(Fmt.dayLong(date)).font(.footnote).foregroundStyle(Theme.muted)
                                    }
                                }
                                Spacer()
                                Chip(text: holiday.closed ? L.Hours.closedHoliday : L.Hours.openHoliday, tone: holiday.closed ? .neutral : .success)
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(L.Hours.title)
        .toolbar {
            if workspace.isAdmin && hours != nil {
                ToolbarItem(placement: .topBarTrailing) { Button(L.Common.edit) { editing = true } }
            }
        }
        .refreshable { await load() }
        .task(id: location?.id) { await load() }
        .sheet(isPresented: $editing) {
            if let hours {
                HoursEditor(locationId: location?.id ?? hours.locationId, initial: hours.business) { await load() }
            }
        }
    }

    private func closureDates(_ closure: Closure) -> String {
        guard let start = LocalDate(closure.date) else { return closure.date }
        if let end = closure.endDate.flatMap(LocalDate.init), end != start {
            return "\(Fmt.dayShort(start)) – \(Fmt.dayShort(end))"
        }
        return Fmt.dayLong(start)
    }

    private func load() async {
        await workspace.loadCatalog()
        do {
            hours = try await workspace.api.hours(workspace.id, locationId: location?.id)
            error = nil
        } catch {
            self.error = error
        }
        if workspace.isAdmin {
            async let h = try? workspace.api.holidays(workspace.id, language: AppLocale.languageCode)
            async let c = try? workspace.api.closures(workspace.id)
            let (foundHolidays, foundClosures) = await (h, c)
            holidays = foundHolidays?.holidays ?? []
            closures = foundClosures ?? []
        }
    }
}

/// Editing a location's week: each day open or closed, with one or more ranges.
struct HoursEditor: View {
    let locationId: String?
    let initial: [HoursRange]
    let onSaved: () async -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss
    @State private var ranges: [Editable] = []
    @State private var busy = false
    @State private var failure: String?

    struct Editable: Identifiable, Equatable {
        let id = UUID()
        var weekday: Int
        var start: Int
        var end: Int
    }

    var body: some View {
        NavigationStack {
            Form {
                ForEach([1, 2, 3, 4, 5, 6, 0], id: \.self) { weekday in
                    daySection(weekday)
                }
                if let failure {
                    Section { FieldError(text: failure) }
                }
            }
            .navigationTitle(L.Professionals.weeklyHours)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ProgressView() } else {
                        Button(L.Common.save) { Task { await save() } }.fontWeight(.semibold)
                    }
                }
            }
            .onAppear {
                if ranges.isEmpty {
                    ranges = initial.compactMap { range in
                        guard let start = WallTime.minutes(range.start), let end = WallTime.minutes(range.end) else { return nil }
                        return Editable(weekday: range.weekday, start: start, end: end)
                    }
                }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    private func daySection(_ weekday: Int) -> some View {
        let day = ranges.filter { $0.weekday == weekday }.sorted { $0.start < $1.start }
        return Section {
            Toggle(isOn: Binding(
                get: { !day.isEmpty },
                set: { open in
                    withAnimation {
                        if open {
                            ranges.append(Editable(weekday: weekday, start: 9 * 60, end: 18 * 60))
                        } else {
                            ranges.removeAll { $0.weekday == weekday }
                        }
                    }
                }
            )) {
                Text(Fmt.weekdayName(weekday)).font(.body.weight(.semibold))
            }
            .tint(Theme.brand)
            ForEach(day) { range in
                HStack {
                    TimeMenu(minutes: binding(range.id, \.start))
                    Text("–").foregroundStyle(Theme.muted)
                    TimeMenu(minutes: binding(range.id, \.end))
                    Spacer()
                    if day.count > 1 {
                        Button {
                            withAnimation { ranges.removeAll { $0.id == range.id } }
                        } label: {
                            Image(systemName: "minus.circle.fill").foregroundStyle(Theme.danger)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(L.Common.remove)
                    }
                }
            }
            if !day.isEmpty {
                Button {
                    let last = day.last?.end ?? 9 * 60
                    withAnimation {
                        ranges.append(Editable(weekday: weekday, start: min(last + 60, 22 * 60), end: min(last + 180, 23 * 60 + 45)))
                    }
                } label: {
                    Label(L.Hours.addRange, systemImage: "plus.circle").font(.subheadline)
                }
            }
        }
    }

    private func binding(_ id: UUID, _ key: WritableKeyPath<Editable, Int>) -> Binding<Int> {
        Binding(
            get: { ranges.first { $0.id == id }?[keyPath: key] ?? 0 },
            set: { value in
                if let index = ranges.firstIndex(where: { $0.id == id }) { ranges[index][keyPath: key] = value }
            }
        )
    }

    private func validate() -> String? {
        for weekday in 0...6 {
            let day = ranges.filter { $0.weekday == weekday }.sorted { $0.start < $1.start }
            if day.contains(where: { $0.end <= $0.start }) { return L.Hours.endBeforeStart }
            for (a, b) in zip(day, day.dropFirst()) where b.start < a.end { return L.Hours.overlap }
        }
        return nil
    }

    private func save() async {
        if let problem = validate() {
            failure = problem
            Haptics.warning()
            return
        }
        busy = true
        failure = nil
        defer { busy = false }
        let list = ranges.sorted { ($0.weekday, $0.start) < ($1.weekday, $1.start) }
            .map { HoursRange(weekday: $0.weekday, start: WallTime.string($0.start), end: WallTime.string($0.end)) }
        do {
            try await workspace.api.saveHours(workspace.id, locationId: locationId, resourceId: nil, hours: list)
            toasts.show(L.Hours.saved)
            await onSaved()
            dismiss()
        } catch {
            failure = ErrorText.message(error)
            Haptics.error()
        }
    }
}

/// A time of day in 15-minute steps.
struct TimeMenu: View {
    @Binding var minutes: Int

    var body: some View {
        Menu {
            Picker("", selection: $minutes) {
                ForEach(Array(stride(from: 0, through: 24 * 60 - 15, by: 15)), id: \.self) { value in
                    Text(WallTime.string(value)).tag(value)
                }
            }
        } label: {
            Text(WallTime.string(minutes))
                .font(.body.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(Theme.ink)
                .padding(.horizontal, 12)
                .frame(minHeight: 34)
                .background(Theme.fill, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
    }
}
