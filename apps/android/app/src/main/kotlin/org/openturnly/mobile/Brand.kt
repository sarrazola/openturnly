package org.openturnly.mobile

import java.net.URL
import org.openturnly.mobile.localization.AppLocale
import org.openturnly.mobile.rules.ServerAddress

/**
 * The identity this build was compiled with.
 *
 * `brand/brand.properties` is read by Gradle at build time and its values land
 * in `BuildConfig`. Nothing here is fetched or configurable at runtime: an
 * app's identity is decided when it is built.
 */
object Brand {
    private fun value(raw: String): String = raw.trim()

    /** Addresses in the brand file carry no scheme. */
    private fun httpsUrl(raw: String): URL? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = runCatching { URL(candidate) }.getOrNull() ?: return null
        if (url.protocol != "https" || url.host.isNullOrEmpty() || url.userInfo != null) return null
        return url
    }

    const val DEFAULT_COLOR = "#0f766e"

    val name: String = value(BuildConfig.BRAND_PRODUCT_NAME).ifEmpty { "OpenTurnly" }

    /** The accent of every screen. Six hex digits in the brand file, without "#". */
    val primaryColorHex: String = value(BuildConfig.BRAND_PRIMARY_COLOR).removePrefix("#").let {
        if (it.length == 6 && it.all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' }) "#$it" else DEFAULT_COLOR
    }

    /** Pre-fills the server field; a local address keeps plain HTTP like a typed one. */
    val defaultServer: String = ServerAddress.normalize(value(BuildConfig.BRAND_DEFAULT_SERVER)) ?: ""

    /**
     * A preset for a service whoever published this build runs.
     *
     * There is none in this repository, on purpose: an open-source build should
     * not point at somebody's hosted product. A build that has one asks for the
     * account name (or an e-mail alone, with a directory) and still offers
     * typing any other address.
     */
    data class HostedPreset(val label: String, val serverTemplate: String)

    val hosted: HostedPreset? = run {
        val template = value(BuildConfig.BRAND_HOSTED_SERVER_TEMPLATE)
        if (!template.contains("{workspace}")) return@run null
        val withScheme = if (template.contains("://")) template else "https://$template"
        if (!withScheme.startsWith("https://")) null
        else HostedPreset(value(BuildConfig.BRAND_HOSTED_LABEL).ifEmpty { name }, withScheme)
    }

    /** An account directory: e-mail and password in, the accounts that person opens out. */
    val hostedSignInUrl: URL? = run {
        val raw = value(BuildConfig.BRAND_HOSTED_SIGN_IN)
        if (raw.isEmpty()) return@run null
        val normalized = ServerAddress.normalize(raw) ?: return@run null
        runCatching { URL(normalized) }.getOrNull()
    }

    val hostedLabel: String = value(BuildConfig.BRAND_HOSTED_LABEL).ifEmpty { name }

    /** Turn an account name (or its address) into the server it lives at. */
    fun hostedServer(workspace: String): String? {
        val preset = hosted ?: return null
        return HostedServer.resolve(workspace, preset.serverTemplate)
    }

    private fun localized(en: String, es: String): URL? {
        val preferred = if (AppLocale.isSpanish) es else en
        return httpsUrl(preferred.ifEmpty { en })
    }

    val privacyPolicyUrl: URL? get() = localized(BuildConfig.BRAND_PRIVACY_POLICY_URL_EN, BuildConfig.BRAND_PRIVACY_POLICY_URL_ES)
    val supportUrl: URL? get() = localized(BuildConfig.BRAND_SUPPORT_URL_EN, BuildConfig.BRAND_SUPPORT_URL_ES)
}
