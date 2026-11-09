import SwiftUI

/// The calendar's state: which week is loaded, which day is picked, the filters.
///
/// A whole week is fetched at once, so moving between its days is instant and
/// the strip can show which days have appointments.
@MainActor
@Observable
final class AgendaModel {
    var selected: LocalDate
    var filter = AgendaRules.Filter()
    private(set) var weeks: [LocalDate: [Appointment]] = [:]
    private(set) var loadingWeek: LocalDate?
    var error: Error?

    init(today: LocalDate) {
        selected = today
    }

    var weekStart: LocalDate { selected.startOfWeek(firstWeekday: 1) }

    var isLoading: Bool { loadingWeek == weekStart && weeks[weekStart] == nil }

    func load(_ workspace: Workspace, force: Bool = false) async {
        let week = weekStart
        if !force, weeks[week] != nil { return }
        loadingWeek = week
        defer { if loadingWeek == week { loadingWeek = nil } }
        do {
            let range = workspace.clock.range(week, through: week.adding(days: 6))
            let list = try await workspace.api.appointments(workspace.id, from: range.from, to: range.to, includeCancelled: true)
            weeks[week] = list
            error = nil
        } catch is CancellationError {
        } catch {
            if weeks[week] == nil { self.error = error }
        }
    }

    func invalidate() { weeks = [:] }

    /// Every appointment of a day, before the filters.
    func all(on day: LocalDate, clock: BusinessClock) -> [Appointment] {
        (weeks[day.startOfWeek(firstWeekday: 1)] ?? []).filter { clock.localDate(of: $0.startsAt) == day }
    }

    func visible(on day: LocalDate, clock: BusinessClock) -> [Appointment] {
        AgendaRules.apply(filter, to: all(on: day, clock: clock))
    }

    /// Days of the loaded week with something booked (after the filters).
    func busyDays(clock: BusinessClock) -> Set<LocalDate> {
        AgendaRules.busyDays(weeks[weekStart] ?? [], filter: filter, clock: clock)
    }

    func clearFilters() {
        filter.professionalIds = []
        filter.locationId = nil
        filter.showCancelled = false
    }
}

