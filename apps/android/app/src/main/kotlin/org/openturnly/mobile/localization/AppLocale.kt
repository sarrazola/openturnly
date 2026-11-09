package org.openturnly.mobile.localization

import java.util.Locale

/**
 * English and Spanish, chosen by the phone.
 *
 * There is no language picker on purpose: someone running their agenda should
 * not have to set one, and the phone already knows. Anything the device is not
 * set to falls back to English.
 */
object AppLocale {
    val languageCode: String get() = if (Locale.getDefault().language.lowercase() == "es") "es" else "en"
    val isSpanish: Boolean get() = languageCode == "es"
    fun <T> pick(en: T, es: T): T = if (isSpanish) es else en

    /** The locale dates and numbers are written in: the phone's region when it speaks the app's language. */
    val locale: Locale get() {
        val device = Locale.getDefault()
        return if (device.language.lowercase() == languageCode) device else Locale.forLanguageTag(languageCode)
    }
}

/** One string in both languages, side by side, so neither can be forgotten. */
internal fun tr(en: String, es: String): String = AppLocale.pick(en, es)
