package org.openturnly.mobile.localization

/**
 * Every piece of interface copy, in English and Spanish.
 *
 * Each string is written once with both languages side by side, so a label that
 * exists in one and not the other cannot compile. The Spanish follows the web
 * admin's wording, so the same business reads the same words on both. Screens
 * read `Copy.agenda.title`; the language follows the phone.
 */
object Copy {
    val common = CommonCopy
    val signIn = SignInCopy
    val nav = NavCopy
    val agenda = AgendaCopy
    val booking = BookingCopy
    val customers = CustomersCopy
    val sales = SalesCopy
    val checkout = CheckoutCopy
    val more = MoreCopy
    val services = ServicesCopy
    val business = BusinessCopy
    val reports = ReportsCopy
    val account = AccountCopy
    val giftCards = GiftCardsCopy
    val errors = ErrorsCopy
}

object CommonCopy {
    val save get() = tr("Save", "Guardar")
    val saveChanges get() = tr("Save changes", "Guardar cambios")
    val cancel get() = tr("Cancel", "Cancelar")
    val close get() = tr("Close", "Cerrar")
    val done get() = tr("Done", "Listo")
    val back get() = tr("Back", "Volver")
    val retry get() = tr("Try again", "Reintentar")
    val edit get() = tr("Edit", "Editar")
    val delete get() = tr("Delete", "Eliminar")
    val add get() = tr("Add", "Agregar")
    val search get() = tr("Search", "Buscar")
    val today get() = tr("Today", "Hoy")
    val tomorrow get() = tr("Tomorrow", "Mañana")
    val yesterday get() = tr("Yesterday", "Ayer")
    val all get() = tr("All", "Todos")
    val active get() = tr("Active", "Activo")
    val inactive get() = tr("Inactive", "Inactivo")
    val name get() = tr("Name", "Nombre")
    val firstName get() = tr("First name", "Nombre")
    val lastName get() = tr("Last name", "Apellido")
    val phone get() = tr("Mobile", "Celular")
    val email get() = tr("Email", "Email")
    val address get() = tr("Address", "Dirección")
    val description get() = tr("Description", "Descripción")
    val notes get() = tr("Notes", "Notas")
    val note get() = tr("Note", "Nota")
    val location get() = tr("Location", "Local")
    val locations get() = tr("Locations", "Locales")
    val allLocations get() = tr("All locations", "Todos los locales")
    val professional get() = tr("Staff member", "Profesional")
    val service get() = tr("Service", "Servicio")
    val date get() = tr("Date", "Fecha")
    val time get() = tr("Time", "Hora")
    val price get() = tr("Price", "Precio")
    val priceOnRequest get() = tr("Price on request", "Precio a consultar")
    val duration get() = tr("Duration", "Duración")
    val code get() = tr("Code", "Código")
    val call get() = tr("Call", "Llamar")
    val whatsapp get() = "WhatsApp"
    val share get() = tr("Share", "Compartir")
    val copy get() = tr("Copy", "Copiar")
    val copied get() = tr("Copied", "Copiado")
    val open get() = tr("Open", "Abrir")
    val optional get() = tr("Optional", "Opcional")
    val loadFailed get() = tr("Could not load this", "No pudimos cargar esto")
    val saved get() = tr("Saved", "Guardado")
    val none get() = tr("None", "Ninguno")
    val yes get() = tr("Yes", "Sí")
    val no get() = tr("No", "No")
    val pickDate get() = tr("Pick a date", "Elige una fecha")
    val ok get() = tr("OK", "Aceptar")
    val more get() = tr("More", "Más")
    val details get() = tr("Details", "Datos")
    val total get() = tr("Total", "Total")
    val period get() = tr("Period", "Periodo")
    val minutes get() = tr("minutes", "minutos")
    val readOnly get() = tr("Only admins can change this.", "Solo los administradores pueden cambiar esto.")
    fun count(n: Int, one: String, other: String) = if (n == 1) "1 $one" else "$n $other"
}

