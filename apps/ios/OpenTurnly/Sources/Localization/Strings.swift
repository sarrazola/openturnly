import Foundation

/// Interface copy, English and Spanish side by side.
///
/// Each string is written once with both languages, so a label can't exist in
/// one and be missing in the other. The Spanish wording follows the web admin's.
enum L {
    enum Common {
        static var ok: String { tr("OK", "Aceptar") }
        static var cancel: String { tr("Cancel", "Cancelar") }
        static var close: String { tr("Close", "Cerrar") }
        static var save: String { tr("Save", "Guardar") }
        static var saveChanges: String { tr("Save changes", "Guardar cambios") }
        static var saved: String { tr("Changes saved", "Cambios guardados") }
        static var edit: String { tr("Edit", "Editar") }
        static var done: String { tr("Done", "Listo") }
        static var retry: String { tr("Try again", "Reintentar") }
        static var back: String { tr("Back", "Atrás") }
        static var optional: String { tr("Optional", "Opcional") }
        static var loading: String { tr("Loading…", "Cargando…") }
        static var search: String { tr("Search", "Buscar") }
        static var today: String { tr("Today", "Hoy") }
        static var tomorrow: String { tr("Tomorrow", "Mañana") }
        static var yesterday: String { tr("Yesterday", "Ayer") }
        static var call: String { tr("Call", "Llamar") }
        static var whatsapp: String { "WhatsApp" }
        static var email: String { "Email" }
        static var share: String { tr("Share", "Compartir") }
        static var copy: String { tr("Copy", "Copiar") }
        static var copied: String { tr("Copied", "Copiado") }
        static var open: String { tr("Open", "Abrir") }
        static var add: String { tr("Add", "Agregar") }
        static var remove: String { tr("Remove", "Quitar") }
        static var active: String { tr("Active", "Activo") }
        static var inactive: String { tr("Inactive", "Inactivo") }
        static var name: String { tr("Name", "Nombre") }
        static var phone: String { tr("Phone", "Celular") }
        static var address: String { tr("Address", "Dirección") }
        static var description: String { tr("Description", "Descripción") }
        static var notes: String { tr("Notes", "Notas") }
        static var date: String { tr("Date", "Fecha") }
        static var time: String { tr("Time", "Hora") }
        static var total: String { "Total" }
        static var none: String { tr("None", "Ninguno") }
        static var somethingWrong: String { tr("Something went wrong", "Algo salió mal") }
        static var couldNotLoad: String { tr("Couldn't load this", "No se pudo cargar") }
        static var period: String { tr("Period", "Periodo") }
        static var thisWeek: String { tr("This week", "Esta semana") }
        static var thisMonth: String { tr("This month", "Este mes") }
        static var lastMonth: String { tr("Last month", "Mes pasado") }
        static var last30: String { tr("Last 30 days", "Últimos 30 días") }
        static var previous: String { tr("Previous", "Anterior") }
        static var next: String { tr("Next", "Siguiente") }
        static var more: String { tr("More", "Más") }
        static var readOnly: String { tr("Only an administrator can change this.", "Solo un administrador puede cambiar esto.") }
    }

    enum SignIn {
        static var title: String { tr("Welcome back", "Hola de nuevo") }
        static var subtitle: String { tr("Sign in to manage your business.", "Ingresa para gestionar tu negocio.") }
        static var serverTitle: String { tr("Connect to your server", "Conéctate a tu servidor") }
        static func serverHint(_ name: String) -> String {
            tr("The address where your business's \(name) runs: the same one you open in the browser.",
               "La dirección donde funciona el \(name) de tu negocio: la misma que abres en el navegador.")
        }
        static var serverLabel: String { tr("Server address", "Dirección del servidor") }
        static var serverPlaceholder: String { tr("bookings.yourbusiness.com", "turnos.tunegocio.com") }
        static var invalidServer: String {
            tr("Enter a valid address. Plain http:// only works on your local network.",
               "Escribe una dirección válida. http:// sin cifrar solo funciona en tu red local.")
        }
        static var unreachable: String {
            tr("We couldn't reach that address. Check it and your connection.",
               "No pudimos conectar con esa dirección. Revísala y revisa tu conexión.")
        }
        static var notOpenTurnly: String {
            tr("That address answers, but it doesn't look like a booking server.",
               "Esa dirección responde, pero no parece un servidor de reservas.")
        }
        static var `continue`: String { tr("Continue", "Continuar") }
        static var accountLabel: String { tr("Account name", "Nombre de la cuenta") }
        static var accountHint: String { tr("The name of your account, or its full address.", "El nombre de tu cuenta, o su dirección completa.") }
        static var accountPlaceholder: String { tr("your-business", "tu-negocio") }
        static var invalidAccount: String { tr("Enter your account name or its address.", "Escribe el nombre de tu cuenta o su dirección.") }
        static var otherAddress: String { tr("Use another address", "Usar otra dirección") }
        static func useHosted(_ label: String) -> String { tr("Use \(label)", "Usar \(label)") }
        static var emailLabel: String { "Email" }
        static var emailPlaceholder: String { tr("you@business.com", "tu@negocio.com") }
        static var passwordLabel: String { tr("Password", "Contraseña") }
        static var submit: String { tr("Sign in", "Ingresar") }
        static func connectedTo(_ host: String) -> String { tr("Connected to \(host)", "Conectado a \(host)") }
        static var changeServer: String { tr("Change server", "Cambiar servidor") }
        static var invalidCredentials: String { tr("The email or password isn't right.", "El email o la contraseña no son correctos.") }
        static var forgot: String { tr("Forgot your password?", "¿Olvidaste tu contraseña?") }
        static var forgotTitle: String { tr("Your administrator manages access", "El administrador gestiona tu acceso") }
        static var forgotBody: String {
            tr("Ask your business's administrator to set a new password for you from the web panel.",
               "Pídele al administrador de tu negocio que te asigne una contraseña nueva desde el panel web.")
        }
        static var chooseAccount: String { tr("Which account?", "¿Qué cuenta?") }
        static var chooseAccountBody: String {
            tr("This email opens more than one account. Pick the one you want to work in.",
               "Este email abre más de una cuenta. Elige con cuál quieres trabajar.")
        }
        static var privacy: String { tr("Privacy policy", "Política de privacidad") }
        static var support: String { tr("Help", "Ayuda") }
        static var showPassword: String { tr("Show password", "Mostrar contraseña") }
        static var hidePassword: String { tr("Hide password", "Ocultar contraseña") }
        static var sessionEnded: String { tr("Your session ended. Sign in again.", "Tu sesión terminó. Ingresa de nuevo.") }
    }

