package org.openturnly.mobile.localization

object CustomersCopy {
    val title get() = tr("Customers", "Clientes")
    val search get() = tr("Search by name, mobile or email", "Buscar por nombre, celular o email")
    val new get() = tr("New customer", "Nuevo cliente")
    val edit get() = tr("Edit customer", "Editar cliente")
    val emptyTitle get() = tr("No customers yet", "Aún no tienes clientes")
    val emptyBody get() = tr(
        "They show up here with their first booking, or you can add them yourself.",
        "Aparecen aquí con su primera reserva, o puedes agregarlos tú.",
    )
    val noMatches get() = tr("No customer matches your search.", "Ningún cliente coincide con tu búsqueda.")
    fun count(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 cliente" else "$n clientes") else (if (n == 1) "1 customer" else "$n customers")

    // A row of the list
    fun visits(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 visita" else "$n visitas") else (if (n == 1) "1 visit" else "$n visits")
    fun next(date: String) = tr("Next $date", "Próxima $date")
    val noContact get() = tr("No contact details", "Sin datos de contacto")
    val newAppointment get() = tr("New appointment", "Nueva cita")

    // The detail
    fun since(date: String) = tr("Customer since $date", "Cliente desde $date")
    val visitsLabel get() = tr("Visits", "Visitas")
    val lastVisit get() = tr("Last visit", "Última visita")
    val nextVisit get() = tr("Next visit", "Próxima cita")
    val details get() = tr("Details", "Datos")
    val upcoming get() = tr("Upcoming", "Próximas")
    val history get() = tr("History", "Historial")
    val noHistory get() = tr("No appointments yet.", "Todavía no tiene citas.")

    // The form
    val contact get() = tr("Contact", "Contacto")
    val moreDetails get() = tr("More details", "Más datos")
    val birthDate get() = tr("Date of birth", "Fecha de nacimiento")
    val clearDate get() = tr("Remove", "Quitar")
    val document get() = tr("ID number", "Documento")
    val gender get() = tr("Gender", "Género")
    /** The four choices, in the order the form offers them. */
    val genders: List<String> = listOf("female", "male", "other", "")
    fun genderLabel(value: String?) = when (value) {
        "female" -> tr("Female", "Femenino")
        "male" -> tr("Male", "Masculino")
        "other" -> tr("Other", "Otro")
        else -> tr("Not set", "Sin definir")
    }
    val created get() = tr("Customer added", "Cliente agregado")
    val updated get() = tr("Changes saved", "Cambios guardados")
    val contactRequired get() = tr("Leave at least a mobile or an email.", "Deja al menos un celular o un email.")
    val nameRequired get() = tr("Write the first and last name.", "Escribe el nombre y el apellido.")
    val firstNameRequired get() = tr("Write the first name.", "Escribe el nombre.")
    val lastNameRequired get() = tr("Write the last name.", "Escribe el apellido.")
    val emailInvalid get() = tr("Enter a valid email.", "Escribe un email válido.")
    val email get() = tr("Email", "Email")
}
