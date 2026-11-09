package org.openturnly.mobile.rules

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Amounts as the server keeps them: integers in the currency's minor unit.
 *
 * The number of decimals comes from the same fixed table the server uses, not
 * from the platform (ICU says COP has two; the server, and people paying in
 * pesos, say none). Formatting follows the web: each currency is written the way
 * its own country writes it, and whole amounts drop their decimals.
 */
object Money {
    private val zeroDecimals = setOf("BIF", "CLP", "COP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW", "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF")
    private val threeDecimals = setOf("BHD", "IQD", "JOD", "KWD", "LYD", "OMR", "TND")
    private val localeByCurrency = mapOf(
        "ARS" to "es-AR", "BOB" to "es-BO", "BRL" to "pt-BR", "CLP" to "es-CL", "COP" to "es-CO", "CRC" to "es-CR",
        "DOP" to "es-DO", "EUR" to "es-ES", "GTQ" to "es-GT", "MXN" to "es-MX", "PEN" to "es-PE", "PYG" to "es-PY",
        "USD" to "en-US", "UYU" to "es-UY",
    )

    fun digits(currency: String): Int = when (currency.uppercase()) {
        in zeroDecimals -> 0
        in threeDecimals -> 3
        else -> 2
    }

    fun locale(currency: String): Locale = Locale.forLanguageTag(localeByCurrency[currency.uppercase()] ?: "en")

    private fun factor(currency: String): Long = when (digits(currency)) { 0 -> 1L; 3 -> 1000L; else -> 100L }

    /** "$ 25.000" for 25000 COP, "$25.50" for 2550 USD. The space before a number is non-breaking. */
    fun format(minor: Long, currency: String): String {
        val code = currency.uppercase()
        val places = digits(code)
        val format = NumberFormat.getCurrencyInstance(locale(code))
        runCatching { format.currency = Currency.getInstance(code) }
        val whole = minor % factor(code) == 0L
        format.minimumFractionDigits = if (whole) 0 else places
        format.maximumFractionDigits = if (whole) 0 else places
        val value = BigDecimal.valueOf(minor).movePointLeft(places)
        return format.format(value)
    }

    /** A signed amount, for discounts: "−$ 5.000". */
    fun negative(minor: Long, currency: String): String = "−" + format(minor, currency)

    /** The amount as typed in a field: "25000" or "25.5", without grouping. */
    fun inputText(minor: Long, currency: String): String {
        val value = BigDecimal.valueOf(minor).movePointLeft(digits(currency)).stripTrailingZeros()
        return if (value.scale() < 0) value.setScale(0).toPlainString() else value.toPlainString()
    }

    /**
     * What a person typed, in minor units. Either separator works as the decimal
     * one when the currency has decimals and what follows it is short enough;
     * every other separator is grouping. Null when there is no number.
     */
    fun parse(text: String, currency: String): Long? {
        val places = digits(currency)
        val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (cleaned.none { it.isDigit() }) return null
        val lastSeparator = cleaned.indexOfLast { it == '.' || it == ',' }
        val decimals = if (lastSeparator >= 0) cleaned.substring(lastSeparator + 1) else ""
        val asDecimal = places > 0 && lastSeparator >= 0 && decimals.length in 1..places && decimals.all { it.isDigit() }
        val integerPart = (if (asDecimal) cleaned.substring(0, lastSeparator) else cleaned).filter { it.isDigit() }.ifEmpty { "0" }
        val fraction = if (asDecimal) decimals else ""
        val number = runCatching { BigDecimal("$integerPart.${fraction.ifEmpty { "0" }}") }.getOrNull() ?: return null
        return runCatching { number.movePointRight(places).setScale(0, RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    /** The currency's symbol as its own country writes it ("$", "€", "S/"). */
    fun symbol(currency: String): String = runCatching {
        Currency.getInstance(currency.uppercase()).getSymbol(locale(currency))
    }.getOrDefault(currency.uppercase())
}