struct AgendaView: View {
    @Environment(AppModel.self) private var app
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: AgendaModel?
    @State private var picking = false
    @State private var filtering = false
    @State private var booking = false
    @State private var cancelling: Appointment?
    @State private var markingNoShow: Appointment?
    @State private var charging: Appointment?
    @State private var rescheduling: Appointment?
    @State private var now = Date()

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                Color.clear
            }
        }
        .background(Theme.canvas)
        .onAppear {
            if model == nil { model = AgendaModel(today: workspace.clock.today()) }
            // Also on coming back from a detail or another tab.
            Task { await workspace.refreshNotifications() }
        }
    }

    @ViewBuilder
    private func content(_ model: AgendaModel) -> some View {
        let clock = workspace.clock
        let today = clock.today(now: now)
        let day = model.selected
        let items = model.visible(on: day, clock: clock)
        let segments = AgendaRules.segments(items, clock: clock)
        let line = day == today ? AgendaRules.nowLine(segments, now: now) : nil
        List {
            DayHeader(day: day, today: today, items: items, now: now, showMoney: workspace.isAdmin)
                .plainRow(top: 4, bottom: 4)
            if model.isLoading {
                CardsSkeleton(count: 3).plainRow()
            } else if let error = model.error, model.all(on: day, clock: clock).isEmpty {
                ErrorState(message: ErrorText.message(error, fallback: L.Agenda.loadFailed)) { Task { await model.load(workspace, force: true) } }
                    .plainRow()
            } else if items.isEmpty {
                emptyState(model, today: today)
                    .plainRow()
            } else {
                if line == .top { NowLine(time: clock.time(of: now)).plainRow(top: 6, bottom: 0) }
                ForEach(segments, id: \.segment) { group in
                    // Plain rows rather than sticky section headers, which would float over the cards.
                    // Counted like the day's summary: no-shows and cancelled ones are listed, not counted.
                    SegmentHeader(segment: group.segment, count: AgendaRules.count(group.items))
                        .plainRow(top: 10, bottom: 2)
                    ForEach(group.items) { appointment in
                        AppointmentCard(appointment: appointment, now: now)
                            .background {
                                // A hidden link keeps the card free of the list's disclosure chevron.
                                NavigationLink(value: Route.appointment(appointment)) { EmptyView() }.opacity(0)
                            }
                            .plainRow(top: 5, bottom: 5)
                            .swipeActions(edge: .leading, allowsFullSwipe: true) { leadingActions(appointment) }
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) { trailingActions(appointment) }
                            .contextMenu { menu(appointment) }
                        if line == .after(appointment.id) {
                            NowLine(time: clock.time(of: now)).plainRow(top: 2, bottom: 2)
                        }
                    }
                }
            }
            Color.clear.frame(height: 80).plainRow()
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .safeAreaInset(edge: .top, spacing: 0) { header(model, today: today) }
        .overlay(alignment: .bottomTrailing) { fab(model, today: today) }
        .refreshable {
            await workspace.loadCatalog(force: true)
            await model.load(workspace, force: true)
            await workspace.refreshNotifications()
        }
        // The tab says "Agenda"; the bar carries the business instead. The title
        // stays for the back button of the screens pushed from here.
        .navigationTitle(L.Agenda.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { Color.clear.frame(width: 1, height: 1).accessibilityHidden(true) }
            ToolbarItem(placement: .topBarLeading) { BusinessSwitcherButton() }
            ToolbarItemGroup(placement: .topBarTrailing) {
                if model.selected != today {
                    Button(L.Common.today) {
                        withAnimation(.snappy) { model.selected = today }
                        Haptics.selection()
                    }
                    .fontWeight(.semibold)
                }
                Button {
                    picking = true
                } label: {
                    Image(systemName: "calendar")
                }
                .accessibilityLabel(L.Agenda.pickDate)
                // A professional only has something to filter with several locations.
                if workspace.isAdmin || workspace.activeLocations.count > 1 {
                    Button {
                        filtering = true
                    } label: {
                        Image(systemName: model.filter.active ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    }
                    .accessibilityLabel(L.Agenda.filters)
                }
                if workspace.hasNotifications {
                    NotificationBell()
                }
            }
        }
        .task(id: model.weekStart) {
            await model.load(workspace)
            // The one place notification permission is asked: once the agenda is up.
            if model.error == nil { await app.push.prepare() }
        }
        .task(id: workspace.revision) {
            guard workspace.revision > 0 else { return }
            model.invalidate()
            await model.load(workspace, force: true)
        }
        .task {
            // Keep "now" fresh so started and ongoing appointments read right.
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                now = Date()
            }
        }
        .onChange(of: model.filter.locationId) { _, id in workspace.agendaLocation = id }
        .onChange(of: workspace.agendaDay) { _, day in
            guard let day else { return }
            withAnimation(.snappy) { model.selected = day }
            workspace.agendaDay = nil
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            now = Date()
            Task { await model.load(workspace, force: true) }
        }
        .sheet(isPresented: $picking) { DatePickerSheet(selected: Binding(get: { model.selected }, set: { model.selected = $0 }), clock: workspace.clock) }
        .sheet(isPresented: $filtering) { AgendaFilterSheet(filter: Binding(get: { model.filter }, set: { model.filter = $0 })) }
        .sheet(isPresented: $booking) {
            BookingView(date: max(model.selected, today)) { day in
                withAnimation(.snappy) { model.selected = day }
            }
        }
        .sheet(item: $charging) { CheckoutView(appointment: $0) }
        .sheet(item: $rescheduling) { appointment in
            RescheduleView(appointment: appointment) { startsAt in
                withAnimation(.snappy) { model.selected = workspace.clock.localDate(of: startsAt) }
            }
        }
        .alert(L.Appointment.cancelTitle, isPresented: Binding(get: { cancelling != nil }, set: { if !$0 { cancelling = nil } }), presenting: cancelling) { appointment in
            Button(L.Appointment.cancel, role: .destructive) { Task { await cancel(appointment) } }
            Button(L.Appointment.keep, role: .cancel) {}
        } message: { _ in
            Text(L.Appointment.cancelBody)
        }
        .alert(L.Appointment.noShowTitle, isPresented: Binding(get: { markingNoShow != nil }, set: { if !$0 { markingNoShow = nil } }), presenting: markingNoShow) { appointment in
            Button(L.Appointment.markNoShow, role: .destructive) { Task { await markNoShow(appointment) } }
            Button(L.Common.goBack, role: .cancel) {}
        } message: { _ in
            Text(L.Appointment.noShowBody)
        }
    }

    // MARK: Header

    private func header(_ model: AgendaModel, today: LocalDate) -> some View {
        VStack(spacing: 10) {
            WeekStrip(selected: Binding(get: { model.selected }, set: { model.selected = $0 }),
                      today: today, busy: model.busyDays(clock: workspace.clock))
            if model.filter.active {
                ActiveFilters(filter: model.filter) {
                    filtering = true
                } clear: {
                    withAnimation(.snappy) { model.clearFilters() }
                }
                .transition(.opacity)
            }
        }
        .padding(.top, 6)
        .padding(.bottom, 10)
        // Compact controls stay usable at the largest text sizes.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
        .background(.bar)
        .overlay(alignment: .bottom) { Rectangle().fill(Theme.line).frame(height: 0.5) }
    }

    private func emptyState(_ model: AgendaModel, today: LocalDate) -> some View {
        Group {
            if AgendaRules.hiddenByFilters(model.filter, all: model.all(on: model.selected, clock: workspace.clock)) {
                EmptyState(symbol: "line.3.horizontal.decrease.circle", title: L.Agenda.emptyFiltered,
                           actionTitle: L.Agenda.clearFilters) {
                    withAnimation(.snappy) { model.clearFilters() }
                }
            } else {
                EmptyState(symbol: "sun.max", title: L.Agenda.emptyTitle,
                           actionTitle: model.selected >= today ? L.Agenda.newAppointment : nil) { booking = true }
            }
        }
        .card()
        .padding(.top, 8)
    }

    private func fab(_ model: AgendaModel, today: LocalDate) -> some View {
        Button {
            Haptics.tap()
            booking = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(Theme.onBrand)
                .frame(width: 60, height: 60)
                .background(Theme.brand.gradient, in: Circle())
                .shadow(color: Theme.brand.opacity(0.4), radius: 12, y: 6)
        }
        .buttonStyle(PressableStyle())
        .padding(.trailing, 20)
        .padding(.bottom, 16)
        .accessibilityLabel(L.Agenda.newAppointment)
    }

    // MARK: Row actions

    private func chargeLabel(_ appointment: Appointment) -> String {
        AppointmentRules.chargeAmount(appointment).map { L.Checkout.chargeAmount(workspace.money($0)) } ?? L.Checkout.charge
    }

    /// Swipe accelerators: the same actions as the long-press menu.
    @ViewBuilder
    private func leadingActions(_ appointment: Appointment) -> some View {
        let actions = AppointmentRules.actions(for: appointment, isAdmin: workspace.isAdmin)
        if actions.contains(.charge) || actions.contains(.closeSale) {
            Button {
                charging = appointment
            } label: {
                Label(actions.contains(.closeSale) ? L.Checkout.closeSale : L.Checkout.charge, systemImage: "banknote")
            }
            .tint(Theme.brand)
        }
    }

    @ViewBuilder
    private func trailingActions(_ appointment: Appointment) -> some View {
        let actions = AppointmentRules.actions(for: appointment, isAdmin: workspace.isAdmin)
        if actions.contains(.cancel) {
            Button(role: .destructive) {
                cancelling = appointment
            } label: {
                Label(L.Common.cancel, systemImage: "xmark")
            }
            .tint(Theme.danger)
        }
        if actions.contains(.reschedule) {
            Button {
                rescheduling = appointment
            } label: {
                Label(L.Common.edit, systemImage: "calendar.badge.clock")
            }
            .tint(Color(hex: "#0284c7"))
        }
    }

    /// «Cobrar $ X» / «Cerrar venta» / «Ver venta», «Editar cita», «Llamar»,
    /// «WhatsApp», «Marcar «No asistió»», «Cancelar cita».
    @ViewBuilder
    private func menu(_ appointment: Appointment) -> some View {
        let parts = AppointmentRules.menu(AppointmentRules.actions(for: appointment, isAdmin: workspace.isAdmin))
        ForEach(parts.before, id: \.self) { action in
            switch action {
            case .charge:
                Button { charging = appointment } label: { Label(chargeLabel(appointment), systemImage: "banknote") }
            case .closeSale:
                Button { charging = appointment } label: { Label(L.Checkout.closeSale, systemImage: "banknote") }
            case .viewSale:
                Button {
                    if let sale = appointment.sale { workspace.agendaPath.append(.saleId(sale.id)) }
                } label: { Label(L.Checkout.viewSale, systemImage: "doc.text") }
            case .reschedule:
                Button { rescheduling = appointment } label: { Label(L.Appointment.reschedule, systemImage: "calendar.badge.clock") }
            default:
                EmptyView()
            }
        }
        if let url = Phone.callURL(appointment.customer.phone) {
            Link(destination: url) { Label(L.Common.call, systemImage: "phone") }
        }
        if let url = Phone.whatsAppURL(appointment.customer.phone) {
            Link(destination: url) { Label(L.Common.whatsapp, systemImage: "message") }
        }
        ForEach(parts.after, id: \.self) { action in
            if action == .markNoShow {
                Button { markingNoShow = appointment } label: { Label(L.Appointment.markNoShow, systemImage: "person.fill.xmark") }
            } else {
                Button(role: .destructive) { cancelling = appointment } label: { Label(L.Appointment.cancel, systemImage: "xmark.circle") }
            }
        }
    }

    private func cancel(_ appointment: Appointment) async {
        do {
            _ = try await workspace.api.cancel(workspace.id, appointment.id)
            toasts.show(L.Appointment.cancelled)
            workspace.changed()
        } catch {
            toasts.error(error)
        }
    }

    private func markNoShow(_ appointment: Appointment) async {
        do {
            _ = try await workspace.api.markNoShow(workspace.id, appointment.id)
            toasts.show(L.Appointment.noShowMarked)
            workspace.changed()
        } catch {
            toasts.error(error)
        }
    }
}

