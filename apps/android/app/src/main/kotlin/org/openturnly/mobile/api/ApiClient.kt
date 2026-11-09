package org.openturnly.mobile.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openturnly.mobile.localization.Copy

/**
 * A failed call: the HTTP status, the server's stable `error.code` and, for
 * validation, which field failed and why (`error.fields`). Status 0 means the
 * server could not be reached at all. The message is written for people.
 */
class ApiException(
    val status: Int,
    val code: String,
    val fields: Map<String, String> = emptyMap(),
) : IOException(code) {
    val isUnauthorized: Boolean get() = status == 401
    override val message: String get() = Copy.errors.describe(code, fields)

    /** The plain-words message for one form field, if the server flagged it. */
    fun fieldMessage(field: String): String? {
        val entry = fields.entries.firstOrNull { it.key == field || it.key.endsWith(".$field") } ?: return null
        return Copy.errors.describeField(field, entry.value)
    }
}

/**
 * The one HTTP client of the app: the platform's own stack, JSON in and out,
 * the session's bearer token on every call, no cookies.
 *
 * The base URL is never baked in: a person signs in by typing (or picking) the
 * server their business runs on, and every call takes it from the session.
 */
object ApiClient {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private const val TIMEOUT_MS = 30_000

    fun encodePath(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun query(vararg pairs: Pair<String, String?>): String {
        val present = pairs.filter { !it.second.isNullOrEmpty() }
        if (present.isEmpty()) return ""
        return "?" + present.joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    /** `{"error": {"code": "…", "fields": {"customer.phone": "invalid_phone"}}}` */
    internal fun parseFailure(status: Int, body: String): ApiException {
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
        val code = runCatching { error?.get("code")?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?: when (status) { 401 -> "unauthorized"; 403 -> "forbidden"; 404 -> "not_found"; 429 -> "too_many_attempts"; else -> "unknown_error" }
        val fields = runCatching {
            (error?.get("fields") as? JsonObject)?.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
            }?.toMap()
        }.getOrNull() ?: emptyMap()
        return ApiException(status, code, fields)
    }

    private suspend fun exchange(url: URL, method: String, body: ByteArray?, contentType: String?, token: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", contentType ?: "application/json")
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                status to text
            } finally {
                connection.disconnect()
            }
        }

    private fun url(server: String, path: String): URL = try {
        URL("$server/api$path")
    } catch (error: Exception) {
        throw ApiException(0, "invalid_server")
    }

    private suspend fun perform(url: URL, method: String, body: ByteArray?, contentType: String?, token: String?): Pair<Int, String> {
        val (status, text) = try {
            exchange(url, method, body, contentType, token)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApiException) {
            throw error
        } catch (error: Exception) {
            throw ApiException(0, "network")
        }
        if (status !in 200..299) throw parseFailure(status, text)
        return status to text
    }

    private fun <T> decode(serializer: KSerializer<T>, status: Int, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (error: Exception) {
        throw ApiException(status, "unknown_error")
    }

    suspend fun <T> request(
        server: String, path: String, serializer: KSerializer<T>, method: String = "GET",
        body: JsonElement? = null, token: String? = null,
    ): T {
        val (status, text) = perform(url(server, path), method, body?.toString()?.toByteArray(), null, token)
        return decode(serializer, status, text)
    }

    /** A call whose answer the app does not need (204s and `{ok: true}`). */
    suspend fun send(server: String, path: String, method: String, body: JsonElement? = null, token: String? = null) {
        val payload = body ?: if (method == "POST" || method == "PATCH" || method == "PUT") JsonObject(emptyMap()) else null
        perform(url(server, path), method, payload?.toString()?.toByteArray(), null, token)
    }

    /** A request to an absolute address outside a server's `/api`, like an account directory. */
    suspend fun <T> request(url: URL, serializer: KSerializer<T>, method: String, body: JsonElement): T {
        val (status, text) = perform(url, method, body.toString().toByteArray(), null, null)
        return decode(serializer, status, text)
    }

    /** Public images (logos, photos) come without credentials; the server's URLs are relative. */
    suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            val code = connection.responseCode
            if (code !in 200..299) throw ApiException(code, "not_found")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}

/** A JSON object from Kotlin values; `null` is sent as JSON null, which some routes require. */
fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (key, value) -> key to element(value) })

/** The same, leaving out keys whose value is [Omit]: for partial updates. */
object Omit

fun element(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { element(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to element(v) })
    else -> JsonPrimitive(value.toString())
}

fun objWithout(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.filter { it.second !== Omit }.associate { (key, value) -> key to element(value) })