    enum Business {
        static var choose: String { tr("Choose a business", "Selecciona un negocio") }
        static var switchBusiness: String { tr("Switch business", "Cambiar de negocio") }
        static var noneTitle: String { tr("No businesses yet", "Aún no tienes negocios") }
        static var noneBody: String {
            tr("Ask an administrator to give you access, or create a business from the web panel.",
               "Pídele a un administrador que te dé acceso, o crea un negocio desde el panel web.")
        }
        static var admin: String { tr("Admin", "Administrador") }
        static var professional: String { tr("Staff member", "Profesional") }
        static var reconnectTitle: String { tr("Couldn't connect", "No se pudo conectar") }
        static var reconnectBody: String {
            tr("Your session is saved. Check your connection and try again.",
               "Tu sesión sigue guardada. Revisa tu conexión e inténtalo de nuevo.")
        }
        static func role(_ access: BusinessAccess) -> String {
            access.role == "professional" ? professional : admin
        }
    }

    enum Tabs {
        static var agenda: String { tr("Calendar", "Agenda") }
        static var customers: String { tr("Customers", "Clientes") }
        static var sales: String { tr("Sales", "Ventas") }
        static var more: String { tr("More", "Más") }
    }

    enum Agenda {
        static var title: String { tr("Calendar", "Agenda") }
        static var pickDate: String { tr("Pick a date", "Elegir fecha") }
        static var everyone: String { tr("All staff", "Todos") }
        static var professional: String { tr("Staff member", "Profesional") }
        static var searchStaff: String { tr("Search staff", "Buscar profesional") }
        static var allLocations: String { tr("All locations", "Todos los locales") }
        static var location: String { tr("Location", "Local") }
        static var showCancelled: String { tr("Show cancelled", "Mostrar canceladas") }
        static var filters: String { tr("Filters", "Filtros") }
        static var emptyTitle: String { tr("A free day", "Día libre") }
        static var emptyBody: String { tr("Nothing booked for this day yet.", "No hay citas este día todavía.") }
        static var emptyFiltered: String { tr("No appointments match these filters.", "Ninguna cita coincide con estos filtros.") }
        static var clearFilters: String { tr("Clear filters", "Quitar filtros") }
        static var morning: String { tr("Morning", "Mañana") }
        static var afternoon: String { tr("Afternoon", "Tarde") }
        static var evening: String { tr("Evening", "Noche") }
        static func count(_ n: Int) -> String { n == 1 ? tr("1 appointment", "1 cita") : tr("\(n) appointments", "\(n) citas") }
        static var now: String { tr("Now", "Ahora") }
        static var newAppointment: String { tr("New appointment", "Nueva cita") }
        static var noProfessional: String { tr("No staff member", "Sin profesional") }
        static var expected: String { tr("expected", "esperado") }
        static var yourAgenda: String { tr("Your appointments", "Tus citas") }
        static var loadFailed: String { tr("Couldn't load the calendar", "No se pudo cargar la agenda") }
    }

    enum Status {
        static var upcoming: String { tr("Upcoming", "Próxima") }
        static var confirmed: String { tr("Confirmed", "Confirmada") }
        static var cancelled: String { tr("Cancelled", "Cancelada") }
        static var noShow: String { tr("No-show", "No asistió") }
        static var pendingPayment: String { tr("Awaiting payment", "Pendiente de pago") }
        static var pendingPaymentHint: String {
            tr("The time is held while the customer pays online. If they don't, it frees itself.",
               "El horario está apartado mientras el cliente paga en línea. Si no paga, se libera solo.")
        }
        static func label(_ status: AppointmentRules.DisplayStatus) -> String {
            switch status {
            case .upcoming: return upcoming
            case .confirmed: return confirmed
            case .cancelled: return cancelled
            case .noShow: return noShow
            case .pendingPayment: return pendingPayment
            }
        }
    }

