import Foundation

/// Server errors in plain words.
///
/// The server answers with stable codes (`slot_taken`) and, for invalid input,
/// a code per field (`customer.phone: invalid_phone`). Like the web, a message is
/// looked up as "<field>_<code>" first, then "<code>", then a generic one.
enum ErrorText {
    static func message(_ error: Error) -> String {
        guard let error = error as? APIError else { return L.Errors.generic }
        return message(code: error.code, fields: error.fields)
    }

    static func message(code: String, fields: [String: String]) -> String {
        if code == "already_booked", let date = fields["startsAt"].flatMap(APIClient.parseInstant) {
            return L.Booking.alreadyBooked(Fmt.dayShort(LocalDate(noonUTC: date)), fields["code"] ?? "")
        }
        if code == "network" { return L.Errors.network }
        // Field errors carry the specific reason.
        if code == "validation_failed" || code == "customer_exists" {
            for key in fields.keys.sorted() {
                guard let issue = fields[key] else { continue }
                let field = key.split(separator: ".").last.map(String.init) ?? key
                if let text = text("\(field)_\(issue)") ?? text(issue) { return text }
            }
        }
        if let text = text(code) { return text }
        return L.Errors.generic
    }

    /// The message for one field of a failed form, if the server named it.
    static func field(_ error: Error, _ name: String) -> String? {
        guard let error = error as? APIError else { return nil }
        for (key, issue) in error.fields where key == name || key.hasSuffix(".\(name)") {
            return text("\(name)_\(issue)") ?? text(issue)
        }
        return nil
    }

