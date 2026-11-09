package org.openturnly.mobile.localization

import org.openturnly.mobile.rules.Appointments

object AgendaCopy {
    val title get() = tr("Agenda", "Agenda")
    val allProfessionals get() = tr("Everyone", "Todos")
    val showCancelled get() = tr("Cancelled", "Canceladas")
    val showCancelledLong get() = tr("Show cancelled", "Mostrar canceladas")
    val filters get() = tr("Filters", "Filtros")
    val professional get() = tr("Staff member", "Profesional")
    val location get() = tr("Location", "Local")
    val clearFilters get() = tr("Clear filters", "Quitar filtros")
    val searchStaff get() = tr("Search staff", "Buscar profesional")
    val emptyTitle get() = tr("No appointments this day", "No hay citas este día")
    val emptyBody get() = tr("A free day. Book one with the + button.", "Un día libre. Agenda una con el botón +.")
    val emptyFiltered get() = tr("Nobody matches these filters.", "Nadie coincide con estos filtros.")
    val now get() = tr("Now", "Ahora")
    val loadFailed get() = tr("Could not load the agenda", "No pudimos cargar la agenda")
    val newAppointment get() = tr("New appointment", "Nueva cita")
    val pickDay get() = tr("Go to a day", "Ir a un día")
    fun summary(count: Int) = if (AppLocale.isSpanish) (if (count == 1) "1 cita" else "$count citas") else (if (count == 1) "1 appointment" else "$count appointments")
    fun toCharge(count: Int) = if (AppLocale.isSpanish) "$count por cobrar" else "$count to charge"

    fun segment(segment: Appointments.Segment) = when (segment) {
        Appointments.Segment.MORNING -> tr("Morning", "Mañana")
        Appointments.Segment.AFTERNOON -> tr("Afternoon", "Tarde")
        Appointments.Segment.EVENING -> tr("Evening", "Noche")
    }

    fun status(status: Appointments.Status) = when (status) {
        Appointments.Status.UPCOMING -> tr("Upcoming", "Próxima")
        Appointments.Status.CONFIRMED -> tr("Confirmed", "Confirmada")
        Appointments.Status.CANCELLED -> tr("Cancelled", "Cancelada")
        Appointments.Status.NO_SHOW -> tr("No-show", "No asistió")
        Appointments.Status.PENDING_PAYMENT -> tr("Awaiting payment", "Pendiente de pago")
    }

    /** For a stored status in a customer's history. */
    fun status(raw: String, started: Boolean) = when (raw) {
        "cancelled" -> status(Appointments.Status.CANCELLED)
        "no_show" -> status(Appointments.Status.NO_SHOW)
        "pending_payment" -> status(Appointments.Status.PENDING_PAYMENT)
        else -> status(if (started) Appointments.Status.CONFIRMED else Appointments.Status.UPCOMING)
    }

    val pendingPaymentHint get() = tr(
        "The time is held while the customer pays online. If they don't pay, it frees itself.",
        "El horario está apartado mientras el cliente paga en línea. Si no paga, se libera solo.",
    )