    enum Appointment {
        static var title: String { tr("Appointment", "Cita") }
        static var code: String { tr("Code", "Código") }
        static var service: String { tr("Service", "Servicio") }
        static var professional: String { tr("Staff member", "Profesional") }
        static var when: String { tr("When", "Cuándo") }
        static var price: String { tr("Price", "Precio") }
        static var priceOnRequest: String { tr("On request", "Consultar") }
        static func discountCode(_ code: String) -> String { tr("Code \(code)", "Código \(code)") }
        static var bookedVia: String { tr("Booked via", "Agendada por") }
        static var sourceOnline: String { tr("Booking page", "Página de reservas") }
        static var sourceAdmin: String { tr("Admin panel", "Panel") }
        static var sourceAgent: String { tr("AI agent", "Agente IA") }
        static var answers: String { tr("Booking answers", "Respuestas de la reserva") }
        static var customerNotes: String { tr("Customer notes", "Notas del cliente") }
        static var customerNotesHint: String {
            tr("Only your team sees them. For example: \"prefers a low fade\".",
               "Solo las ve tu equipo. Por ejemplo: «prefiere degradado bajo».")
        }
        static var noNotes: String { tr("No notes yet.", "Sin notas todavía.") }
        static var addNotes: String { tr("Add notes", "Agregar notas") }
        static var editNotes: String { tr("Edit notes", "Editar notas") }
        static var notesSaved: String { tr("Notes saved", "Notas guardadas") }
        static var payment: String { tr("Payment", "Pago") }
        static var reschedule: String { tr("Edit appointment", "Editar cita") }
        static var cancel: String { tr("Cancel appointment", "Cancelar cita") }
        static var cancelTitle: String { tr("Cancel this appointment?", "¿Cancelar esta cita?") }
        static var cancelBody: String {
            tr("The time becomes available again for other customers.", "El horario quedará disponible de nuevo para otros clientes.")
        }
        static var keep: String { tr("Keep it", "No, mantenerla") }
        static var cancelled: String { tr("Appointment cancelled", "Cita cancelada") }
        static var markNoShow: String { tr("Didn't come", "No asistió") }
        static var noShowMarked: String { tr("Marked as a no-show", "Marcada como «No asistió»") }
        static var undoNoShow: String { tr("They came", "Sí vino") }
        static var noShowUndone: String { tr("The appointment is confirmed again", "La cita vuelve a estar confirmada") }
        static var viewCustomer: String { tr("View customer", "Ver cliente") }
        static var rescheduled: String { tr("Appointment updated", "Cita actualizada") }
        static var location: String { tr("Location", "Local") }
        static var gone: String { tr("This appointment is no longer in the calendar.", "Esta cita ya no está en la agenda.") }
    }

    enum Checkout {
        static var charge: String { tr("Charge", "Cobrar") }
        static func chargeAmount(_ amount: String) -> String { tr("Charge \(amount)", "Cobrar \(amount)") }
        static var closeSale: String { tr("Close sale", "Cerrar venta") }
        static var deposit: String { tr("Deposit", "Abono") }
        static var viewSale: String { tr("View sale", "Ver venta") }
        static var paid: String { tr("Paid", "Pagada") }
        static func paidIn(_ number: Int) -> String { tr("Sale #\(number)", "Venta #\(number)") }
        static var unpaid: String { tr("Not charged", "Sin cobrar") }
        static func dueLeft(_ amount: String) -> String { tr("\(amount) left to charge", "Faltan \(amount) por cobrar") }
        static func depositLeft(_ amount: String) -> String { tr("Left a \(amount) deposit", "Abonó \(amount)") }
        static var newSale: String { tr("New sale", "Nueva venta") }
        static func titleAppointment(_ name: String) -> String { tr("Charge \(name)", "Cobrar a \(name)") }
        static var customer: String { tr("Customer", "Cliente") }
        static var noCustomer: String { tr("No customer", "Sin cliente") }
        static var pickCustomer: String { tr("Pick a customer", "Elegir cliente") }
        static var items: String { tr("Services and products", "Servicios y productos") }
        static var addService: String { tr("Add service", "Agregar servicio") }
        static var addProduct: String { tr("Add product", "Agregar producto") }
        static var noLines: String { tr("Add at least one service or product.", "Agrega al menos un servicio o producto.") }
        static var nobody: String { tr("No staff", "Sin profesional") }
        static var quantity: String { tr("Quantity", "Cantidad") }
        static var unitPrice: String { tr("Price", "Precio") }
        static var tips: String { tr("Tips", "Propinas") }
        static var addTip: String { tr("Add tip", "Agregar propina") }
        static var tipsHint: String {
            tr("If the customer leaves a tip, note it for whoever served them.",
               "Si el cliente deja propina, anótala para el profesional que lo atendió.")
        }
        static var note: String { tr("Note", "Nota") }
        static var internalSale: String { tr("Internal sale", "Venta interna") }
        static var internalHint: String {
            tr("For the team or the business's own use. Shown separately in the cash register.",
               "Para el equipo o para uso del negocio. Se ve aparte en la caja.")
        }
        static var subtotal: String { "Subtotal" }
        static var discount: String { tr("Discount", "Descuento") }
        static var payments: String { tr("How they paid", "Cómo pagó") }
        static var addPayment: String { tr("Add payment method", "Agregar medio de pago") }
        static func useDeposit(_ date: String) -> String { tr("Deposit from \(date)", "Abono del \(date)") }
        static var deposits: String { tr("Deposits", "Abonos") }
        static var toCollect: String { tr("To collect now", "Por cobrar ahora") }
        static var balanced: String { tr("The payment matches the total.", "El pago cuadra con el total.") }
        static func missing(_ amount: String) -> String { tr("\(amount) missing", "Faltan \(amount)") }
        static func over(_ amount: String) -> String { tr("\(amount) too much", "Sobran \(amount)") }
        static func done(_ number: Int) -> String { tr("Sale #\(number) recorded", "Venta #\(number) registrada") }
        static var code: String { tr("Code or gift card", "Código o gift card") }
        static var apply: String { tr("Apply", "Aplicar") }
        static var removeCode: String { tr("Remove code", "Quitar código") }
        static func codeState(_ state: String) -> String {
            tr("That gift card can't be used: \(state).", "Esa gift card no se puede usar: \(state).")
        }
        static func cardLeft(_ amount: String) -> String { tr("\(amount) left on it", "Le quedan \(amount)") }
        static var paymentsMismatch: String { tr("What was paid doesn't match the total.", "Lo pagado no cuadra con el total.") }
        static var depositTitle: String { tr("Record a deposit", "Registrar abono") }
        static var depositHint: String {
            tr("Money the customer leaves in advance. It's taken off when you charge their appointment.",
               "El dinero que el cliente deja por adelantado. Se descuenta cuando cobras su cita.")
        }
        static var depositSave: String { tr("Save deposit", "Guardar abono") }
        static var depositCreated: String { tr("Deposit recorded", "Abono registrado") }
        static var amount: String { tr("Amount", "Monto") }
        static var amountRequired: String { tr("Enter the amount.", "Escribe el monto.") }
        static var method: String { tr("Payment method", "Medio de pago") }
    }

