import SwiftUI

/// Creating or editing a customer. Phones are sent with their country code, and
/// at least a phone or an e-mail is required, like the server asks.
struct CustomerFormView: View {
    let customer: Customer?
    let onSaved: (Customer) -> Void
    @Environment(Workspace.self) private var workspace
    @Environment(ToastCenter.self) private var toasts
    @Environment(\.dismiss) private var dismiss

    @State private var firstName = ""
    @State private var lastName = ""
    @State private var phoneISO = "CO"
    @State private var phoneNational = ""
    @State private var email = ""
    @State private var notes = ""
    @State private var document = ""
    @State private var gender = ""
    @State private var hasBirthDate = false
    @State private var birthDate = Date()
    @State private var busy = false
    @State private var failure: Error?
    @State private var attempted = false
    @State private var prepared = false

    private var phone: String? { Phone.compose(iso: phoneISO, national: phoneNational) }
    private var namesValid: Bool {
        !firstName.trimmingCharacters(in: .whitespaces).isEmpty && !lastName.trimmingCharacters(in: .whitespaces).isEmpty
    }
    private var contactValid: Bool { phone != nil || !email.trimmingCharacters(in: .whitespaces).isEmpty }
    private var emailValid: Bool { email.trimmingCharacters(in: .whitespaces).isEmpty || TextRules.isEmail(email.trimmingCharacters(in: .whitespaces)) }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L.Booking.firstName, text: $firstName).textContentType(.givenName)
                    TextField(L.Booking.lastName, text: $lastName).textContentType(.familyName)
                } footer: {
                    if attempted && firstName.trimmingCharacters(in: .whitespaces).isEmpty { FieldError(text: L.Booking.firstNameRequired) }
                    else if attempted && lastName.trimmingCharacters(in: .whitespaces).isEmpty { FieldError(text: L.Booking.lastNameRequired) }
                    else { FieldError(text: failure.flatMap { ErrorText.field($0, "firstName") ?? ErrorText.field($0, "lastName") }) }
                }
                Section {
                    PhoneField(iso: $phoneISO, national: $phoneNational, placeholder: L.Common.phone)
                    TextField(L.Common.email, text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text(L.Customers.contact)
                } footer: {
                    VStack(alignment: .leading, spacing: 4) {
                        if attempted && !contactValid {
                            FieldError(text: L.Booking.contactHint)
                        } else {
                            Text(L.Booking.contactHint)
                        }
                        if attempted && !emailValid { FieldError(text: L.Booking.emailInvalid) }
                        FieldError(text: failure.flatMap { ErrorText.field($0, "phone") ?? ErrorText.field($0, "email") })
                    }
                }
                Section(L.Customers.notes) {
                    TextField(L.Customers.notesHint, text: $notes, axis: .vertical).lineLimit(3...8)
                }
                Section(L.Customers.moreDetails) {
                    Toggle(L.Customers.birthDate, isOn: $hasBirthDate.animation())
                    if hasBirthDate {
                        DatePicker(L.Customers.birthDate, selection: $birthDate, in: ...Date(), displayedComponents: .date)
                            .environment(\.timeZone, TimeZone(identifier: "UTC")!)
                    }
                    TextField(L.Customers.document, text: $document)
                    Picker(L.Customers.gender, selection: $gender) {
                        Text(L.Customers.genderName(nil)).tag("")
                        Text(L.Customers.genderName("female")).tag("female")
                        Text(L.Customers.genderName("male")).tag("male")
                        Text(L.Customers.genderName("other")).tag("other")
                    }
                }
                if let failure, ErrorText.field(failure, "phone") == nil, ErrorText.field(failure, "email") == nil,
                   ErrorText.field(failure, "firstName") == nil, ErrorText.field(failure, "lastName") == nil {
                    Section { FieldError(text: ErrorText.message(failure)) }
                }
            }
            .navigationTitle(customer == nil ? L.Customers.new : L.Customers.edit)
            .keyboardDoneButton()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(L.Common.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if busy { ProgressView() } else {
                        Button(L.Common.save) { Task { await save() } }.fontWeight(.semibold)
                    }
                }
            }
            .onAppear(perform: prepare)
        }
        .interactiveDismissDisabled(busy)
    }

    private func prepare() {
        guard !prepared else { return }
        prepared = true
        phoneISO = Phone.defaultCountry(business: workspace.business.country)
        guard let customer else { return }
        firstName = customer.firstName
        lastName = customer.lastName
        if let stored = customer.phone, let parts = Phone.split(stored) {
            phoneISO = parts.iso
            phoneNational = parts.national
        }
        email = customer.email ?? ""
        notes = customer.notes ?? ""
        document = customer.document ?? ""
        gender = customer.gender ?? ""
        if let birth = customer.birthDate.flatMap(LocalDate.init) {
            hasBirthDate = true
            birthDate = birth.noonUTC
        }
    }

    private func save() async {
        attempted = true
        guard namesValid, contactValid, emailValid else {
            Haptics.warning()
            return
        }
        busy = true
        failure = nil
        defer { busy = false }
        let body: [String: JSON] = [
            "firstName": .string(firstName.trimmingCharacters(in: .whitespaces)),
            "lastName": .string(lastName.trimmingCharacters(in: .whitespaces)),
            "phone": .opt(phone),
            "email": .text(email),
            "notes": .text(notes),
            "document": .text(document),
            "gender": gender.isEmpty ? .null : .string(gender),
            "birthDate": hasBirthDate ? .string(LocalDate(noonUTC: birthDate).string) : .null,
        ]
        do {
            let saved: Customer
            if let customer {
                saved = try await workspace.api.updateCustomer(workspace.id, customer.id, body)
                toasts.show(L.Common.saved)
            } else {
                saved = try await workspace.api.createCustomer(workspace.id, body)
                toasts.show(L.Customers.created)
            }
            workspace.changed()
            onSaved(saved)
            dismiss()
        } catch {
            failure = error
            Haptics.error()
        }
    }
}
