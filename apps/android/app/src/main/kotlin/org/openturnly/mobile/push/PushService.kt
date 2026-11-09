package org.openturnly.mobile.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.openturnly.mobile.rules.NotificationData

/** Receives what Firebase Cloud Messaging delivers: new tokens and messages about new bookings. */
class PushService : FirebaseMessagingService() {
    // The registration token the server sends to (see Push.token).
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Push.tokenChanged(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = NotificationData.parse(message.data, message.notification?.title, message.notification?.body)
        Push.show(applicationContext, data)
        Push.onMessage?.invoke(data)
    }
}
