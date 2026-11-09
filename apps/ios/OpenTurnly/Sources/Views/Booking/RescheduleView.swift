import SwiftUI

/// Moving an appointment: another day, time, professional or service. Only times
/// where it fits are offered; its own current time counts as free.
struct RescheduleView: View {
    let appointment: Appointment
    var onSaved: ((Date) -> Void)?
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    @State private var locationId: String?
    @State private var serviceId: String?
    @State private var resourceId: String?
    @State private var date: LocalDate
    @State private var time: String?
    @State private var slots = SlotLoader()
    @State private var busy = false
    @State private var failure: Error?
    @State private var prepared = false

    init(appointment: Appointment, onSaved: ((Date) -> Void)? = nil) {
        self.appointment = appointment
        self.onSaved = onSaved
        // The business clock is not reachable before the view is in a workspace;
        // the device zone is only a first guess, corrected in `prepare`.
        _date = State(initialValue: BusinessClock(timeZone: .current).localDate(of: appointment.startsAt))
        _locationId = State(initialValue: appointment.locationId)
        _serviceId = State(initialValue: appointment.serviceId)
        _resourceId = State(initialValue: appointment.resourceId)
    }

    private var clock: BusinessClock { workspace.clock }
    private var originalDate: LocalDate { clock.localDate(of: appointment.startsAt) }
    private var originalTime: String { clock.time(of: appointment.startsAt) }

    private var places: [Location] {
        BookingRules.places(workspace.locations, keeping: appointment.locationId, professionalId: workspace.business.professionalId)
    }
    private var location: Location? { workspace.location(locationId) }
    private var services: [Service] {
        var list = BookingRules.offered(workspace.services, location: location)
        if let id = appointment.serviceId, !list.contains(where: { $0.id == id }), let own = workspace.service(id) { list.insert(own, at: 0) }
        return list
    }
    private var team: [Professional] { BookingRules.team(workspace.professionals, serviceId: serviceId, location: location) }
    private var times: [Slot] { BookingRules.times(slots.slots, resourceId: workspace.isAdmin ? resourceId : nil) }

    private var slotKey: SlotLoader.Key {
        SlotLoader.Key(serviceId: serviceId, date: date, locationId: locationId, appointmentId: appointment.id)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(appointment.customer.fullName).font(.title3.weight(.bold)).foregroundStyle(Theme.ink)
                        Text(L.Booking.rescheduleHint(appointment.code)).font(.subheadline).foregroundStyle(Theme.muted)
                    }
                    currentCard
                    StepBlock(number: 1, title: L.Booking.service, done: serviceId != nil) {
                        if places.count > 1 {
                            MenuRow(label: L.Booking.location, symbol: "mappin.and.ellipse",
                                    options: places.map { (value: Optional($0.id), title: $0.name, subtitle: $0.address) },
                                    selection: $locationId)
                            Divider().padding(.vertical, 8)
                        }
                        ServiceField(services: services, selection: $serviceId)
                    }
                    StepBlock(number: 2, title: L.Booking.professional, done: serviceId != nil) {
                        if workspace.isAdmin {
                            MenuRow(label: L.Booking.professional, symbol: "person",
                                    options: [(value: String?.none, title: L.Booking.anyFree, subtitle: nil)]
                                        + team.map { (value: Optional($0.id), title: $0.name, subtitle: nil) },
                                    selection: $resourceId)
                        } else {
                            HStack(spacing: 12) {
                                Image(systemName: "person.fill.checkmark").foregroundStyle(Theme.brand).frame(width: 22)
                                Text(L.Booking.bookedForYou(workspace.me?.name ?? workspace.session.user.name))
                                    .font(.subheadline).foregroundStyle(Theme.muted)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                    StepBlock(number: 3, title: L.Booking.date, trailing: Fmt.dayLong(date), done: time != nil) {
                        VStack(alignment: .leading, spacing: 14) {
                            DayScroller(selected: $date, today: clock.today(), clock: clock)
                            Divider()
                            GroupedSlots(serviceChosen: serviceId != nil, slots: times, selected: $time,
                                         current: date == originalDate ? originalTime : nil, loading: slots.loading, error: slots.error)
                        }
                    }
                }
                .padding(16)
                .padding(.bottom, 24)
            }
            .background(Theme.canvas)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                BookingBar(message: failure.map { ErrorText.message($0) }, summary: nil, title: L.Common.saveChanges,
                           busy: busy, disabled: serviceId == nil || time == nil) {
                    Task { await save() }
                }
            }
            .navigationTitle(L.Appointment.reschedule)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
            .task { await prepare() }
            .task(id: slotKey) {
                guard prepared else { return }
                await loadSlots()
            }
            .onChange(of: serviceId) { _, _ in
                if let resourceId, !team.contains(where: { $0.id == resourceId }) { self.resourceId = nil }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    /// Where the appointment is now: «Lunes, 9 de noviembre · 16:00», «service · professional».
    private var currentCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(L.Booking.currentTime).font(.caption.weight(.semibold)).foregroundStyle(Theme.muted).textCase(.uppercase)
            Text("\(Fmt.dayLong(originalDate, thisYear: clock.today().year)) · \(originalTime)")
                .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
            Text([appointment.serviceName, appointment.resourceName].compactMap { $0 }.joined(separator: " · "))
                .font(.footnote).foregroundStyle(Theme.muted)
        }
        .card(padding: 14)
    }

    private func prepare() async {
        await workspace.loadCatalog()
        let today = clock.today()
        date = originalDate < today ? today : originalDate
        prepared = true
        await loadSlots()
    }

    private func loadSlots() async {
        await slots.load(slotKey, workspace: workspace)
        // Keep the current time picked while it still fits.
        if date == originalDate, times.contains(where: { $0.time == originalTime }), time == nil || !times.contains(where: { $0.time == time }) {
            time = originalTime
        } else if let time, !times.contains(where: { $0.time == time }) {
            self.time = nil
        }
    }

    private func save() async {
        guard let serviceId, let time else { return }
        busy = true
        failure = nil
        defer { busy = false }
        var body: [String: JSON] = [
            "serviceId": .string(serviceId),
            "resourceId": workspace.isAdmin ? .opt(resourceId) : .opt(appointment.resourceId),
            "date": .string(date.string),
            "time": .string(time),
        ]
        if let locationId { body["locationId"] = .string(locationId) }
        do {
            let row = try await workspace.api.reschedule(workspace.id, appointment.id, body)
            toasts.show(L.Appointment.rescheduled)
            workspace.changed()
            workspace.agendaDay = clock.localDate(of: row.startsAt)
            onSaved?(row.startsAt)
            dismiss()
        } catch {
            failure = error
            Haptics.error()
            if (error as? APIError)?.code == "slot_taken" { await loadSlots() }
        }
    }
}