    // swiftlint:disable:next cyclomatic_complexity function_body_length
    static func text(_ key: String) -> String? {
        switch key {
        case "unauthorized": return L.SignIn.sessionEnded
        case "invalid_credentials", "invalid_email_or_password": return L.SignIn.invalidCredentials
        case "too_many_attempts": return tr("Too many tries with a wrong password. Wait 15 minutes.", "Demasiados intentos con una contraseña equivocada. Espera 15 minutos.")
        case "forbidden": return tr("You don't have permission to do this.", "No tienes permiso para hacer esto.")
        case "forbidden_origin": return tr("This can only be done from the web panel.", "Esto solo se puede hacer desde el panel web.")
        case "no_profile": return tr("Your user has no staff profile. Ask the admin to link one.", "Tu usuario no tiene un perfil de profesional. Pídele al administrador que lo vincule.")
        case "not_found": return tr("We couldn't find that.", "No encontramos eso.")
        case "business_not_found": return tr("We couldn't find this business.", "No encontramos este negocio.")
        case "appointment_not_found": return tr("We couldn't find this appointment.", "No encontramos esta cita.")
        case "customer_not_found": return tr("That customer no longer exists.", "Ese cliente ya no existe.")
        case "service_not_found": return tr("We couldn't find this service.", "No encontramos este servicio.")
        case "professional_not_found": return tr("We couldn't find this staff member.", "No encontramos este profesional.")
        case "location_not_found": return tr("That location doesn't exist or isn't taking bookings.", "Ese local no existe o no está recibiendo reservas.")
        case "sale_not_found": return tr("We couldn't find that sale.", "No encontramos esa venta.")
        case "deposit_not_found": return tr("We couldn't find that deposit.", "No encontramos ese abono.")
        case "gift_card_not_found": return tr("That code doesn't exist.", "Ese código no existe.")
        case "member_not_found": return tr("That person no longer has access.", "Esa persona ya no tiene acceso.")
        case "location_required": return tr("Choose a location.", "Elige un local.")
        case "slot_taken": return L.Booking.slotTaken
        case "appointment_cancelled": return tr("That appointment was already cancelled.", "Esa cita ya fue cancelada.")
        case "appointment_no_show": return tr("This appointment was marked as a no-show.", "Esta cita quedó como «No asistió».")
        case "appointment_unpaid": return tr("This appointment is waiting for the customer's payment.", "Esta cita está esperando el pago del cliente.")
        case "appointment_paid": return tr("This appointment was already charged.", "Esta cita ya se cobró.")
        case "appointment_not_started": return tr("The appointment hasn't started yet: mark it once the time comes.", "La cita todavía no empieza: márcala cuando pase la hora.")
        case "customer_exists", "phone_customer_exists": return tr("You already have a customer with that phone.", "Ya tienes un cliente con ese celular.")
        case "email_customer_exists": return tr("You already have a customer with that email.", "Ya tienes un cliente con ese email.")
        case "payments_mismatch": return L.Checkout.paymentsMismatch
        case "payments_deposit_amount": return tr("A deposit has to be used in full.", "Un abono se usa completo.")
        case "deposit_unavailable": return tr("That deposit isn't available any more.", "Ese abono ya no está disponible.")
        case "deposit_used": return tr("That deposit was used in a sale. Void the sale first.", "Ese abono ya se usó en una venta. Anula la venta primero.")
        case "gift_card_unusable": return tr("That code can't be used right now.", "Ese código no se puede usar ahora.")
        case "gift_card_balance": return tr("The gift card doesn't have enough balance.", "La gift card no tiene saldo suficiente.")
        case "discount_too_big", "discountAmount_too_big": return tr("The discount is bigger than the sale.", "El descuento es mayor que la venta.")
        case "code_invalid_code", "_invalid_code": return tr("Use 3 to 24 letters, numbers or dashes.", "Usa de 3 a 24 letras, números o guiones.")
        case "wrong_password", "currentPassword_wrong_password": return tr("The current password isn't right.", "La contraseña actual no es correcta.")
        case "user_already_exists": return tr("An account with this email already exists.", "Ya existe una cuenta con este email.")
        case "already_member", "email_already_member": return tr("That person already has access to this business.", "Esa persona ya tiene acceso a este negocio.")
        case "already_has_access": return tr("That person already has access to every business.", "Esa persona ya tiene acceso a todos los negocios.")
        case "invalid_range": return tr("Invalid date range.", "El rango de fechas no es válido.")
        case "invalid_date": return tr("Choose a date.", "Elige una fecha.")
        case "invalid_time": return tr("Invalid time.", "La hora no es válida.")
        case "hours_overlap": return L.Hours.overlap
        case "hours_end_before_start": return L.Hours.endBeforeStart
        case "not_at_location": return tr("That staff member doesn't work at that location.", "Ese profesional no atiende en ese local.")
        case "cash_unchanged": return tr("The counted cash matches the drawer: nothing to adjust.", "El efectivo contado es igual al que hay en caja: no hay nada que rectificar.")
        case "invalid_phone", "phone_invalid_phone", "phone_invalid_format": return tr("Enter a valid phone number, with its country code.", "Escribe un número de celular válido, con su código de país.")
        case "contact_required", "phone_contact_required", "email_contact_required": return L.Booking.contactHint
        case "email_invalid_format": return tr("Enter a valid email.", "Escribe un email válido.")
        case "password_too_small", "newPassword_too_small": return tr("Use at least 10 characters.", "Usa al menos 10 caracteres.")
        case "name_too_small", "firstName_too_small", "lastName_too_small": return tr("This field is required.", "Este campo es obligatorio.")
        case "birthDate_invalid_date": return tr("Check the date: it can't be in the future.", "Revisa la fecha: no puede ser en el futuro.")
        case "customerId_required": return L.Booking.pickCustomer
        case "depositAmount_price_required": return tr("Give the service a price to charge it online.", "Ponle precio al servicio para cobrarlo en línea.")
        case "depositAmount_deposit_required": return tr("Enter the deposit amount.", "Escribe de cuánto es el abono.")
        case "depositAmount_deposit_too_high": return tr("The deposit can't be more than the price.", "El abono no puede ser mayor que el precio.")
        case "required": return tr("This field is required.", "Este campo es obligatorio.")
        case "too_small": return tr("This field is too short.", "Este campo es muy corto.")
        case "too_big": return tr("This field is too long.", "Este campo es muy largo.")
        case "invalid_format": return tr("The format is not valid.", "El formato no es válido.")
        case "invalid_type": return tr("This field is required.", "Este campo es obligatorio.")
        case "invalid_value": return tr("Choose a valid option.", "Elige una opción válida.")
        case "internal_error": return tr("Something went wrong on the server. Please try again.", "Algo salió mal en el servidor. Inténtalo de nuevo.")
        case "unknown_error": return L.Errors.generic
        case "network": return L.Errors.network
        default: return nil
        }
    }
}