// MARK: - Pieces

extension View {
    /// A list row without separator or background, with card spacing.
    func plainRow(top: CGFloat = 6, bottom: CGFloat = 6) -> some View {
        listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: top, leading: 16, bottom: bottom, trailing: 16))
    }
}

/// The picked day in words, with its count, expected takings and what is left to charge.
private struct DayHeader: View {
    let day: LocalDate
    let today: LocalDate
    let items: [Appointment]
    let now: Date
    let showMoney: Bool
    @Environment(Workspace.self) private var workspace

    var body: some View {
        let expected = AgendaRules.expected(items)
        let toCharge = AgendaRules.toCharge(items, now: now)
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(Fmt.dayLong(day, thisYear: today.year))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(Theme.ink)
                if let relative = Fmt.relativeName(day, today: today) { Chip(text: relative, tone: .brand) }
            }
            HStack(spacing: 6) {
                Text(L.Agenda.count(AgendaRules.count(items)))
                if showMoney, expected > 0 {
                    Text("·")
                    Text(L.Agenda.expected(workspace.money(expected)))
                }
                if showMoney, toCharge > 0 {
                    Chip(text: L.Agenda.toCharge(toCharge), tone: .warning)
                        .padding(.leading, 2)
                }
            }
            .font(.subheadline)
            .foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 8)
        .accessibilityElement(children: .combine)
    }
}

