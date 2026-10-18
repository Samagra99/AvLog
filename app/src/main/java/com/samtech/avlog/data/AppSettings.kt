package com.samtech.avlog.data

import android.content.Context
import androidx.core.content.edit

/** Small app settings store (not flight data). */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("avlog_settings", Context.MODE_PRIVATE)

    /** P5.5: the POST_NOTIFICATIONS prompt is shown at most once, in context. */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIF, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED_NOTIF, value) }

    private companion object {
        const val KEY_ASKED_NOTIF = "asked_post_notifications"
    }
}
