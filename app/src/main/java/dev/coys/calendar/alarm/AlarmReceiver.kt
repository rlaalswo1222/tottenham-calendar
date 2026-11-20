package dev.coys.calendar.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.coys.calendar.Settings
import dev.coys.calendar.data.MatchDetail
import dev.coys.calendar.data.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * Fires for kickoff reminders, line-up polls and wake alarms. Network work runs
 * inside goAsync(): an exact alarm briefly exempts the app from Doze, which a
 * deferred WorkManager job would not get.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val matchId = intent.getStringExtra(EXTRA_MATCH_ID) ?: return
        when (intent.action) {
            ACTION_DISMISS_WAKE -> Notifier.dismissWake(context, matchId)
            AlarmKind.WAKE.name -> {
                val match = MatchRepository.get(context).cachedMatch(matchId) ?: return
                Notifier.showWakeAlarm(context, match)
            }
            AlarmKind.REMINDER.name, AlarmKind.LINEUP_CHECK.name -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        handleMatchAlarm(context, intent.action!!, matchId)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun handleMatchAlarm(context: Context, action: String, matchId: String) {
        val repo = MatchRepository.get(context)
        val settings = Settings(context)
        val match = repo.cachedMatch(matchId) ?: return
        val detail: MatchDetail? = withTimeoutOrNull(8_000) {
            runCatching { repo.refreshDetail(match) }.getOrNull()
        } ?: repo.cachedDetail(matchId)
        val lineupReady = detail?.lineupsAnnounced == true && settings.lineupAlert

        when (action) {
            AlarmKind.REMINDER.name -> {
                Notifier.showMatchNotification(context, match, if (lineupReady) detail else null, reminder = true)
                if (lineupReady) settings.lineupNotifiedIds += matchId
            }
            AlarmKind.LINEUP_CHECK.name -> {
                if (matchId in settings.lineupNotifiedIds || !settings.lineupAlert) return
                if (lineupReady) {
                    Notifier.showMatchNotification(context, match, detail, reminder = false)
                    settings.lineupNotifiedIds += matchId
                } else {
                    AlarmPlan.nextLineupPoll(match.kickoff, Instant.now())?.let {
                        AlarmScheduler.schedule(context, PlannedAlarm(AlarmKind.LINEUP_CHECK, matchId, it))
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"
        const val ACTION_DISMISS_WAKE = "dev.coys.calendar.DISMISS_WAKE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