private struct SegmentHeader: View {
    let segment: AgendaRules.Segment
    let count: Int

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.caption.weight(.bold)).foregroundStyle(Theme.brand)
            Text(count > 0 ? "\(L.Agenda.segment(segment)) · \(count)" : L.Agenda.segment(segment))
                .font(.footnote.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase).tracking(0.4)
            Spacer()
        }
        .padding(.horizontal, 4)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var symbol: String {
        switch segment {
        case .morning: return "sunrise.fill"
        case .afternoon: return "sun.max.fill"
        case .evening: return "moon.stars.fill"
        }
    }
}

/// The red line at the current time, with the time in a pill.
private struct NowLine: View {
    let time: String

    var body: some View {
        HStack(spacing: 0) {
            Text(time)
                .font(.caption2.weight(.bold))
                .monospacedDigit()
                .foregroundStyle(.white)
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Theme.danger, in: Capsule())
            Rectangle().fill(Theme.danger).frame(height: 1.5)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(L.Agenda.now), \(time)")
    }
}

/// Seven days to tap, with a dot under the busy ones; swipe for other weeks.
struct WeekStrip: View {
    @Binding var selected: LocalDate
    let today: LocalDate
    let busy: Set<LocalDate>

    var body: some View {
        let start = selected.startOfWeek(firstWeekday: 1)
        VStack(spacing: 8) {
            HStack {
                Text(Fmt.monthYear(start.adding(days: 3)))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.ink)
                Spacer()
                HStack(spacing: 18) {
                    Button { move(-7) } label: { Image(systemName: "chevron.left") }
                        .accessibilityLabel(L.Agenda.previousWeek)
                    Button { move(7) } label: { Image(systemName: "chevron.right") }
                        .accessibilityLabel(L.Agenda.nextWeek)
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.brand)
            }
            .padding(.horizontal, 20)
            HStack(spacing: 4) {
                ForEach(0..<7, id: \.self) { offset in
                    let day = start.adding(days: offset)
                    DayPill(day: day, selected: day == selected, today: day == today, busy: busy.contains(day))
                        .onTapGesture {
                            Haptics.selection()
                            withAnimation(.snappy) { selected = day }
                        }
                }
            }
            .padding(.horizontal, 12)
            .gesture(
                DragGesture(minimumDistance: 24).onEnded { value in
                    if value.translation.width < -40 { move(7) } else if value.translation.width > 40 { move(-7) }
                }
            )
        }
    }

    private func move(_ days: Int) {
        Haptics.selection()
        withAnimation(.snappy) { selected = selected.adding(days: days) }
    }
}