    enum Payments {
        static func method(_ raw: String) -> String {
            switch raw {
            case "cash": return tr("Cash", "Efectivo")
            case "card": return tr("Card", "Tarjeta")
            case "transfer": return tr("Bank transfer", "Transferencia")
            case "other": return tr("Other", "Otro")
            case "deposit": return tr("Deposit", "Abono")
            case "gift_card": return "Gift card"
            case "online": return tr("Online", "En línea")
            default: return raw
            }
        }

        static func symbol(_ raw: String) -> String {
            switch raw {
            case "cash": return "banknote"
            case "card": return "creditcard"
            case "transfer": return "building.columns"
            case "deposit": return "tray.and.arrow.down"
            case "gift_card": return "giftcard"
            case "online": return "globe"
            default: return "ellipsis.circle"
            }
        }
    }

    enum Booking {
        static var title: String { tr("New appointment", "Nueva cita") }
        static var hint: String {
            tr("Book an appointment yourself: by phone, WhatsApp or at the counter.",
               "Agenda una cita tú mismo: por teléfono, por WhatsApp o en el local.")
        }
        static var customer: String { tr("Customer", "Cliente") }
        static var existing: String { tr("Search", "Buscar") }
        static var newCustomer: String { tr("New", "Nuevo") }
        static var searchCustomer: String { tr("Name, phone or email…", "Nombre, celular o email…") }
        static var typeToSearch: String { tr("Type at least 2 letters to search.", "Escribe al menos 2 letras para buscar.") }
        static var noCustomerFound: String { tr("We couldn't find that customer.", "No encontramos ese cliente.") }
        static var createCustomer: String { tr("Create them", "Crearlo") }
        static var changeCustomer: String { tr("Pick another customer", "Elegir otro cliente") }
        static var change: String { tr("Change", "Cambiar") }
        static var pickCustomer: String { tr("Pick a customer or create a new one.", "Elige un cliente o crea uno nuevo.") }
        static var firstName: String { tr("First name", "Nombre") }
        static var lastName: String { tr("Last name", "Apellido") }
        static var contactHint: String { tr("Leave at least a phone number or an email.", "Deja al menos un celular o un email.") }
        static var service: String { tr("Service", "Servicio") }
        static var pickService: String { tr("Pick a service", "Elige un servicio") }
        static var professional: String { tr("Staff member", "Profesional") }
        static var anyFree: String { tr("Whoever is free", "Quien esté libre") }
        static var location: String { tr("Location", "Local") }
        static var date: String { tr("Date", "Fecha") }
        static var freeTimes: String { tr("Free times", "Horarios libres") }
        static var noFreeTimes: String {
            tr("No free times that day for this choice. Try another day or staff member.",
               "No hay horarios libres ese día con esta combinación. Prueba otro día u otro profesional.")
        }
        static var pickTime: String { tr("Pick a free time.", "Elige un horario libre.") }
        static func bookedForYou(_ name: String) -> String {
            tr("The appointment is booked with you (\(name)).", "La cita queda a tu nombre (\(name)).")
        }
        static var create: String { tr("Book appointment", "Agendar cita") }
        static var created: String { tr("Appointment booked", "Cita agendada") }
        static func alreadyBooked(_ date: String, _ code: String) -> String {
            tr("That customer already has an appointment on \(date) (code \(code)).",
               "Ese cliente ya tiene una cita el \(date) (código \(code)).")
        }
        static var slotTaken: String {
            tr("That time was just taken. Pick another time or someone who's still free.",
               "Ese horario se acaba de ocupar. Elige otro horario u otra persona que esté libre.")
        }
        static var currentTime: String { tr("Current time", "Hora actual") }
        static func rescheduleTitle(_ name: String) -> String { tr("Edit \(name)'s appointment", "Editar la cita de \(name)") }
        static func rescheduleHint(_ code: String) -> String {
            tr("Change the day, time, staff member or service. Code \(code).",
               "Cambia el día, la hora, el profesional o el servicio. Código \(code).")
        }
    }

