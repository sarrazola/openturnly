package org.openturnly.mobile

import android.app.Application
import org.openturnly.mobile.push.Push

class OpenTurnlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Starts Firebase only when the brand carries push; otherwise does nothing.
        Push.start(this)
    }
}
