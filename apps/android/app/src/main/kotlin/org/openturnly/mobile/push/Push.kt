package org.openturnly.mobile.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openturnly.mobile.Brand
import org.openturnly.mobile.BuildConfig
import org.openturnly.mobile.R
import org.openturnly.mobile.api.OpenTurnlyApi
import org.openturnly.mobile.localization.Copy
import org.openturnly.mobile.rules.NotificationData
import org.openturnly.mobile.storage.PushPreferences
import org.openturnly.mobile.storage.SessionStore

/**
 * Push notifications through Firebase Cloud Messaging, the platform's channel.
 *
 * Firebase only starts when the brand file carries the four values of a
 * Firebase Android app ([available]). Without them nothing here touches it:
 * the app never asks for notification permission and the inbox still works
 * from the server. Firebase's own start-up is removed from the manifest and its
 * automatic token is off, so a token exists only once someone wants
 * notifications. Nothing here may break the app: every failure is swallowed.
 */
object Push {
    private const val TAG = "push"

    /** Appointments booked, moved and cancelled. The id also names the default channel in the manifest. */
    const val CHANNEL = "bookings"

    private val projectId = BuildConfig.BRAND_FIREBASE_PROJECT_ID.trim()
    private val applicationId = BuildConfig.BRAND_FIREBASE_APPLICATION_ID.trim()
    private val apiKey = BuildConfig.BRAND_FIREBASE_API_KEY.trim()
    private val senderId = BuildConfig.BRAND_FIREBASE_SENDER_ID.trim()

    /** Whether this build has push at all. */
    val available: Boolean = listOf(projectId, applicationId, apiKey, senderId).all { it.isNotEmpty() }

    @Volatile private var started = false

    /** Set while the app is open: a new token to register, a message that changes the counts. */
    @Volatile var onToken: ((String) -> Unit)? = null
    @Volatile var onMessage: ((NotificationData) -> Unit)? = null

    /** From the application's start, before anything can need Firebase. */
    fun start(context: Context) {
        if (!available || started) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .setGcmSenderId(senderId)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            started = true
        } catch (error: Exception) {
            Log.w(TAG, "Firebase did not start: ${error.message}")
        }
        createChannel(context)
    }

    /**
     * This phone's push token, or nothing when push is off or unreachable.
     *
     * Firebase now also offers `register()`, which hands over an installation id
     * instead of a token; the server addresses a registration token, so the app
     * keeps asking for one.
     */
    @Suppress("DEPRECATION")
    suspend fun token(): String? {
        if (!started) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "no push token: ${error.message}")
            null
        }
    }

    /** Android 13 and later ask before an app may notify. */
    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Whether the phone lets this app notify: the permission, or the app's switch in the settings. */
    fun permitted(context: Context): Boolean = !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun createChannel(context: Context) {
        if (!available) return
        val channel = NotificationChannel(CHANNEL, Copy.notifications.channel, NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Posts a message's notification; a tap opens the app on what it is about. */
    @SuppressLint("MissingPermission") // permitted() checks it first.
    fun show(context: Context, message: NotificationData) {
        if (!available || !message.showable || !permitted(context)) return
        createChannel(context)
        val id = (message.notificationId ?: message.appointmentId ?: (message.title + message.body)).hashCode()
        val open = PendingIntent.getActivity(
            context, id, NotificationTap.of(message).intent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(Color.parseColor(Brand.primaryColorHex))
            .setContentTitle(message.title.ifBlank { Brand.name })
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .apply { message.badge?.let { setNumber(it) } }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "notification not posted: ${error.message}")
        }
    }

    /** Takes every posted notification away, which also clears the launcher's dot. */
    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    /**
     * A new token from the platform. The open app registers it with its session;
     * with the app closed, the stored session does. Called off the main thread.
     */
    fun tokenChanged(context: Context, token: String) {
        onToken?.let { it(token); return }
        if (!available || PushPreferences.off(context) || !permitted(context)) return
        val session = SessionStore.load(context) ?: return
        try {
            runBlocking { OpenTurnlyApi(session.server, session.token).registerDevice(token) }
            PushPreferences.storeToken(context, token)
        } catch (error: Exception) {
            Log.w(TAG, "push token not registered: ${error.message}")
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error == null && task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(error ?: IllegalStateException("cancelled"))
        }
    }
}