    enum Customers {
        static var title: String { tr("Customers", "Clientes") }
        static var search: String { tr("Search by name, email or phone", "Buscar por nombre, email o celular") }
        static var new: String { tr("New customer", "Nuevo cliente") }
        static var edit: String { tr("Edit customer", "Editar cliente") }
        static var emptyTitle: String { tr("No customers yet", "Aún no tienes clientes") }
        static var emptyBody: String {
            tr("They show up here with their first booking, or you can add them yourself.",
               "Aparecen aquí con su primera reserva, o puedes agregarlos tú.")
        }
        static var noResults: String { tr("No customer matches your search.", "Ningún cliente coincide con tu búsqueda.") }
        static func count(_ n: Int) -> String { n == 1 ? tr("1 customer", "1 cliente") : tr("\(n) customers", "\(n) clientes") }
        static func since(_ date: String) -> String { tr("Customer since \(date)", "Cliente desde \(date)") }
        static var visits: String { tr("Visits", "Citas") }
        static var lastVisit: String { tr("Last visit", "Última visita") }
        static var nextVisit: String { tr("Next visit", "Próxima cita") }
        static var never: String { "—" }
        static var contact: String { tr("Contact", "Contacto") }
        static var notes: String { tr("Notes", "Notas") }
        static var notesHint: String {
            tr("Only your team sees them. For example: \"prefers a low fade\".",
               "Solo las ve tu equipo. Por ejemplo: «prefiere degradado bajo».")
        }
        static var upcoming: String { tr("Upcoming", "Próximas") }
        static var history: String { tr("History", "Historial") }
        static var noHistory: String { tr("No appointments yet.", "Todavía no tiene citas.") }
        static var attended: String { tr("Attended", "Atendida") }
        static var created: String { tr("Customer added", "Cliente agregado") }
        static var moreDetails: String { tr("More details", "Más datos") }
        static var birthDate: String { tr("Date of birth", "Fecha de nacimiento") }
        static var document: String { tr("ID number", "Documento") }
        static var gender: String { tr("Gender", "Género") }
        static func genderName(_ raw: String?) -> String {
            switch raw {
            case "female": return tr("Female", "Femenino")
            case "male": return tr("Male", "Masculino")
            case "other": return tr("Other", "Otro")
            default: return tr("Not set", "Sin definir")
            }
        }
        static var bookFor: String { tr("Book", "Agendar") }
        static var spent: String { tr("Total spent", "Total gastado") }
        static var visitsDone: String { tr("Visits", "Visitas") }
    }

    enum Sales {
        static var title: String { tr("Sales", "Ventas") }
        static var salesTab: String { tr("Sales", "Ventas") }
        static var depositsTab: String { tr("Deposits", "Abonos") }
        static var empty: String { tr("No sales on this day.", "No hay ventas este día.") }
        static var emptyDeposits: String { tr("No deposits on this day.", "No hay abonos este día.") }
        static var collected: String { tr("Collected", "Recaudado") }
        static func count(_ n: Int) -> String { n == 1 ? tr("1 sale", "1 venta") : tr("\(n) sales", "\(n) ventas") }
        static var tipsShort: String { tr("tips", "propinas") }
        static func average(_ amount: String) -> String { tr("Average \(amount)", "Promedio \(amount)") }
        static var byMethod: String { tr("By payment method", "Por medio de pago") }
        static var paid: String { tr("Paid", "Pagada") }
        static var void: String { tr("Voided", "Anulada") }
        static var `internal`: String { tr("Internal", "Interna") }
        static func saleTitle(_ number: Int) -> String { tr("Sale #\(number)", "Venta #\(number)") }
        static var soldBy: String { tr("Recorded by", "Registrada por") }
        static func tipFor(_ name: String) -> String { tr("Tip for \(name)", "Propina para \(name)") }
        static var voidAction: String { tr("Void sale", "Anular venta") }
        static func voidTitle(_ number: Int) -> String { tr("Void sale #\(number)?", "¿Anular la venta #\(number)?") }
        static var voidBody: String {
            tr("It stays in the history as voided and stops counting in the cash register and reports. If a deposit paid for it, the deposit becomes available again.",
               "Queda en el historial como anulada y deja de contar en la caja y los reportes. Si se pagó con un abono, el abono vuelve a quedar disponible.")
        }
        static var voided: String { tr("Sale voided", "Venta anulada") }
        static var items: String { tr("Items", "Detalle") }
        static var depositAvailable: String { tr("Available", "Disponible") }
        static func depositUsed(_ number: Int) -> String { tr("Used in sale #\(number)", "Usado en venta #\(number)") }
        static var depositReturned: String { tr("Returned", "Devuelto") }
        static func forAppointment(_ code: String) -> String { tr("Appointment \(code)", "Cita \(code)") }
    }

    enum More {
        static var title: String { tr("More", "Más") }
        static var business: String { tr("Business", "Negocio") }
        static var businessInfo: String { tr("Business details", "Datos del negocio") }
        static var bookingPage: String { tr("Booking page", "Página de reservas") }
        static var hours: String { tr("Opening hours", "Horario del negocio") }
        static var services: String { tr("Services", "Servicios") }
        static var professionals: String { tr("Staff", "Profesionales") }
        static var team: String { tr("Team access", "Equipo") }
        static var money: String { tr("Money", "Dinero") }
        static var cash: String { tr("Cash register", "Caja") }
        static var inventory: String { tr("Inventory", "Inventario") }
        static var giftCards: String { "Gift cards" }
        static var reports: String { tr("Reports", "Reportes") }
        static var account: String { tr("My account", "Mi cuenta") }
        static var signOut: String { tr("Sign out", "Cerrar sesión") }
        static var signOutTitle: String { tr("Sign out of this phone?", "¿Cerrar sesión en este teléfono?") }
        static func version(_ value: String) -> String { tr("Version \(value)", "Versión \(value)") }
        static var webOnly: String {
            tr("Settings, automations and integrations are in the web panel.",
               "La configuración, los flujos y las integraciones están en el panel web.")
        }
    }

