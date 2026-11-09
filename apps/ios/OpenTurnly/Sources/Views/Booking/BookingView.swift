import SwiftUI

/// A customer as the booking form knows it: someone found, or someone new.
struct PickedCustomer: Equatable {
    let id: String
    let name: String
    let phone: String?
    let email: String?
}

/// Booking an appointment from the phone, in four numbered steps: customer,
/// service (and location), professional, day and a free time from the server.
struct BookingView: View {
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    var preset: PickedCustomer?
    var onBooked: ((LocalDate) -> Void)?

    @State private var mode: Mode = .existing
    @State private var customer: PickedCustomer?
    @State private var query = ""
    @State private var results: [CustomerSummary] = []
    @State private var searching = false
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var phoneISO = "CO"
    @State private var phoneNational = ""
    @State private var email = ""

    @State private var locationId: String?
    @State private var serviceId: String?
    @State private var resourceId: String?
    @State private var date: LocalDate
    @State private var time: String?
    @State private var slots = SlotLoader()

    @State private var busy = false
    @State private var attempted = false
    @State private var failure: Error?

    enum Mode: Hashable { case existing, new }
    enum Step: Hashable { case customer, service, professional, time }

    init(date: LocalDate, preset: PickedCustomer? = nil, onBooked: ((LocalDate) -> Void)? = nil) {
        self.preset = preset
        self.onBooked = onBooked
        _date = State(initialValue: date)
        _customer = State(initialValue: preset)
    }

    // MARK: Derived

    private var places: [Location] { BookingRules.places(workspace.locations, professionalId: workspace.business.professionalId) }
    private var location: Location? { workspace.location(locationId) ?? (places.count == 1 ? places.first : nil) }

    private var services: [Service] {
        var list = BookingRules.offered(workspace.services, location: location)
        if let me = workspace.me, let mine = me.serviceIds { list = list.filter { mine.contains($0.id) } }
        return list
    }

    private var team: [Professional] { BookingRules.team(workspace.professionals, serviceId: serviceId, location: location) }

    private var times: [Slot] {
        BookingRules.times(slots.slots, resourceId: workspace.isAdmin ? resourceId : nil)
    }

    private var needsLocation: Bool { BookingRules.needsLocation(workspace.locations) && location == nil }

    /// The customer step's first problem, if any.
    private var customerProblem: String? {
        if customer != nil { return nil }
        if mode == .existing { return L.Booking.pickCustomer }
        if firstName.trimmingCharacters(in: .whitespaces).isEmpty { return L.Booking.firstNameRequired }
        if lastName.trimmingCharacters(in: .whitespaces).isEmpty { return L.Booking.lastNameRequired }
        let mail = email.trimmingCharacters(in: .whitespaces)
        if !mail.isEmpty && !TextRules.isEmail(mail) { return L.Booking.emailInvalid }
        if phoneNational.filter(\.isNumber).isEmpty && mail.isEmpty { return L.Booking.contactHint }
        return nil
    }

    /// The first thing missing and the step it belongs to.
    private var missing: (step: Step, message: String)? {
        if let problem = customerProblem { return (.customer, problem) }
        if needsLocation { return (.service, ErrorText.text("location_required") ?? L.Booking.pickServiceError) }
        if serviceId == nil { return (.service, L.Booking.pickServiceError) }
        if time == nil { return (.time, L.Booking.pickTime) }
        return nil
    }

    private var summary: String? {
        guard let service = workspace.service(serviceId) else { return nil }
        return [service.name, Fmt.dayMedium(date), time].compactMap { $0 }.joined(separator: " · ")
    }

