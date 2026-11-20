package dev.coys.calendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.coys.calendar.MainActivity
import dev.coys.calendar.Settings
import dev.coys.calendar.data.MatchRepository
import java.time.Instant

/** Applies [AlarmPlan] to AlarmManager, cancelling anything no longer planned. */
object AlarmScheduler {

    fun rescheduleAll(context: Context) {
        val settings = Settings(context)
        val matches = MatchRepository.get(context).cachedMatches()
        val plan = AlarmPlan.plan(matches, settings.alarmPrefs(), Instant.now())

        val am = context.getSystemService(AlarmManager::class.java)
        val wanted = plan.map { it.key }.toSet()
        for (key in settings.scheduledAlarmKeys - wanted) {
            val (kind, id) = key.split("|", limit = 2)
            am.cancel(pendingIntent(context, AlarmKind.valueOf(kind), id))
        }
        plan.forEach { schedule(context, it) }
        settings.scheduledAlarmKeys = wanted
    }

    fun schedule(context: Context, alarm: PlannedAlarm) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, alarm.kind, alarm.matchId)
        val at = alarm.at.toEpochMilli()
        when {
            alarm.kind == AlarmKind.WAKE && canExact(am) -> {
                val show = PendingIntent.getActivity(
                    context, 0, MainActivity.openMatchIntent(context, alarm.matchId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            }
            canExact(am) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun canExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, kind: AlarmKind, matchId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(kind.name)
            .setData(Uri.parse("coys://alarm/${kind.name}/$matchId"))
            .putExtra(AlarmReceiver.EXTRA_MATCH_ID, matchId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

fun Settings.alarmPrefs() = AlarmPrefs(
    kickoffReminder = kickoffReminder,
    reminderMinutes = reminderMinutes,
    lineupAlert = lineupAlert,
    wakeLeadMinutes = wakeLeadMinutes,
    wakeIds = wakeAlarmMatchIds,
    lineupNotifiedIds = lineupNotifiedIds,
)