    enum Services {
        static var title: String { tr("Services", "Servicios") }
        static var new: String { tr("New service", "Nuevo servicio") }
        static var edit: String { tr("Edit service", "Editar servicio") }
        static var emptyTitle: String { tr("No services yet", "Aún no tienes servicios") }
        static var emptyBody: String {
            tr("Create the first one, for example \"Classic cut, 30 min\".", "Crea el primero, por ejemplo «Corte clásico, 30 min».")
        }
        static var name: String { tr("Name", "Nombre") }
        static var namePlaceholder: String { tr("Classic cut", "Corte clásico") }
        static var category: String { tr("Category", "Categoría") }
        static var categoryPlaceholder: String { tr("E.g. Cuts, Beard, Color", "Ej. Cortes, Barba, Color") }
        static var uncategorized: String { tr("Other", "Otros") }
        static var duration: String { tr("Duration", "Duración") }
        static var price: String { tr("Price", "Precio") }
        static var description: String { tr("Description", "Descripción") }
        static var descriptionPlaceholder: String { tr("What it includes, who it's for…", "Qué incluye, para quién es…") }
        static var visible: String { tr("Visible", "Visible") }
        static var hidden: String { tr("Hidden", "Oculto") }
        static var active: String { tr("Visible on the booking page", "Visible en la página de reservas") }
        static var activeHint: String { tr("If you turn it off, customers can't book it.", "Si lo apagas, tus clientes no podrán reservarlo.") }
        static var created: String { tr("Service created", "Servicio creado") }
        static var onlinePayment: String { tr("Online payment", "Cobro en línea") }
        static func paymentMode(_ raw: String?) -> String {
            switch raw {
            case "optional": return tr("Optional", "Opcional")
            case "deposit": return tr("Deposit", "Abono")
            case "full": return tr("Full payment", "Pago completo")
            default: return tr("None", "Sin cobro")
            }
        }
        static var priceRequired: String { tr("Enter a price.", "Escribe un precio.") }
        static var nameRequired: String { tr("Enter a name.", "Escribe un nombre.") }
    }

    enum Professionals {
        static var title: String { tr("Staff", "Profesionales") }
        static var edit: String { tr("Edit staff member", "Editar profesional") }
        static var emptyTitle: String { tr("No staff yet", "Aún no tienes profesionales") }
        static var emptyBody: String {
            tr("Customers book straight with the business. Add staff from the web panel if several people serve at once.",
               "Tus clientes reservan directamente con el negocio. Agrega profesionales desde el panel web si atienden varias personas a la vez.")
        }
        static func servicesCount(_ n: Int) -> String { n == 1 ? tr("1 service", "1 servicio") : tr("\(n) services", "\(n) servicios") }
        static var ownHours: String { tr("Own hours", "Horario propio") }
        static var businessHours: String { tr("Business hours", "Horario del negocio") }
        static var available: String { tr("Available", "Disponible") }
        static var unavailable: String { tr("Unavailable", "No disponible") }
        static var active: String { tr("Available for bookings", "Disponible para reservas") }
        static var activeHint: String {
            tr("Turn it off during long holidays or if they no longer work with you.",
               "Apágalo durante vacaciones largas o si ya no trabaja contigo.")
        }
        static var services: String { tr("Services they do", "Servicios que realiza") }
        static var serviceCommission: String { tr("Service commission", "Comisión por servicios") }
        static var productCommission: String { tr("Product commission", "Comisión por productos") }
        static var weeklyHours: String { tr("Weekly hours", "Horario semanal") }
        static var usesBusinessHours: String {
            tr("Works the location's opening hours.", "Atiende en el horario del local.")
        }
        static var you: String { tr("You", "Tú") }
    }

    enum Hours {
        static var title: String { tr("Opening hours", "Horario del negocio") }
        static var closed: String { tr("Closed", "Cerrado") }
        static var addRange: String { tr("Add hours", "Agregar horario") }
        static var from: String { tr("From", "Desde") }
        static var to: String { tr("To", "Hasta") }
        static var saved: String { tr("Hours saved", "Horario guardado") }
        static var holidays: String { tr("Public holidays", "Festivos") }
        static var closedHoliday: String { tr("Closed", "Cerrado") }
        static var openHoliday: String { tr("Open", "Abierto") }
        static var closures: String { tr("Special closures", "Cierres especiales") }
        static var noClosures: String { tr("No closures ahead.", "No hay cierres próximos.") }
        static var bookingRules: String { tr("Online bookings", "Reservas en línea") }
        static var slotInterval: String { tr("Time between slots", "Intervalo entre horarios") }
        static var minNotice: String { tr("Minimum notice", "Anticipación mínima") }
        static var maxAdvance: String { tr("Booking window", "Ventana de reservas") }
        static func days(_ n: Int) -> String { n == 1 ? tr("1 day", "1 día") : tr("\(n) days", "\(n) días") }
        static var overlap: String { tr("Some time ranges overlap on the same day.", "Hay horarios que se cruzan en el mismo día.") }
        static var endBeforeStart: String { tr("A time range ends before it starts.", "Hay un horario que termina antes de empezar.") }
    }

