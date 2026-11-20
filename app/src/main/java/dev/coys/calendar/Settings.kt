package dev.coys.calendar

import android.content.Context
import androidx.core.content.edit

/** User preferences plus small bits of alarm bookkeeping, in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var kickoffReminder: Boolean
        get() = prefs.getBoolean("kickoff_reminder", true)
        set(v) = prefs.edit { putBoolean("kickoff_reminder", v) }

    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 30)
        set(v) = prefs.edit { putInt("reminder_minutes", v) }

    var lineupAlert: Boolean
        get() = prefs.getBoolean("lineup_alert", true)
        set(v) = prefs.edit { putBoolean("lineup_alert", v) }

    var wakeLeadMinutes: Int
        get() = prefs.getInt("wake_lead_minutes", 15)
        set(v) = prefs.edit { putInt("wake_lead_minutes", v) }

    var wakeAlarmMatchIds: Set<String>
        get() = prefs.getStringSet("wake_ids", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("wake_ids", v) }

    fun setWakeAlarm(matchId: String, on: Boolean) {
        wakeAlarmMatchIds = if (on) wakeAlarmMatchIds + matchId else wakeAlarmMatchIds - matchId
    }

    /** Matches whose line-up notification has already gone out. */
    var lineupNotifiedIds: Set<String>
        get() = prefs.getStringSet("lineup_notified", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("lineup_notified", v) }

    /** Keys of alarms currently registered with AlarmManager, so they can be cancelled. */
    var scheduledAlarmKeys: Set<String>
        get() = prefs.getStringSet("scheduled_alarms", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("scheduled_alarms", v) }

    var lastRefreshMillis: Long
        get() = prefs.getLong("last_refresh", 0L)
        set(v) = prefs.edit { putLong("last_refresh", v) }
}