object SignInCopy {
    val title get() = tr("Sign in", "Inicia sesión")
    val subtitle get() = tr("Your agenda, customers and sales, on your phone.", "Tu agenda, tus clientes y tus ventas, en tu celular.")
    val serverTitle get() = tr("Connect to your server", "Conéctate a tu servidor")
    val serverHint get() = tr("The address where your business's OpenTurnly runs.", "La dirección donde corre el OpenTurnly de tu negocio.")
    val serverLabel get() = tr("Server address", "Dirección del servidor")
    val serverPlaceholder get() = "turnos.example.com"
    val accountLabel get() = tr("Account", "Cuenta")
    val accountPlaceholder get() = tr("your-business", "tu-negocio")
    val accountHint get() = tr("Your account's name, or paste its address.", "El nombre de tu cuenta, o pega su dirección.")
    val otherAddress get() = tr("Use another address", "Usar otra dirección")
    fun useHosted(name: String) = tr("Use $name", "Usar $name")
    val connect get() = tr("Continue", "Continuar")
    val connectedTo get() = tr("Connected to", "Conectado a")
    val changeServer get() = tr("Change server", "Cambiar servidor")
    val emailLabel get() = tr("Email", "Email")
    val emailPlaceholder get() = tr("you@business.com", "tu@negocio.com")
    val passwordLabel get() = tr("Password", "Contraseña")
    val submit get() = tr("Sign in", "Entrar")
    val invalidServer get() = tr("Enter a valid address. Plain http:// only works on a local network.", "Escribe una dirección válida. http:// sin cifrar solo funciona en una red local.")
    val notOpenTurnly get() = tr("That address does not answer like an OpenTurnly server.", "Esa dirección no responde como un servidor de OpenTurnly.")
    val wrongCredentials get() = tr("The email or the password is not right.", "El email o la contraseña no son correctos.")
    val forgot get() = tr("Forgot your password?", "¿Olvidaste tu contraseña?")
    val forgotTitle get() = tr("Ask your administrator", "Pídesela a tu administrador")
    val forgotBody get() = tr("Whoever manages your business on OpenTurnly can set a new password for you.", "Quien administra tu negocio en OpenTurnly puede ponerte una contraseña nueva.")
    val chooseAccount get() = tr("Which account?", "¿Qué cuenta?")
    val chooseAccountBody get() = tr("This email opens more than one account. Pick the one you want to work in.", "Este email abre más de una cuenta. Elige en cuál quieres trabajar.")
    val privacy get() = tr("Privacy", "Privacidad")
    val support get() = tr("Help", "Ayuda")
    val reconnectTitle get() = tr("Could not connect", "No pudimos conectarnos")
    val reconnectBody get() = tr("Your session is saved. Check your connection and try again.", "Tu sesión sigue guardada. Revisa tu conexión e inténtalo de nuevo.")
    val sessionEnded get() = tr("Your session ended. Sign in again.", "Tu sesión terminó. Vuelve a entrar.")
    val noBusinesses get() = tr("This account has no business yet", "Esta cuenta todavía no tiene negocios")
    val noBusinessesBody get() = tr("Create one from the web panel, or ask for access to yours.", "Crea uno desde el panel web o pide acceso al tuyo.")
}

object NavCopy {
    val agenda get() = tr("Agenda", "Agenda")
    val customers get() = tr("Customers", "Clientes")
    val sales get() = tr("Sales", "Ventas")
    val more get() = tr("More", "Más")
    val chooseBusiness get() = tr("Choose a business", "Elige un negocio")
    val switchBusiness get() = tr("Switch business", "Cambiar de negocio")
    val owner get() = tr("Administrator", "Administrador")
    val admin get() = tr("Administrator", "Administrador")
    val professionalRole get() = tr("Staff member", "Profesional")
    val signOut get() = tr("Sign out", "Cerrar sesión")
    val signOutTitle get() = tr("Sign out?", "¿Cerrar sesión?")
    val signOutBody get() = tr("You'll need your email and password to come back.", "Para volver necesitarás tu email y tu contraseña.")
    val sample get() = tr("Sample", "Ejemplo")
    fun role(role: String) = when (role) { "owner" -> owner; "admin" -> admin; else -> professionalRole }
}