private struct DayPill: View {
    let day: LocalDate
    let selected: Bool
    let today: Bool
    let busy: Bool

    var body: some View {
        VStack(spacing: 4) {
            Text(Fmt.weekdayShort(day))
                .font(.caption2.weight(.semibold))
                .foregroundStyle(selected ? Theme.onBrand.opacity(0.85) : Theme.muted)
            Text("\(day.day)")
                .font(.body.weight(.bold))
                .fontDesign(.rounded)
                .foregroundStyle(selected ? Theme.onBrand : today ? Theme.brand : Theme.ink)
            Circle()
                .fill(selected ? Theme.onBrand : Theme.brand)
                .frame(width: 5, height: 5)
                .opacity(busy ? 1 : 0)
        }
        .frame(maxWidth: .infinity, minHeight: 64)
        .background {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(selected ? AnyShapeStyle(Theme.brand.gradient) : AnyShapeStyle(Color.clear))
        }
        .overlay {
            if today && !selected {
                RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Theme.brand.opacity(0.5), lineWidth: 1.5)
            }
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Fmt.dayLong(day))
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// What the agenda is narrowed to: one tap opens the filters, the cross clears them.
private struct ActiveFilters: View {
    let filter: AgendaRules.Filter
    let open: () -> Void
    let clear: () -> Void
    @Environment(Workspace.self) private var workspace

    var body: some View {
        HStack(spacing: 0) {
            Button(action: open) {
                HStack(spacing: 8) {
                    if let only = people.first, people.count == 1 {
                        Avatar(name: only.name, colorHex: workspace.colorHex(for: only.id), size: 22,
                               photoURL: workspace.absoluteURL(only.photoUrl))
                    } else {
                        Image(systemName: "line.3.horizontal.decrease").font(.footnote.weight(.semibold))
                    }
                    Text(summary).font(.subheadline.weight(.medium)).lineLimit(1)
                }
                .padding(.leading, people.count == 1 ? 5 : 12)
            }
            .accessibilityLabel("\(L.Agenda.filters): \(summary)")
            Button(action: clear) {
                Image(systemName: "xmark")
                    .font(.caption.weight(.bold))
                    .frame(width: 32, height: 32)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(L.Agenda.clearFilters)
        }
        .frame(minHeight: 32)
        .foregroundStyle(Theme.brand)
        .background(Theme.brand.opacity(0.12), in: Capsule())
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
    }

    private var people: [Professional] {
        workspace.professionals.filter { filter.professionalIds.contains($0.id) }
    }

    private var summary: String {
        var parts: [String] = []
        if let first = people.first {
            parts.append(people.count == 1 ? first.name : "\(first.name) +\(people.count - 1)")
        }
        if let location = workspace.location(filter.locationId) { parts.append(location.name) }
        if filter.showCancelled { parts.append(L.Agenda.withCancelled) }
        return parts.joined(separator: " · ")
    }
}

/// Whose agenda to see (everyone or one person), at which location, and
/// whether cancelled appointments show. Picking a person closes the sheet.
private struct AgendaFilterSheet: View {
    @Binding var filter: AgendaRules.Filter
    @Environment(Workspace.self) private var workspace
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    /// A long team gets a search field.
    private var searchable: Bool { team.count > 8 }

    private var team: [Professional] {
        workspace.professionals.filter { $0.active || filter.professionalIds.contains($0.id) }
    }

    private var shown: [Professional] {
        let text = query.trimmingCharacters(in: .whitespaces)
        return text.isEmpty ? team : team.filter { $0.name.localizedCaseInsensitiveContains(text) }
    }