    // Detail
    val detailTitle get() = tr("Appointment", "Cita")
    val bookingCode get() = tr("Booking code", "Código de reserva")
    val bookedVia get() = tr("Booked via", "Agendada por")
    val bookedOn get() = tr("Booked on", "Reservada el")
    fun source(source: String, label: String?) = when (source) {
        "online" -> tr("Booking page", "Página de reservas")
        "agent" -> tr("AI agent", "Agente IA") + (label?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        else -> tr("Admin", "Panel")
    }
    val customer get() = tr("Customer", "Cliente")
    val viewCustomer get() = tr("View customer", "Ver cliente")
    val customerNotes get() = tr("Customer notes", "Notas del cliente")
    val customerNotesHint get() = tr("Private: only your team sees them.", "Privadas: solo las ve tu equipo.")
    val noNotes get() = tr("No notes yet.", "Sin notas todavía.")
    val addNotes get() = tr("Add notes", "Agregar notas")
    val notesSaved get() = tr("Notes saved", "Notas guardadas")
    val payment get() = tr("Payment", "Pago")
    val discountCode get() = tr("Discount code", "Código de descuento")
    val answers get() = tr("Booking form", "Formulario de reserva")
    val actions get() = tr("Actions", "Acciones")

    // Actions (the web's labels)
    val reschedule get() = tr("Edit appointment", "Editar cita")
    val cancelAppointment get() = tr("Cancel appointment", "Cancelar cita")
    val cancelTitle get() = tr("Cancel this appointment?", "¿Cancelar esta cita?")
    val confirmCancel get() = tr("The time becomes available again for other customers.", "El horario quedará disponible de nuevo para otros clientes.")
    val keep get() = tr("Keep it", "No, mantenerla")
    val cancelled get() = tr("Appointment cancelled", "Cita cancelada")
    val markNoShow get() = tr("No-show", "No asistió")
    val markNoShowLong get() = tr("Mark as no-show", "Marcar «No asistió»")
    val noShowMarked get() = tr("Marked as no-show", "Marcada como «No asistió»")
    val undoNoShow get() = tr("They came", "Sí vino")
    val noShowUndone get() = tr("The appointment is confirmed again", "La cita vuelve a estar confirmada")
    val noShowTitle get() = tr("Mark as no-show?", "¿Marcar como «No asistió»?")
    val noShowBody get() = tr("The time is freed. You can undo it with \"They came\".", "El horario queda libre. Puedes deshacerlo con «Sí vino».")

    // Payment states (the web's checkout copy)
    val unpaid get() = tr("Not charged", "Sin cobrar")
    val paid get() = tr("Paid", "Pagada")
    fun saleNumber(number: Int) = tr("Sale #$number", "Venta #$number")
    fun dueLeft(amount: String) = tr("$amount left to charge", "Faltan $amount por cobrar")
    fun depositLeft(amount: String) = tr("Left a $amount deposit", "Abonó $amount")
    fun deposited(amount: String) = tr("Deposit $amount", "Abono $amount")
    val charge get() = tr("Charge", "Cobrar")
    val closeSale get() = tr("Close sale", "Cerrar venta")
    val viewSale get() = tr("View sale", "Ver venta")
    val newDeposit get() = tr("Record deposit", "Registrar abono")
}

object BookingCopy {
    val title get() = tr("New appointment", "Nueva cita")
    val hint get() = tr("Book it yourself: by phone, WhatsApp or at the counter.", "Agenda una cita tú mismo: por teléfono, por WhatsApp o en el local.")
    val customer get() = tr("Customer", "Cliente")
    val existing get() = tr("Search", "Buscar")
    val newCustomer get() = tr("New", "Nuevo")
    val searchCustomer get() = tr("Name, mobile or email…", "Nombre, celular o email…")
    val searchHint get() = tr("Type at least two letters.", "Escribe al menos dos letras.")
    val noCustomerFound get() = tr("We couldn't find that customer.", "No encontramos ese cliente.")
    val createCustomer get() = tr("Create them", "Crearlo")
    val changeCustomer get() = tr("Choose another customer", "Elegir otro cliente")
    val pickCustomer get() = tr("Choose a customer or create a new one.", "Elige un cliente o crea uno nuevo.")
    val contactHint get() = tr("Leave at least a mobile or an email. The mobile goes with its country code.", "Deja al menos un celular o un email. El celular va con su código de país.")
    val service get() = tr("Service", "Servicio")
    val pickService get() = tr("Choose a service", "Elige un servicio")
    val professional get() = tr("Staff member", "Profesional")
    val anyFree get() = tr("Whoever is free", "Quien esté libre")
    fun bookedForYou(name: String) = tr("The appointment is yours ($name).", "La cita queda a tu nombre ($name).")
    val date get() = tr("Day", "Día")
    val freeTimes get() = tr("Free times", "Horarios libres")
    val noFreeTimes get() = tr("No free times that day for this choice. Try another day or staff member.", "No hay horarios libres ese día con esta combinación. Prueba otro día u otro profesional.")
    val pickTime get() = tr("Choose a free time.", "Elige un horario libre.")
    val pickServiceFirst get() = tr("Choose a service to see the free times.", "Elige un servicio para ver los horarios libres.")
    val create get() = tr("Book appointment", "Agendar cita")
    val created get() = tr("Appointment booked", "Cita agendada")
    fun alreadyBooked(date: String, code: String) = tr("That customer already has an appointment on $date (code $code).", "Ese cliente ya tiene una cita el $date (código $code).")
    val slotTaken get() = tr("Someone just took that time. Pick another one.", "Alguien acaba de tomar ese horario. Elige otro.")
    val rescheduleTitle get() = tr("Edit appointment", "Editar cita")
    fun rescheduleHint(code: String) = tr("Change the day, time, staff member or service. Code $code.", "Cambia el día, la hora, el profesional o el servicio. Código $code.")
    val currentTime get() = tr("Current time", "Hora actual de la cita")
    val rescheduled get() = tr("Appointment updated", "Cita actualizada")
    val morning get() = tr("Morning", "Mañana")
    val afternoon get() = tr("Afternoon", "Tarde")
    val evening get() = tr("Evening", "Noche")
    val country get() = tr("Country", "País")
}