    private var slotKey: SlotLoader.Key {
        SlotLoader.Key(serviceId: serviceId, date: date, locationId: location?.id, appointmentId: nil)
    }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        StepBlock(number: 1, title: L.Booking.customer, done: customerProblem == nil) { customerBlock }
                            .id(Step.customer)
                        StepBlock(number: 2, title: L.Booking.service, done: serviceId != nil && !needsLocation) { serviceBlock }
                            .id(Step.service)
                        StepBlock(number: 3, title: L.Booking.professional, done: serviceId != nil) { professionalBlock }
                            .id(Step.professional)
                        StepBlock(number: 4, title: L.Booking.date, trailing: Fmt.dayLong(date), done: time != nil) { timeBlock }
                            .id(Step.time)
                    }
                    .padding(16)
                    .padding(.bottom, 24)
                }
                .scrollDismissesKeyboard(.interactively)
                .background(Theme.canvas)
                .bottomBar {
                    BookingBar(message: barMessage, summary: summary, title: L.Booking.create, symbol: "checkmark", busy: busy) {
                        Task { await submit(proxy) }
                    }
                }
            }
            .navigationTitle(L.Booking.title)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } } }
            .task { await prepare() }
            .task(id: slotKey) {
                time = nil
                await slots.load(slotKey, workspace: workspace)
            }
            .task(id: query) { await search() }
            .onChange(of: serviceId) { _, _ in
                if let resourceId, !team.contains(where: { $0.id == resourceId }) { self.resourceId = nil }
            }
            .onChange(of: resourceId) { _, _ in
                if let time, !times.contains(where: { $0.time == time }) { self.time = nil }
            }
        }
        .interactiveDismissDisabled(busy)
    }

    private var barMessage: String? {
        if let failure, !(failure is CancellationError) { return ErrorText.message(failure) }
        return attempted ? missing?.message : nil
    }

    // MARK: Customer

    @ViewBuilder
    private var customerBlock: some View {
        if let customer {
            HStack(spacing: 12) {
                Avatar(name: customer.name, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(customer.name).font(.body.weight(.semibold)).foregroundStyle(Theme.ink)
                    Text(Phone.format(customer.phone).isEmpty ? (customer.email ?? "") : Phone.format(customer.phone))
                        .font(.footnote).foregroundStyle(Theme.muted)
                        .lineLimit(1).truncationMode(.middle)
                }
                Spacer(minLength: 8)
                if preset == nil {
                    Button(L.Booking.change) {
                        withAnimation { self.customer = nil }
                    }
                    .font(.footnote.weight(.semibold))
                }
            }
        } else {
            Picker("", selection: $mode.animation()) {
                Text(L.Booking.existing).tag(Mode.existing)
                Text(L.Booking.newCustomer).tag(Mode.new)
            }
            .pickerStyle(.segmented)
            .padding(.bottom, 12)
            if mode == .existing { searchBox } else { newCustomerFields }
        }
    }

    private var searchBox: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(Theme.subtle)
                TextField(L.Booking.searchCustomer, text: $query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                if searching { ProgressView().controlSize(.small) }
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(Theme.fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            let trimmed = query.trimmingCharacters(in: .whitespaces)
            if trimmed.count < 2 {
                Text(L.Booking.typeToSearch).font(.footnote).foregroundStyle(Theme.subtle).padding(.top, 6)
            } else if results.isEmpty && !searching {
                HStack {
                    Text(L.Booking.noCustomerFound).font(.footnote).foregroundStyle(Theme.muted)
                    Button(L.Booking.createCustomer) { startNew(from: trimmed) }
                        .font(.footnote.weight(.semibold))
                }
                .padding(.top, 6)
            } else {
                ForEach(results.prefix(8)) { found in
                    Button {
                        Haptics.selection()
                        withAnimation {
                            customer = PickedCustomer(id: found.id, name: found.fullName, phone: found.phone, email: found.email)
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Avatar(name: found.fullName, size: 36)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(found.fullName).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.ink)
                                Text([Phone.format(found.phone), found.email ?? ""].filter { !$0.isEmpty }.joined(separator: " · "))
                                    .font(.caption).foregroundStyle(Theme.muted).lineLimit(1)
                            }
                            Spacer()
                            Image(systemName: "plus.circle.fill").foregroundStyle(Theme.brand)
                        }
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var newCustomerFields: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                TextField(L.Booking.firstName, text: $firstName).textContentType(.givenName)
                Divider().frame(height: 24)
                TextField(L.Booking.lastName, text: $lastName).textContentType(.familyName)
            }
            .frame(minHeight: 44)
            FieldError(text: failure.flatMap { ErrorText.field($0, "firstName") ?? ErrorText.field($0, "lastName") })
            Divider()
            PhoneField(iso: $phoneISO, national: $phoneNational, placeholder: L.Common.phone)
                .frame(minHeight: 44)
            FieldError(text: failure.flatMap { ErrorText.field($0, "phone") })
            Divider()
            TextField(L.Booking.emailOptional, text: $email)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .frame(minHeight: 44)
            FieldError(text: failure.flatMap { ErrorText.field($0, "email") })
            Text(L.Booking.contactHint).font(.caption).foregroundStyle(Theme.subtle).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 4)
        }
    }

    // MARK: What, who and when

    @ViewBuilder
    private var serviceBlock: some View {
        if places.count > 1 {
            MenuRow(label: L.Booking.location, symbol: "mappin.and.ellipse",
                    options: places.map { (value: Optional($0.id), title: $0.name, subtitle: $0.address) },
                    selection: Binding(get: { location?.id }, set: { locationId = $0 }),
                    placeholder: L.Booking.location)
            Divider().padding(.vertical, 8)
        }
        ServiceField(services: services, selection: $serviceId)
    }

    @ViewBuilder
    private var professionalBlock: some View {
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

    private var timeBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            DayScroller(selected: $date, today: workspace.clock.today(), clock: workspace.clock)
            Divider()
            GroupedSlots(serviceChosen: serviceId != nil, slots: times, selected: $time, loading: slots.loading, error: slots.error)
        }
    }

    // MARK: Work

    private func prepare() async {
        await workspace.loadCatalog()
        phoneISO = Phone.defaultCountry(business: workspace.business.country)
        if locationId == nil {
            // The agenda's location filter first, else the first active place.
            let filtered = places.first { $0.id == workspace.agendaLocation }
            locationId = (filtered ?? places.first)?.id
        }
    }

    private func search() async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else {
            results = []
            return
        }
        try? await Task.sleep(for: .milliseconds(280))
        guard !Task.isCancelled else { return }
        searching = true
        defer { searching = false }
        do {
            results = try await workspace.api.customers(workspace.id, query: trimmed)
        } catch is CancellationError {
        } catch {
            results = []
        }
    }

    private func startNew(from text: String) {
        let draft = BookingRules.draft(from: text)
        firstName = draft.firstName
        lastName = draft.lastName
        if !draft.phone.isEmpty { phoneNational = draft.phone }
        email = draft.email
        withAnimation { mode = .new }
    }

    private func submit(_ proxy: ScrollViewProxy) async {
        attempted = true
        failure = nil
        if let missing {
            Haptics.warning()
            withAnimation { proxy.scrollTo(missing.step, anchor: .top) }
            return
        }
        guard let serviceId, let time else { return }
        busy = true
        defer { busy = false }
        var body: [String: JSON] = [
            "serviceId": .string(serviceId),
            "resourceId": workspace.isAdmin ? .opt(resourceId) : .opt(workspace.business.professionalId),
            "date": .string(date.string),
            "time": .string(time),
        ]
        if let location { body["locationId"] = .string(location.id) }
        if let customer {
            body["customerId"] = .string(customer.id)
        } else {
            var details: [String: JSON] = [
                "firstName": .string(firstName.trimmingCharacters(in: .whitespaces)),
                "lastName": .string(lastName.trimmingCharacters(in: .whitespaces)),
            ]
            if let phone = Phone.compose(iso: phoneISO, national: phoneNational) { details["phone"] = .string(phone) }
            if let mail = TextRules.clean(email) { details["email"] = .string(mail) }
            body["customer"] = .object(details)
        }
        do {
            _ = try await workspace.api.createAppointment(workspace.id, body)
            toasts.show(L.Booking.created)
            workspace.changed()
            workspace.agendaDay = date
            onBooked?(date)
            dismiss()
        } catch {
            failure = error
            Haptics.error()
            if let api = error as? APIError, api.code == "slot_taken" {
                self.time = nil
                await slots.load(slotKey, workspace: workspace)
            }
        }
    }
}
