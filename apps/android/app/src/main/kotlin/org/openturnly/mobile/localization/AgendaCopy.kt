package org.openturnly.mobile.localization

import org.openturnly.mobile.rules.Appointments

object AgendaCopy {
    val title get() = tr("Calendar", "Agenda")
    val allProfessionals get() = tr("Everyone", "Todos")
    val showCancelled get() = tr("Cancelled", "Canceladas")
    val showCancelledLong get() = tr("Show cancelled", "Mostrar canceladas")
    val withCancelled get() = tr("With cancelled", "Con canceladas")
    val filters get() = tr("Filters", "Filtros")
    val professional get() = tr("Staff member", "Profesional")
    val location get() = tr("Location", "Local")
    val clearFilters get() = tr("Clear filters", "Quitar filtros")
    val searchStaff get() = tr("Search staff", "Buscar profesional")
    val emptyTitle get() = tr("No appointments this day", "No hay citas este día")
    val emptyFiltered get() = tr("No appointments match these filters.", "Ninguna cita coincide con estos filtros.")
    val now get() = tr("Now", "Ahora")
    val loadFailed get() = tr("Couldn't load the calendar", "No se pudo cargar la agenda")
    val newAppointment get() = tr("New appointment", "Nueva cita")
    val pickDay get() = tr("Go to a date", "Ir a una fecha")
    val previousWeek get() = tr("Previous week", "Semana anterior")
    val nextWeek get() = tr("Next week", "Semana siguiente")
    fun summary(count: Int) = if (AppLocale.isSpanish) (if (count == 1) "1 cita" else "$count citas") else (if (count == 1) "1 appointment" else "$count appointments")
    fun expected(amount: String) = tr("$amount expected", "$amount esperado")
    fun toCharge(count: Int) = if (AppLocale.isSpanish) "$count por cobrar" else "$count to charge"

    fun segment(segment: Appointments.Segment) = when (segment) {
        Appointments.Segment.MORNING -> tr("Morning", "Por la mañana")
        Appointments.Segment.AFTERNOON -> tr("Afternoon", "Por la tarde")
        Appointments.Segment.EVENING -> tr("Evening", "Por la noche")
    }

    fun status(status: Appointments.Status) = when (status) {
        Appointments.Status.UPCOMING -> tr("Upcoming", "Próxima")
        Appointments.Status.NOW -> tr("Now", "Ahora")
        Appointments.Status.CONFIRMED -> tr("Confirmed", "Confirmada")
        Appointments.Status.CANCELLED -> tr("Cancelled", "Cancelada")
        Appointments.Status.NO_SHOW -> tr("No-show", "No asistió")
        Appointments.Status.PENDING_PAYMENT -> tr("Awaiting payment", "Pendiente de pago")
    }

    val pendingPaymentHint get() = tr(
        "The time is held while the customer pays online. If they don't pay, it frees itself.",
        "El horario está apartado mientras el cliente paga en línea. Si no paga, se libera solo.",
    )

