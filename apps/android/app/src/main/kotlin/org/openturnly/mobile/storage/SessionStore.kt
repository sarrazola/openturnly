package org.openturnly.mobile.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The server, the bearer token and the business last opened, kept together. */
@Serializable
data class StoredSession(val server: String, val token: String, val businessId: String? = null)

/**
 * A small file encrypted with AES-GCM under a key the Android keystore holds
 * (in secure hardware where the phone has it). The key never leaves the
 * keystore, so the file is useless when copied elsewhere; it is kept out of
 * backups too.
 */
object SecureStore {
    private const val ALIAS = "openturnly.session.key"
    private const val IV_BYTES = 12

    @Synchronized
    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun file(context: Context, name: String) = AtomicFile(File(context.noBackupFilesDir, name))

    fun write(context: Context, name: String, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain)
        val target = file(context, name)
        val stream = target.startWrite()
        try {
            stream.write(sealed)
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }

    /** Null when there is nothing stored or it cannot be opened (a reset keystore, a tampered file). */
    fun read(context: Context, name: String): ByteArray? = runCatching {
        val sealed = file(context, name).readFully()
        if (sealed.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed.copyOfRange(0, IV_BYTES)))
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }.getOrNull()

    fun delete(context: Context, name: String) { file(context, name).delete() }
}

object SessionStore {
    private const val FILE = "session.bin"
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): StoredSession? {
        val raw = SecureStore.read(context, FILE)?.toString(Charsets.UTF_8) ?: return null
        val session = runCatching { json.decodeFromString(StoredSession.serializer(), raw) }.getOrNull() ?: return null
        if (session.token.isEmpty()) return null
        val url = runCatching { java.net.URL(session.server) }.getOrNull() ?: return null
        if (url.protocol !in listOf("http", "https") || url.userInfo != null) return null
        return session
    }

    /** Never falls back to plaintext: if the keystore refuses, the session is not kept. */
    fun store(context: Context, session: StoredSession) {
        SecureStore.write(context, FILE, json.encodeToString(StoredSession.serializer(), session).toByteArray(Charsets.UTF_8))
    }

    fun clear(context: Context) {
        runCatching { SecureStore.delete(context, FILE) }
    }
}

/**
 * The last server someone signed in to, so signing back in starts at the e-mail.
 * An address is not a secret; it lives in plain preferences, apart from the token.
 */
object LastServer {
    private const val FILE = "openturnly.prefs"
    private const val KEY = "last.server"

    fun load(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.startsWith("http") }

    fun store(context: Context, server: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, server).apply()
    }
}
