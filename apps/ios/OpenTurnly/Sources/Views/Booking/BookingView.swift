import SwiftUI

/// A customer as the booking form knows it: someone found, or someone new.
struct PickedCustomer: Equatable {
    let id: String
    let name: String
    let phone: String?
    let email: String?
}

/// Booking an appointment from the phone: customer, service, professional,
/// date, a free time from the server, then "Agendar cita".
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

    private var newCustomerValid: Bool {
        !firstName.trimmingCharacters(in: .whitespaces).isEmpty && !lastName.trimmingCharacters(in: .whitespaces).isEmpty
            && (!phoneNational.filter(\.isNumber).isEmpty || TextRules.isEmail(email.trimmingCharacters(in: .whitespaces)))
    }

    private var customerReady: Bool { mode == .existing ? customer != nil : newCustomerValid }
    private var needsLocation: Bool { BookingRules.needsLocation(workspace.locations) && location == nil }
    private var canSubmit: Bool { customerReady && serviceId != nil && time != nil && !needsLocation && !busy }

    private var slotKey: SlotLoader.Key {
        SlotLoader.Key(serviceId: serviceId, date: date, locationId: location?.id, appointmentId: nil)
    }

    // MARK: Body

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(L.Booking.hint).font(.subheadline).foregroundStyle(Theme.muted)
                    customerBlock
                    if places.count > 1 { locationBlock }
                    serviceBlock
                    dateBlock
                    timesBlock
                    if let failure, !(failure is CancellationError) {
                        NoticeBanner(text: ErrorText.message(failure), symbol: "exclamationmark.triangle.fill", tone: .danger)
                    }
                }
                .padding(16)
                .padding(.bottom, 90)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.canvas)
            .bottomBar { submitBar }
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

    // MARK: Customer

    private var customerBlock: some View {
        FormBlock(title: L.Booking.customer) {
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
                if attempted && !customerReady {
                    FieldError(text: mode == .existing ? L.Booking.pickCustomer : L.Booking.contactHint).padding(.top, 8)
                }
            }
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
            TextField(L.Common.email, text: $email)
                .keyboardType(.emailAddress)
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .frame(minHeight: 44)
            FieldError(text: failure.flatMap { ErrorText.field($0, "email") })
            Text(L.Booking.contactHint).font(.caption).foregroundStyle(Theme.subtle).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 4)
        }
    }

    // MARK: What and when

    private var locationBlock: some View {
        FormBlock(title: L.Booking.location) {
            MenuRow(label: L.Booking.location, symbol: "mappin.and.ellipse",
                    options: places.map { (value: Optional($0.id), title: $0.name, subtitle: $0.address) },
                    selection: Binding(get: { location?.id }, set: { locationId = $0 }),
                    placeholder: L.Booking.location)
            FieldError(text: attempted && needsLocation ? ErrorText.text("location_required") : nil)
        }
    }

    private var serviceBlock: some View {
        FormBlock(title: L.Booking.service) {
            MenuRow(label: L.Booking.service, symbol: "scissors",
                    options: services.map { (value: Optional($0.id), title: $0.name, subtitle: $0.summary(workspace.business)) },
                    selection: $serviceId, placeholder: L.Booking.pickService)
            Divider().padding(.vertical, 8)
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
    }

    private var dateBlock: some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionTitle(text: L.Booking.date, trailing: Fmt.dayLong(date))
            DayScroller(selected: $date, today: workspace.clock.today(), clock: workspace.clock)
        }
    }

    private var timesBlock: some View {
        FormBlock(title: L.Booking.freeTimes, trailing: serviceId == nil ? nil : "\(times.count)") {
            if serviceId == nil {
                Text(L.Booking.pickService).font(.subheadline).foregroundStyle(Theme.subtle)
            } else if let error = slots.error, times.isEmpty {
                Text(ErrorText.message(error)).font(.subheadline).foregroundStyle(Theme.danger)
            } else {
                SlotGrid(slots: times, selected: $time, loading: slots.loading)
            }
            if attempted && time == nil && serviceId != nil && !times.isEmpty {
                FieldError(text: L.Booking.pickTime).padding(.top, 8)
            }
        }
    }

    private var submitBar: some View {
        VStack(spacing: 0) {
            Divider()
            PrimaryButton(title: L.Booking.create, symbol: "checkmark", busy: busy, disabled: false) {
                Task { await submit() }
            }
            .opacity(canSubmit || busy ? 1 : 0.55)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .background(.bar)
    }

    // MARK: Work

    private func prepare() async {
        await workspace.loadCatalog()
        phoneISO = Phone.defaultCountry(business: workspace.business.country)
        if locationId == nil, places.count == 1 { locationId = places.first?.id }
        if serviceId == nil { serviceId = services.first?.id }
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
        let words = text.split(separator: " ").map(String.init)
        if text.contains("@") {
            email = text
        } else if text.filter(\.isNumber).count >= 6 {
            phoneNational = text.filter(\.isNumber)
        } else {
            firstName = words.first ?? ""
            lastName = words.dropFirst().joined(separator: " ")
        }
        withAnimation { mode = .new }
    }

    private func submit() async {
        attempted = true
        guard canSubmit, let serviceId, let time else {
            Haptics.warning()
            return
        }
        busy = true
        failure = nil
        defer { busy = false }
        var body: [String: JSON] = [
            "serviceId": .string(serviceId),
            "resourceId": workspace.isAdmin ? .opt(resourceId) : .opt(workspace.business.professionalId),
            "date": .string(date.string),
            "time": .string(time),
        ]
        if let location { body["locationId"] = .string(location.id) }
        if mode == .existing, let customer {
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