    // Detail
    val detailTitle get() = tr("Appointment", "Cita")
    val gone get() = tr("This appointment is no longer in the calendar.", "Esta cita ya no está en la agenda.")
    val noProfessional get() = tr("No staff member", "Sin profesional")
    val bookedVia get() = tr("Booked via", "Agendada por")
    val bookedOn get() = tr("Booked on", "Reservada el")
    fun source(source: String, label: String?) = when (source) {
        "online" -> tr("Booking page", "Página de reservas")
        "agent" -> tr("AI assistant", "Asistente de IA") + (label?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        else -> tr("Your team", "Tu equipo")
    }
    val customer get() = tr("Customer", "Cliente")
    val noContact get() = tr("No contact details", "Sin datos de contacto")
    val viewCustomer get() = tr("View customer", "Ver cliente")
    val customerNotes get() = tr("Customer notes", "Notas del cliente")
    val customerNotesHint get() = tr(
        "Only your team sees them. For example: \"prefers a low fade\".",
        "Solo las ve tu equipo. Por ejemplo: «prefiere degradado bajo».",
    )
    val addNotes get() = tr("Add notes", "Agregar notas")
    val notesSaved get() = tr("Notes saved", "Notas guardadas")
    val payment get() = tr("Payment", "Pago")
    val discount get() = tr("Discount", "Descuento")
    val deposits get() = tr("Deposits", "Abonos")
    val answers get() = tr("Booking answers", "Respuestas de la reserva")
    val details get() = tr("Details", "Detalles")
    val actions get() = tr("Actions", "Acciones")

    // Actions (the web's labels)
    val reschedule get() = tr("Edit appointment", "Editar cita")
    val cancelAppointment get() = tr("Cancel appointment", "Cancelar cita")
    val cancelTitle get() = tr("Cancel this appointment?", "¿Cancelar esta cita?")
    val confirmCancel get() = tr("The time becomes available again for other customers.", "El horario quedará disponible de nuevo para otros clientes.")
    val keep get() = tr("Keep it", "No, mantenerla")
    val cancelled get() = tr("Appointment cancelled", "Cita cancelada")
    val markNoShow get() = tr("Mark as no-show", "Marcar «No asistió»")
    val noShowMarked get() = tr("Marked as no-show", "Marcada como «No asistió»")
    val undoNoShow get() = tr("They came", "Sí vino")
    val noShowUndone get() = tr("The appointment is confirmed again", "La cita vuelve a estar confirmada")
    val noShowTitle get() = tr("Mark as no-show?", "¿Marcar como «No asistió»?")
    val noShowBody get() = tr("The time is freed. You can undo it with \"They came\".", "El horario queda libre. Puedes deshacerlo con «Sí vino».")
    val goBack get() = tr("Go back", "Volver")

    // Payment states (the web's checkout copy)
    val unpaid get() = tr("Not charged", "Sin cobrar")
    val paid get() = tr("Paid", "Pagada")
    val toClose get() = tr("To close", "Por cerrar")
    val coveredHint get() = tr("Deposits cover the price: just close the sale.", "Los abonos cubren el precio: solo falta cerrar la venta.")
    fun saleNumber(number: Int) = tr("Sale #$number", "Venta #$number")
    fun dueLeft(amount: String) = tr("$amount left to charge", "Faltan $amount por cobrar")
    /** On a card once it started: what deposits left to pay. */
    fun left(amount: String) = tr("$amount left", "Faltan $amount")
    fun deposited(amount: String) = tr("Deposit $amount", "Abonó $amount")
    val charge get() = tr("Charge", "Cobrar")
    fun chargeAmount(amount: String) = tr("Charge $amount", "Cobrar $amount")
    val closeSale get() = tr("Close sale", "Cerrar venta")
    val viewSale get() = tr("View sale", "Ver venta")
    val newDeposit get() = tr("Record deposit", "Registrar abono")
}

object BookingCopy {
    val title get() = tr("New appointment", "Nueva cita")
    val customer get() = tr("Customer", "Cliente")
    val existing get() = tr("Search", "Buscar")
    val newCustomer get() = tr("New", "Nuevo")
    val searchCustomer get() = tr("Name, mobile or email…", "Nombre, celular o email…")
    val searchHint get() = tr("Type at least 2 letters to search.", "Escribe al menos 2 letras para buscar.")
    val noCustomerFound get() = tr("We couldn't find that customer.", "No encontramos ese cliente.")
    val createCustomer get() = tr("Create them", "Crearlo")
    val changeCustomer get() = tr("Change", "Cambiar")
    val pickCustomer get() = tr("Choose a customer or create a new one.", "Elige un cliente o crea uno nuevo.")
    val contactHint get() = tr("Leave at least a mobile or an email.", "Deja al menos un celular o un email.")
    val service get() = tr("Service", "Servicio")
    val location get() = tr("Location", "Local")
    val pickService get() = tr("Choose a service", "Elige un servicio")
    val pickServiceError get() = tr("Choose a service.", "Elige un servicio.")
    val professional get() = tr("Staff member", "Profesional")
    val anyFree get() = tr("Whoever is free", "Quien esté libre")
    fun bookedForYou(name: String) = tr("The appointment is booked with you ($name).", "La cita queda a tu nombre ($name).")
    val date get() = tr("Day and time", "Día y hora")
    val freeTimes get() = tr("Free times", "Horarios libres")
    val noFreeTimes get() = tr("No free times that day for this choice. Try another day or staff member.", "No hay horarios libres ese día con esta combinación. Prueba otro día u otro profesional.")
    val pickTime get() = tr("Choose a free time.", "Elige un horario libre.")
    val pickServiceFirst get() = tr("Choose a service to see the free times.", "Elige un servicio para ver los horarios libres.")
    val create get() = tr("Book appointment", "Agendar cita")
    val created get() = tr("Appointment booked", "Cita agendada")
    fun alreadyBooked(day: String, time: String, code: String) = tr(
        "That customer already has an appointment on $day at $time (code $code).",
        "Ese cliente ya tiene una cita el $day a las $time (código $code).",
    )
    val slotTaken get() = tr("Someone just took that time. Pick another one.", "Alguien acaba de tomar ese horario. Elige otro.")
    val rescheduleTitle get() = tr("Edit appointment", "Editar cita")
    fun rescheduleHint(code: String) = tr("Change the day, time, staff member or service. Code $code.", "Cambia el día, la hora, el profesional o el servicio. Código $code.")
    val currentTime get() = tr("Current time", "Hora actual de la cita")
    val rescheduled get() = tr("Appointment updated", "Cita actualizada")
    val country get() = tr("Country", "País")
}
