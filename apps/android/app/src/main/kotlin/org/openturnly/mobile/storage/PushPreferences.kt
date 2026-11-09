package org.openturnly.mobile.storage

import android.content.Context

/**
 * What this phone remembers about push: the token it last gave the server (to
 * take it back on sign-out), whether the person switched notifications off
 * here, and whether the permission was already asked for once. None of it is a
 * secret: a push token only lets someone send to this phone.
 */
object PushPreferences {
    private const val FILE = "openturnly.push"
    private const val TOKEN = "push.token"
    private const val OFF = "push.off"
    private const val ASKED = "push.asked"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun token(context: Context): String? = prefs(context).getString(TOKEN, null)?.takeIf { it.isNotBlank() }
    fun storeToken(context: Context, token: String) { prefs(context).edit().putString(TOKEN, token).apply() }

    fun off(context: Context): Boolean = prefs(context).getBoolean(OFF, false)
    fun storeOff(context: Context, off: Boolean) { prefs(context).edit().putBoolean(OFF, off).apply() }

    fun asked(context: Context): Boolean = prefs(context).getBoolean(ASKED, false)
    fun storeAsked(context: Context) { prefs(context).edit().putBoolean(ASKED, true).apply() }
}