    var body: some View {
        NavigationStack {
            List {
                if workspace.isAdmin && team.count > 1 {
                    Section(L.Agenda.professional) {
                        if query.isEmpty {
                            choice(L.Agenda.everyone, selected: filter.professionalIds.isEmpty) {
                                Image(systemName: "person.2.fill")
                                    .font(.footnote)
                                    .foregroundStyle(Theme.brand)
                                    .frame(width: 34, height: 34)
                                    .background(Theme.brand.opacity(0.14), in: Circle())
                            } action: {
                                pick(nil)
                            }
                        }
                        ForEach(shown) { professional in
                            choice(professional.name, selected: filter.professionalIds == [professional.id]) {
                                Avatar(name: professional.name, colorHex: workspace.colorHex(for: professional.id), size: 34,
                                       photoURL: workspace.absoluteURL(professional.photoUrl))
                            } action: {
                                pick(professional.id)
                            }
                        }
                    }
                }
                if workspace.activeLocations.count > 1 && query.isEmpty {
                    Section(L.Agenda.location) {
                        choice(L.Agenda.allLocations, selected: filter.locationId == nil) {
                            EmptyView()
                        } action: {
                            filter.locationId = nil
                        }
                        ForEach(workspace.activeLocations) { location in
                            choice(location.name, selected: filter.locationId == location.id) {
                                EmptyView()
                            } action: {
                                filter.locationId = location.id
                            }
                        }
                    }
                }
                if workspace.isAdmin && query.isEmpty {
                    Section {
                        Toggle(L.Agenda.showCancelled, isOn: $filter.showCancelled)
                    }
                }
            }
            .modifier(SearchIf(enabled: searchable, text: $query, prompt: L.Agenda.searchStaff))
            .navigationTitle(L.Agenda.filters)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !filter.professionalIds.isEmpty || filter.locationId != nil {
                    ToolbarItem(placement: .topBarLeading) {
                        Button(L.Agenda.clearFilters) {
                            filter.professionalIds = []
                            filter.locationId = nil
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) { Button(L.Common.done) { dismiss() } }
            }
        }
        .presentationDetents(searchable ? [.large] : [.medium, .large])
    }

    private func pick(_ id: String?) {
        Haptics.selection()
        filter.professionalIds = id.map { [$0] } ?? []
        dismiss()
    }

    private func choice<Leading: View>(_ title: String, selected: Bool, @ViewBuilder leading: () -> Leading, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                leading()
                Text(title).foregroundStyle(Theme.ink).lineLimit(1)
                Spacer(minLength: 8)
                if selected {
                    Image(systemName: "checkmark").font(.body.weight(.semibold)).foregroundStyle(Theme.brand)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// `.searchable` only when there is enough to search.
private struct SearchIf: ViewModifier {
    let enabled: Bool
    @Binding var text: String
    let prompt: String

    func body(content: Content) -> some View {
        if enabled {
            content.searchable(text: $text, placement: .navigationBarDrawer(displayMode: .always), prompt: prompt)
        } else {
            content
        }
    }
}

/// A calendar to jump to any date.
struct DatePickerSheet: View {
    @Binding var selected: LocalDate
    let clock: BusinessClock
    var minimum: LocalDate?
    @Environment(\.dismiss) private var dismiss
    @State private var date: Date

    init(selected: Binding<LocalDate>, clock: BusinessClock, minimum: LocalDate? = nil) {
        _selected = selected
        self.clock = clock
        self.minimum = minimum
        _date = State(initialValue: clock.instant(selected.wrappedValue, time: "12:00") ?? Date())
    }

    var body: some View {
        NavigationStack {
            Group {
                if let minimum, let start = clock.instant(minimum, time: "00:00") {
                    DatePicker(L.Agenda.pickDate, selection: $date, in: start..., displayedComponents: .date)
                } else {
                    DatePicker(L.Agenda.pickDate, selection: $date, displayedComponents: .date)
                }
            }
            .datePickerStyle(.graphical)
            .padding(.horizontal)
            .environment(\.timeZone, clock.timeZone)
            .navigationTitle(L.Agenda.pickDate)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
            }
            .onChange(of: date) { _, value in
                selected = clock.localDate(of: value)
                Haptics.selection()
                dismiss()
            }
            Spacer(minLength: 0)
        }
        .presentationDetents([.medium, .large])
    }
}
