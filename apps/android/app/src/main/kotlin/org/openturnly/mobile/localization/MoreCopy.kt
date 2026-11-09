package org.openturnly.mobile.localization

object MoreCopy {
    val title get() = tr("More", "Más")
    val manage get() = tr("Your business", "Tu negocio")
    val money get() = tr("Money and stock", "Dinero e inventario")
    val you get() = tr("You", "Tú")
    val bookingPage get() = tr("Booking page", "Página de reservas")
    fun activeCount(n: Int) = tr("$n active", if (n == 1) "1 activo" else "$n activos")
    fun version(version: String) = tr("Version $version", "Versión $version")
}

object ServicesCopy {
    val title get() = tr("Services", "Servicios")
    val new get() = tr("New service", "Nuevo servicio")
    val edit get() = tr("Edit service", "Editar servicio")
    val empty get() = tr("No services yet", "Todavía no hay servicios")
    val emptyBody get() = tr("Create the first one with +.", "Crea el primero con +.")
    val category get() = tr("Category", "Categoría")
    val categoryHint get() = tr("Groups it on the booking page, like \"Cuts\".", "La agrupa en la página de reservas, como «Cortes».")
    val noCategory get() = tr("No category", "Sin categoría")
    val durationMin get() = tr("Duration (minutes)", "Duración (minutos)")
    val activeHint get() = tr("Inactive services can't be booked.", "Los servicios inactivos no se pueden reservar.")
    val created get() = tr("Service created", "Servicio creado")
    val updated get() = tr("Service updated", "Servicio actualizado")
    val deleted get() = tr("Service deleted", "Servicio eliminado")
    val deleteTitle get() = tr("Delete this service?", "¿Eliminar este servicio?")
    val deleteBody get() = tr("Past appointments keep its name. To stop taking bookings without losing it, make it inactive.", "Las citas pasadas conservan su nombre. Para dejar de recibir reservas sin perderlo, desactívalo.")
    val onlinePayment get() = tr("Online payment", "Pago en línea")
    fun paymentMode(mode: String) = when (mode) {
        "optional" -> tr("Optional", "Opcional")
        "deposit" -> tr("Deposit", "Abono")
        "full" -> tr("Full payment", "Pago completo")
        else -> tr("None", "Sin cobro")
    }
    val nameRequired get() = tr("Write the service's name.", "Escribe el nombre del servicio.")
    val durationInvalid get() = tr("Between 5 and 600 minutes.", "Entre 5 y 600 minutos.")
    val priceRequired get() = tr("Write the price (0 if it's free).", "Escribe el precio (0 si es gratis).")
}