    enum Info {
        static var title: String { tr("Business details", "Datos del negocio") }
        static var name: String { tr("Business name", "Nombre del negocio") }
        static var contact: String { tr("Contact", "Contacto") }
        static var phone: String { tr("Phone", "Teléfono") }
        static var email: String { tr("Contact email", "Email de contacto") }
        static var address: String { tr("Address", "Dirección") }
        static var description: String { tr("Description", "Descripción") }
        static var bookingLink: String { tr("Booking page", "Página de reservas") }
        static var bookingHint: String {
            tr("Share it so your customers book on their own, any time.",
               "Compártela para que tus clientes reserven solos, a cualquier hora.")
        }
        static var qr: String { tr("QR code", "Código QR") }
        static var qrHint: String {
            tr("Print it for the counter or the mirror: customers scan it and book.",
               "Imprímelo para el mostrador o el espejo: tus clientes lo escanean y reservan.")
        }
        static var shareQR: String { tr("Share QR", "Compartir QR") }
        static var openPage: String { tr("Open page", "Abrir página") }
        static var region: String { tr("Region", "Región") }
        static var timezone: String { tr("Time zone", "Zona horaria") }
        static var currency: String { tr("Currency", "Moneda") }
        static func shareText(_ name: String) -> String { tr("Book with \(name):", "Reserva en \(name):") }
    }

    enum Reports {
        static var title: String { tr("Reports", "Reportes") }
        static var summary: String { tr("Summary", "Resumen") }
        static var commissions: String { tr("Commissions", "Comisiones") }
        static var bookings: String { tr("Bookings", "Reservas") }
        static var occupancy: String { tr("Occupancy", "Ocupación") }
        static var newCustomers: String { tr("New customers", "Clientes nuevos") }
        static var billed: String { tr("Sales", "Ventas") }
        static func vs(_ value: String) -> String { tr("vs. \(value) before", "vs. \(value) antes") }
        static func chart(_ unit: String) -> String {
            switch unit {
            case "week": return tr("Bookings per week", "Reservas por semana")
            case "month": return tr("Bookings per month", "Reservas por mes")
            default: return tr("Bookings per day", "Reservas por día")
            }
        }
        static func rateOf(_ rate: String, _ amount: String) -> String { tr("\(rate) % of \(amount)", "\(rate) % de \(amount)") }
        static var origin: String { tr("Where bookings come from", "Cómo llegan tus reservas") }
        static var originOnline: String { tr("Booking page", "Página de reservas") }
        static var originAdmin: String { tr("Your team, from the calendar", "Tu equipo, desde la agenda") }
        static var originAgent: String { tr("AI assistant", "Asistente de IA") }
        static var topSold: String { tr("Best sellers", "Lo más vendido") }
        static func soldCount(_ n: Int) -> String { n == 1 ? tr("1 sold", "1 vendido") : tr("\(n) sold", "\(n) vendidos") }
        static var byProfessional: String { tr("Appointments by staff member", "Citas por profesional") }
        static var noProfessional: String { tr("No staff member", "Sin profesional") }
        static var today: String { tr("Today's appointments", "Citas de hoy") }
        static var noneToday: String { tr("No appointments today.", "Hoy no hay citas.") }
        static var nothing: String { tr("No data for these dates yet.", "Aún no hay datos en estas fechas.") }
        static var services: String { tr("Services", "Servicios") }
        static var products: String { tr("Products", "Productos") }
        static var tips: String { tr("Tips", "Propinas") }
        static var toPay: String { tr("Total to pay", "Total a pagar") }
        static var paid: String { tr("Paid", "Pagada") }
    }

    enum Account {
        static var title: String { tr("My account", "Mi cuenta") }
        static var details: String { tr("Your details", "Tus datos") }
        static var currentPassword: String { tr("Current password", "Contraseña actual") }
        static var emailNeedsPassword: String {
            tr("To change the email you sign in with, confirm your password.",
               "Para cambiar el email con el que entras, confirma tu contraseña.")
        }
        static var password: String { tr("Password", "Contraseña") }
        static var passwordHint: String {
            tr("Changing it signs you out on your other devices.", "Al cambiarla se cierra tu sesión en los demás dispositivos.")
        }
        static var newPassword: String { tr("New password", "Nueva contraseña") }
        static var newPasswordHint: String { tr("At least 10 characters.", "Mínimo 10 caracteres.") }
        static var changePassword: String { tr("Change password", "Cambiar contraseña") }
        static var passwordChanged: String { tr("Password changed", "Contraseña cambiada") }
        static var server: String { tr("Server", "Servidor") }
        static var role: String { tr("Role", "Rol") }
        static var installOwner: String { tr("Administrator", "Administrador") }
        static var appearance: String { tr("Appearance follows your phone's light or dark mode.", "La apariencia sigue el modo claro u oscuro de tu teléfono.") }
    }

