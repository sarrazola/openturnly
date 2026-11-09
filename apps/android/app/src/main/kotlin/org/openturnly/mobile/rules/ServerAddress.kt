package org.openturnly.mobile.rules

import java.net.URI

/**
 * What a person types as their server, turned into the base every route hangs off.
 *
 * `https://` is assumed when no scheme is typed. Plain `http://` is only for a
 * development server on this machine or the local network: loopback, the
 * private IPv4 ranges, `.local` and `.localhost` names, and `10.0.2.2`, which is
 * the development machine as the Android emulator sees it.
 */
object ServerAddress {
    fun isLocalHost(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h == "::1") return true
        val parts = h.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return when {
            octets[0] == 127 -> true
            octets[0] == 10 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            else -> false
        }
    }

    /**
     * The base URL (scheme, host, optional port and path, no trailing slash), or
     * null when the input cannot be a server or asks for plain HTTP on the internet.
     */
    fun normalize(input: String): String? {
        var value = input.trim()
        if (value.isEmpty() || value.any { it.isWhitespace() }) return null
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value)
        if (!hasScheme) {
            val host = value.substringBefore("/").substringBefore("?").let { authority ->
                if (authority.startsWith("[")) authority.substringBefore("]") + "]" else authority.substringBefore(":")
            }
            value = (if (isLocalHost(host)) "http://" else "https://") + value
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host ?: return null
        if (scheme != "https" && scheme != "http") return null
        if (scheme == "http" && !isLocalHost(host)) return null
        if (uri.userInfo != null) return null
        var path = (uri.rawPath ?: "").trimEnd('/')
        // People paste the address they see in the browser: drop the panel or API part.
        for (suffix in listOf("/api", "/admin")) {
            val index = path.indexOf(suffix)
            if (index >= 0 && (path.length == index + suffix.length || path[index + suffix.length] == '/')) path = path.substring(0, index)
        }
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val hostPart = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
        return "$scheme://${hostPart.lowercase()}$port$path"
    }

    /** The server's own page to recover a password. */
    fun forgotPasswordUrl(base: String): String = base.trimEnd('/') + "/forgot-password"

    /** The host as shown to people: "turnos.example.com" or "10.0.2.2:4100". */
    fun display(base: String): String = base.substringAfter("://").trimEnd('/')
}