object ProfessionalsCopy {
    val title get() = tr("Staff", "Profesionales")
    val new get() = tr("New staff member", "Nuevo profesional")
    val empty get() = tr("No staff yet", "Todavía no hay profesionales")
    val emptyBody get() = tr("Add the people who serve your customers.", "Agrega a las personas que atienden a tus clientes.")
    fun servicesCount(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 servicio" else "$n servicios") else (if (n == 1) "1 service" else "$n services")
    val ownHours get() = tr("Own hours", "Horario propio")
    val locationHours get() = tr("Location's hours", "Horario del local")
    val usesLocationHours get() = tr("Works the location's hours.", "Trabaja el horario del local.")
    val services get() = tr("Services they do", "Servicios que hace")
    val commissions get() = tr("Commissions", "Comisiones")
    val serviceCommission get() = tr("On services (%)", "Por servicios (%)")
    val productCommission get() = tr("On products (%)", "Por productos (%)")
    val weeklyHours get() = tr("Weekly hours", "Horario semanal")
    val activeHint get() = tr("Inactive staff don't appear for booking.", "Los profesionales inactivos no aparecen para reservar.")
    val created get() = tr("Staff member created", "Profesional creado")
    val updated get() = tr("Staff member updated", "Profesional actualizado")
    val nameRequired get() = tr("Write their name.", "Escribe su nombre.")
    val percentInvalid get() = tr("Between 0 and 100.", "Entre 0 y 100.")
    val hoursOnWeb get() = tr("Their own hours are set in the web panel.", "Su horario propio se cambia en el panel web.")
}

object HoursCopy {
    val title get() = tr("Opening hours", "Horario del negocio")
    val closed get() = tr("Closed", "Cerrado")
    val open get() = tr("Open", "Abierto")
    val addRange get() = tr("Add hours", "Agregar horario")
    val from get() = tr("From", "Desde")
    val to get() = tr("To", "Hasta")
    val saved get() = tr("Hours saved", "Horario guardado")
    val bookingRules get() = tr("Online booking", "Reservas en línea")
    val slotInterval get() = tr("Times every", "Horarios cada")
    val minNotice get() = tr("Minimum notice", "Anticipación mínima")
    val maxAdvance get() = tr("Book up to", "Se puede reservar hasta")
    fun days(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 día antes" else "$n días antes") else (if (n == 1) "1 day ahead" else "$n days ahead")
    val closures get() = tr("Special closures", "Cierres especiales")
    val noClosures get() = tr("No closures coming up.", "No hay cierres próximos.")
    val wholeBusiness get() = tr("Whole business", "Todo el negocio")
    val endBeforeStart get() = tr("A range ends before it starts.", "Hay un horario que termina antes de empezar.")
    val holidays get() = tr("Closed on public holidays", "Cerrado en festivos")
    fun minutesLabel(minutes: Int): String = when {
        minutes == 0 -> tr("None", "Ninguna")
        minutes % 1440 == 0 -> if (AppLocale.isSpanish) "${minutes / 1440} d" else "${minutes / 1440} d"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "$minutes min"
    }
}

object BusinessCopy {
    val title get() = tr("Business", "Negocio")
    val details get() = tr("Business details", "Datos del negocio")
    val bookingLink get() = tr("Your booking page", "Tu página de reservas")
    val bookingLinkHint get() = tr("Share it so customers book on their own, any time.", "Compártela para que tus clientes reserven solos, a cualquier hora.")
    val qrTitle get() = tr("QR code", "Código QR")
    val qrHint get() = tr("Print it for your counter or show it from your phone.", "Imprímelo para tu mostrador o muéstralo desde tu celular.")
    val shareQr get() = tr("Share QR", "Compartir QR")
    val shareText get() = tr("Book your appointment here:", "Agenda tu cita aquí:")
    val region get() = tr("Region", "Región")
    val timezone get() = tr("Time zone", "Zona horaria")
    val currency get() = tr("Currency", "Moneda")
    val language get() = tr("Booking page language", "Idioma de la página")
    val updated get() = tr("Business updated", "Negocio actualizado")
    val nameRequired get() = tr("Write the business's name.", "Escribe el nombre del negocio.")
    val linkCopied get() = tr("Link copied", "Enlace copiado")
}

object ReportsCopy {
    val title get() = tr("Reports", "Reportes")
    val summary get() = tr("Summary", "Resumen")
    val tips get() = tr("Tips", "Propinas")
    val commissions get() = tr("Commissions", "Comisiones")
    val bookings get() = tr("Bookings", "Reservas")
    val occupancy get() = tr("Occupancy", "Ocupación")
    val newCustomers get() = tr("New customers", "Clientes nuevos")
    val billed get() = tr("Sales", "Ventas")
    fun vs(value: String) = tr("vs. $value before", "vs. $value antes")
    val noComparison get() = tr("Nothing to compare", "Sin datos para comparar")
    val origin get() = tr("How bookings arrive", "Cómo llegan tus reservas")
    fun originLabel(key: String) = when (key) {
        "online" -> tr("Booking page", "Página de reservas")
        "agent" -> tr("AI assistant", "Asistente de IA")
        else -> tr("Your team, from the agenda", "Tu equipo, desde la agenda")
    }
    val topSold get() = tr("Best sellers", "Lo más vendido")
    fun sold(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 vendido" else "$n vendidos") else "$n sold"
    val byProfessional get() = tr("Appointments by staff member", "Citas por profesional")
    val noProfessional get() = tr("No staff member", "Sin profesional")
    val nothing get() = tr("No data in these dates yet.", "Aún no hay datos en estas fechas.")
    val noTips get() = tr("No tips in these dates.", "No hay propinas en estas fechas.")
    val points get() = tr("pts", "pts")
    val today get() = tr("Today's appointments", "Citas de hoy")
    val noneToday get() = tr("No appointments today.", "Hoy no hay citas.")
    val chartBookings get() = tr("Bookings", "Reservas")
    val chartBilled get() = tr("Sales", "Ventas")
    val services get() = tr("Services", "Servicios")
    val products get() = tr("Products", "Productos")
    val toPay get() = tr("Total to pay", "Total a pagar")
    fun rateOf(rate: String, amount: String) = tr("$rate % of $amount", "$rate % de $amount")
    val noProfessionals get() = tr("No staff yet.", "Aún no tienes profesionales.")
    fun preset(key: org.openturnly.mobile.rules.Periods.Preset) = when (key) {
        org.openturnly.mobile.rules.Periods.Preset.TODAY -> tr("Today", "Hoy")
        org.openturnly.mobile.rules.Periods.Preset.WEEK -> tr("This week", "Esta semana")
        org.openturnly.mobile.rules.Periods.Preset.MONTH -> tr("This month", "Este mes")
        org.openturnly.mobile.rules.Periods.Preset.LAST_MONTH -> tr("Last month", "Mes pasado")
        org.openturnly.mobile.rules.Periods.Preset.LAST_30 -> tr("Last 30 days", "Últimos 30 días")
        org.openturnly.mobile.rules.Periods.Preset.YEAR -> tr("This year", "Este año")
    }
    val paidBadge get() = tr("Paid", "Pagada")
}

object AccountCopy {
    val title get() = tr("My account", "Mi cuenta")
    val details get() = tr("Your details", "Tus datos")
    val currentPassword get() = tr("Current password", "Contraseña actual")
    val emailNeedsPassword get() = tr("To change the email you sign in with, confirm your password.", "Para cambiar el email con el que entras, confirma tu contraseña.")
    val passwordTitle get() = tr("Password", "Contraseña")
    val passwordHint get() = tr("Changing it signs you out on your other devices.", "Al cambiarla se cierra tu sesión en los demás dispositivos.")
    val newPassword get() = tr("New password", "Nueva contraseña")
    val newPasswordHint get() = tr("At least 10 characters.", "Mínimo 10 caracteres.")
    val repeatPassword get() = tr("Repeat the new password", "Repite la nueva contraseña")
    val mismatch get() = tr("The passwords don't match.", "Las contraseñas no coinciden.")
    val changePassword get() = tr("Change password", "Cambiar contraseña")
    val passwordChanged get() = tr("Password changed", "Contraseña cambiada")
    val updated get() = tr("Details saved", "Datos guardados")
    val session get() = tr("Session", "Sesión")
    val server get() = tr("Server", "Servidor")
    val role get() = tr("Role", "Rol")
    val theme get() = tr("The app follows your phone's light or dark theme.", "La app sigue el tema claro u oscuro de tu celular.")
}

object CashCopy {
    val title get() = tr("Cash register", "Caja")
    val inDrawer get() = tr("Cash in the drawer", "Efectivo en caja")
    val inDrawerHint get() = tr("All time, at every location shown.", "Desde siempre, en los locales que ves.")
    val result get() = tr("Result of the period", "Resultado del periodo")
    val sales get() = tr("Sales", "Ventas")
    val deposits get() = tr("Deposits", "Abonos")
    val internal get() = tr("Internal sales", "Ventas internas")
    val giftCards get() = tr("Gift cards", "Gift cards")
    val otherIncome get() = tr("Other income", "Otros ingresos")
    val expenses get() = tr("Expenses", "Egresos")
    val movements get() = tr("Movements", "Movimientos")
    val noMovements get() = tr("No movements in these dates.", "No hay movimientos en estas fechas.")
    val income get() = tr("Income", "Ingreso")
    val expense get() = tr("Expense", "Egreso")
    val transfer get() = tr("Transfer", "Traspaso")
    val adjustment get() = tr("Cash count", "Cuadre de caja")
    fun kind(kind: String) = when (kind) {
        "income" -> income
        "expense" -> expense
        "transfer_out" -> tr("Transfer (out)", "Traspaso (sale)")
        "transfer_in" -> tr("Transfer (in)", "Traspaso (entra)")
        "adjustment" -> adjustment
        else -> kind
    }
    val category get() = tr("Category", "Categoría")
    val saved get() = tr("Movement recorded", "Movimiento registrado")
    val newIncome get() = tr("Record income", "Registrar ingreso")
    val newExpense get() = tr("Record expense", "Registrar egreso")
}

object InventoryCopy {
    val title get() = tr("Inventory", "Inventario")
    val search get() = tr("Search product or SKU", "Buscar producto o SKU")
    val empty get() = tr("No products yet", "Todavía no hay productos")
    val emptyBody get() = tr("Products are created in the web panel.", "Los productos se crean en el panel web.")
    val lowStock get() = tr("Low stock", "Stock bajo")
    fun lowStockCount(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 producto con stock bajo" else "$n productos con stock bajo") else (if (n == 1) "1 product low on stock" else "$n products low on stock")
    fun inStock(n: Long) = tr("$n in stock", "$n en stock")
    val stock get() = tr("Stock", "Stock")
    val stockByLocation get() = tr("Stock by location", "Stock por local")
    val alertAt get() = tr("Alert at", "Alerta en")
    val sku get() = tr("SKU", "SKU")
    val category get() = tr("Category", "Categoría")
    val brand get() = tr("Brand", "Marca")
    val price get() = tr("Sale price", "Precio de venta")
    val internalPrice get() = tr("Internal price", "Precio interno")
    val cost get() = tr("Cost", "Costo")
    val notForSale get() = tr("Not for sale", "No se vende")
    val count get() = tr("Count stock", "Registrar conteo")
    val countHint get() = tr("How many are there now at this location?", "¿Cuántos hay ahora en este local?")
    val counted get() = tr("Counted", "Contados")
    val countSaved get() = tr("Stock updated", "Stock actualizado")
    val inactive get() = tr("Inactive", "Inactivo")
}

object GiftCardsCopy {
    val title get() = tr("Gift cards", "Gift cards")
    val empty get() = tr("No codes or gift cards yet", "Todavía no hay códigos ni gift cards")
    val emptyBody get() = tr("Create them in the web panel.", "Créalos desde el panel web.")
    val discount get() = tr("Discount code", "Código de descuento")
    val balance get() = tr("Gift card with balance", "Gift card con saldo")
    fun state(state: String) = when (state) {
        "active" -> tr("Active", "Activa")
        "inactive" -> tr("Inactive", "Desactivada")
        "scheduled" -> tr("Scheduled", "Programada")
        "expired" -> tr("Expired", "Vencida")
        "used_up" -> tr("Used up", "Sin usos")
        "spent" -> tr("No balance", "Sin saldo")
        else -> state
    }
    fun uses(n: Int, max: Int?) = if (max != null) tr("$n of $max uses", "$n de $max usos") else if (AppLocale.isSpanish) (if (n == 1) "1 uso" else "$n usos") else (if (n == 1) "1 use" else "$n uses")
    fun left(amount: String) = tr("$amount left", "Quedan $amount")
    fun until(date: String) = tr("until $date", "hasta $date")
    val activeLabel get() = tr("Active", "Activa")
    val updated get() = tr("Gift card updated", "Gift card actualizada")
}

object AccessCopy {
    val title get() = tr("Team access", "Equipo")
    val members get() = tr("People with access", "Personas con acceso")
    val invitations get() = tr("Pending invitations", "Invitaciones pendientes")
    val invite get() = tr("Invite by email", "Invitar por email")
    val inviteHint get() = tr("They get administrator access. If they don't have an account yet, you'll get a link to send them (valid for 7 days).", "Tendrá acceso de administrador. Si todavía no tiene cuenta, te damos un enlace para enviarle (vale 7 días).")
    val send get() = tr("Invite", "Invitar")
    val added get() = tr("They now have access", "Ya tiene acceso")
    val invited get() = tr("Invitation created", "Invitación creada")
    val inviteLink get() = tr("Send them this link to create their account. It's shown only once.", "Envíale este enlace para que cree su cuenta. Solo se muestra una vez.")
    fun expires(date: String) = tr("Expires $date", "Vence el $date")
    val empty get() = tr("Only you have access for now.", "Por ahora solo tú tienes acceso.")
    val ownerNote get() = tr("The install's administrator always has access and isn't listed.", "El administrador de la instalación siempre tiene acceso y no aparece en la lista.")
    fun linked(name: String) = tr("Profile: $name", "Perfil: $name")
}