    enum Cash {
        static var title: String { tr("Cash register", "Caja") }
        static var inDrawer: String { tr("Cash in the drawer", "Efectivo en caja") }
        static var flows: String { tr("Money in and out", "Entradas y salidas") }
        static var sales: String { tr("Sales", "Ventas") }
        static var deposits: String { tr("Deposits", "Abonos") }
        static var `internal`: String { tr("Internal sales", "Ventas internas") }
        static var giftCards: String { "Gift cards" }
        static var otherIncome: String { tr("Other income", "Otros ingresos") }
        static var expenses: String { tr("Expenses", "Egresos") }
        static var result: String { tr("Result", "Resultado") }
        static var movements: String { tr("Movements", "Movimientos") }
        static var noMovements: String { tr("No movements in this period.", "No hay movimientos en este periodo.") }
        static var income: String { tr("Income", "Ingreso") }
        static var expense: String { tr("Expense", "Egreso") }
        static var newMovement: String { tr("New movement", "Nuevo movimiento") }
        static var category: String { tr("Category", "Categoría") }
        static var saved: String { tr("Movement recorded", "Movimiento registrado") }
        static func kind(_ raw: String) -> String {
            switch raw {
            case "income": return income
            case "expense": return expense
            case "transfer_out": return tr("Transfer (out)", "Traspaso (sale)")
            case "transfer_in": return tr("Transfer (in)", "Traspaso (entra)")
            case "adjustment": return tr("Cash count", "Cuadre de caja")
            default: return raw
            }
        }
    }

    enum Inventory {
        static var title: String { tr("Inventory", "Inventario") }
        static var emptyTitle: String { tr("No products yet", "Aún no tienes productos") }
        static var emptyBody: String {
            tr("Add the products you sell or use from the web panel.", "Agrega desde el panel web los productos que vendes o usas.")
        }
        static var lowStock: String { tr("Low stock", "Stock bajo") }
        static func inStock(_ n: Int) -> String { tr("\(n) in stock", "\(n) en stock") }
        static var notForSale: String { tr("Not for sale", "No se vende") }
        static var price: String { tr("Price", "Precio") }
        static var internalPrice: String { tr("Price for the team", "Precio para el equipo") }
        static var cost: String { tr("Cost", "Costo") }
        static var stock: String { tr("Stock", "Stock") }
        static var alertAt: String { tr("Alert at", "Alerta en") }
        static var sku: String { "SKU" }
        static var taxIncluded: String { tr("Includes VAT", "Incluye IVA") }
    }

    enum GiftCards {
        static var title: String { "Gift cards" }
        static var emptyTitle: String { tr("No gift cards or codes yet", "Aún no tienes gift cards ni códigos") }
        static var emptyBody: String {
            tr("Create discount codes and gift cards from the web panel.", "Crea códigos de descuento y gift cards desde el panel web.")
        }
        static var discount: String { tr("Discount code", "Código de descuento") }
        static var balance: String { tr("Gift card with balance", "Gift card con saldo") }
        static func state(_ raw: String) -> String {
            switch raw {
            case "active": return tr("Active", "Activa")
            case "inactive": return tr("Deactivated", "Desactivada")
            case "scheduled": return tr("Scheduled", "Programada")
            case "expired": return tr("Expired", "Vencida")
            case "used_up": return tr("No uses left", "Sin usos")
            case "spent": return tr("No balance left", "Sin saldo")
            default: return raw
            }
        }
        static func uses(_ used: Int, _ max: Int?) -> String {
            if let max { return tr("\(used) of \(max) uses", "\(used) de \(max) usos") }
            return used == 1 ? tr("1 use", "1 uso") : tr("\(used) uses", "\(used) usos")
        }
        static func left(_ amount: String) -> String { tr("\(amount) left", "Quedan \(amount)") }
        static func until(_ date: String) -> String { tr("Until \(date)", "Hasta el \(date)") }
        static var activate: String { tr("Activate", "Activar") }
        static var deactivate: String { tr("Deactivate", "Desactivar") }
    }

    enum Team {
        static var title: String { tr("Team access", "Equipo") }
        static var hint: String {
            tr("People who can sign in to this business. Staff members only see their own calendar.",
               "Las personas que pueden entrar a este negocio. Los profesionales solo ven su propia agenda.")
        }
        static var members: String { tr("People with access", "Personas con acceso") }
        static var invitations: String { tr("Pending invitations", "Invitaciones pendientes") }
        static var invite: String { tr("Invite by email", "Invitar por email") }
        static var inviteHint: String {
            tr("If the email already has an account, they get access right away as an admin. Otherwise you get a link to send them, valid for 7 days.",
               "Si el email ya tiene cuenta, recibe acceso de inmediato como administrador. Si no, te damos un enlace para enviarle, válido por 7 días.")
        }
        static var send: String { tr("Invite", "Invitar") }
        static var added: String { tr("They have access now", "Ya tiene acceso") }
        static var invited: String { tr("Invitation created", "Invitación creada") }
        static var inviteLink: String { tr("Invitation link", "Enlace de invitación") }
        static var shareLink: String { tr("Share the link", "Compartir el enlace") }
        static var linkOnce: String {
            tr("Send it now: it's shown only once.", "Envíalo ahora: solo se muestra una vez.")
        }
        static func expires(_ date: String) -> String { tr("Expires \(date)", "Vence el \(date)") }
        static var revoke: String { tr("Revoke", "Revocar") }
        static var empty: String { tr("Only you have access for now.", "Por ahora solo tú tienes acceso.") }
        static func linked(_ name: String) -> String { tr("Profile: \(name)", "Perfil: \(name)") }
        static var invalidEmail: String { tr("Enter a valid email.", "Escribe un email válido.") }
    }

    enum Errors {
        static var generic: String { tr("Something went wrong. Please try again.", "Algo salió mal. Inténtalo de nuevo.") }
        static var network: String { tr("Can't reach the server. Check your connection.", "No hay conexión con el servidor. Revisa tu conexión.") }
    }
}
