package org.openturnly.mobile.localization

import org.openturnly.mobile.rules.Money
import org.openturnly.mobile.rules.Periods

object MoreCopy {
    val title get() = tr("More", "Más")
    val you get() = tr("You", "Tú")
    val bookingLink get() = tr("Booking link", "Link de reservas")
    val webPanel get() = tr("Open the web panel", "Abrir el panel web")
    val webPanelHint get() = tr(
        "Hours, services, staff, cash register, inventory, gift cards and settings are managed in the web panel.",
        "El horario, los servicios, el equipo, la caja, el inventario, las gift cards y la configuración se manejan en el panel web.",
    )
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

object BusinessCopy {
    val title get() = tr("Business", "Negocio")
    val details get() = tr("Business details", "Datos del negocio")
    val bookingLink get() = tr("Your booking page", "Tu página de reservas")
    val bookingLinkHint get() = tr("Share it so your customers book on their own, any time.", "Compártela para que tus clientes reserven solos, a cualquier hora.")
    val qrTitle get() = tr("QR code", "Código QR")
    val qrHint get() = tr("Print it for the counter or the mirror: customers scan it and book.", "Imprímelo para el mostrador o el espejo: tus clientes lo escanean y reservan.")
    val shareQr get() = tr("Share QR", "Compartir QR")
    /** What goes out with the link when it is shared: "Reserva tu cita en Barbería Centro: https://…". */
    fun shareText(name: String, link: String) = tr("Book with $name: $link", "Reserva tu cita en $name: $link")
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
    val origin get() = tr("Where bookings come from", "Cómo llegan tus reservas")
    fun originLabel(key: String) = when (key) {
        "online" -> tr("Booking page", "Página de reservas")
        "agent" -> tr("AI assistant", "Asistente de IA")
        else -> tr("Your team, from the calendar", "Tu equipo, desde la agenda")
    }
    /** An origin row's figures: "12 · 60 %". */
    fun originFigures(count: Int, share: Int) = "$count · " + Money.percent(share.toString(), AppLocale.locale)
    val topSold get() = tr("Best sellers", "Lo más vendido")
    fun sold(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 vendido" else "$n vendidos") else "$n sold"
    val byProfessional get() = tr("Appointments by staff member", "Citas por profesional")
    fun appointments(n: Int) = if (AppLocale.isSpanish) (if (n == 1) "1 cita" else "$n citas") else (if (n == 1) "1 appointment" else "$n appointments")
    val noProfessional get() = tr("No staff member", "Sin profesional")
    val nothing get() = tr("No data for these dates yet.", "Aún no hay datos en estas fechas.")
    val noTips get() = tr("No tips in these dates.", "No hay propinas en estas fechas.")
    val points get() = tr("pts", "pts")
    /** A whole percent as the language writes it: "45 %" / "45%". */
    fun percent(value: Int) = Money.percent(value.toString(), AppLocale.locale)
    /** A KPI's change: "+12 %" and "−5 %" (a real minus sign), "+3 pts" for occupancy, "0 %" when flat. */
    fun change(trend: Periods.Trend, inPoints: Boolean): String {
        val sign = when (trend.direction) { Periods.Direction.UP -> "+"; Periods.Direction.DOWN -> "\u2212"; Periods.Direction.FLAT -> "" }
        return sign + if (inPoints) "${trend.amount}\u00A0$points" else percent(trend.amount)
    }
    val today get() = tr("Today's appointments", "Citas de hoy")
    val noneToday get() = tr("No appointments today.", "Hoy no hay citas.")
    val chartBookings get() = tr("Bookings", "Reservas")
    val chartBilled get() = tr("Sales", "Ventas")
    val services get() = tr("Services", "Servicios")
    val products get() = tr("Products", "Productos")
    fun servicesCount(n: Int) = tr("Services ($n)", "Servicios ($n)")
    fun productsCount(n: Int) = tr("Products ($n)", "Productos ($n)")
    val toPay get() = tr("Total to pay", "Total a pagar")
    /** The base a commission is taken from: "10 % de $ 200.000" / "10% of $200,000". */
    fun rateOf(rate: Double, amount: String): String {
        val number = java.text.NumberFormat.getNumberInstance(AppLocale.locale).apply { maximumFractionDigits = 1; isGroupingUsed = false }.format(rate)
        val percent = Money.percent(number, AppLocale.locale)
        return tr("$percent of $amount", "$percent de $amount")
    }
    val noProfessionals get() = tr("No staff yet.", "Aún no tienes profesionales.")
    fun preset(key: Periods.Preset) = when (key) {
        Periods.Preset.TODAY -> tr("Today", "Hoy")
        Periods.Preset.WEEK -> tr("This week", "Esta semana")
        Periods.Preset.MONTH -> tr("This month", "Este mes")
        Periods.Preset.LAST_MONTH -> tr("Last month", "Mes pasado")
        Periods.Preset.LAST_30 -> tr("Last 30 days", "Últimos 30 días")
        Periods.Preset.YEAR -> tr("This year", "Este año")
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
    val updated get() = tr("Changes saved", "Cambios guardados")
    val session get() = tr("Session", "Sesión")
    val server get() = tr("Server", "Servidor")
    val role get() = tr("Role", "Rol")
    val theme get() = tr("The app follows your phone's light or dark mode.", "La app sigue el modo claro u oscuro de tu teléfono.")
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
