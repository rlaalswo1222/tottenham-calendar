package dev.coys.calendar.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.coys.calendar.MainActivity
import dev.coys.calendar.R
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchDetail
import dev.coys.calendar.data.SPURS_ID
import dev.coys.calendar.ui.Format
import java.time.Duration
import java.time.Instant

object Notifier {
    private const val CHANNEL_MATCH = "match"
    private const val CHANNEL_WAKE = "wake_alarm"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MATCH, "경기 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "킥오프 전 알림과 라인업 발표 알림"
            },
        )
        val alarmSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WAKE, "기상 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "새벽 경기용 알람. 끌 때까지 울려요."
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800)
                setBypassDnd(true)
            },
        )
    }

    /**
     * One notification per match: the kickoff reminder and the line-up alert share an id,
     * so whichever comes second replaces the first and carries both pieces of news.
     */
    fun showMatchNotification(context: Context, match: Match, lineup: MatchDetail?, reminder: Boolean) {
        val until = untilText(Duration.between(Instant.now(), match.kickoff))
        val vs = "${if (match.spursIsHome) "vs" else "@"} ${match.opponent.name}"
        val spursXi = lineup?.takeIf { it.lineupsAnnounced }?.lineups?.firstOrNull { it.team.id == SPURS_ID }
        val title = when {
            spursXi != null && reminder -> "⚽ $until 킥오프 · 라인업 발표"
            spursXi != null -> "📋 라인업 발표 · $until 킥오프"
            else -> "⚽ $until 킥오프"
        }
        val line = "$vs · ${match.competition} · ${Format.time(match.kickoff)}"
        val body = spursXi?.let { "$line\n${AlarmPlan.lineupSummary(it)}" } ?: line

        val n = NotificationCompat.Builder(context, CHANNEL_MATCH)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle(title)
            .setContentText(spursXi?.let { AlarmPlan.lineupSummary(it) } ?: line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openMatch(context, match.id))
            .setAutoCancel(true)
            .setTimeoutAfter(Duration.ofHours(3).toMillis())
            .build()
        post(context, match.id.hashCode(), n)
    }

    fun showWakeAlarm(context: Context, match: Match) {
        val fullScreen = PendingIntent.getActivity(
            context, match.id.hashCode(), WakeAlarmActivity.intent(context, match.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, match.id.hashCode(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_DISMISS_WAKE)
                .putExtra(AlarmReceiver.EXTRA_MATCH_ID, match.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_WAKE)
            .setSmallIcon(R.drawable.ic_stat_ball)
            .setContentTitle("⏰ 곧 킥오프: ${if (match.spursIsHome) "vs" else "@"} ${match.opponent.name}")
            .setContentText("${Format.time(match.kickoff)} · ${match.competition}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // Own group so system auto-grouping doesn't re-post it as silent.
            .setGroup("wake_${match.id}")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "끄기", dismiss)
            .setOngoing(true)
            .setTimeoutAfter(Duration.ofMinutes(10).toMillis())
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        post(context, wakeId(match.id), n)
    }

    fun dismissWake(context: Context, matchId: String) {
        NotificationManagerCompat.from(context).cancel(wakeId(matchId))
    }

    /** "곧", "25분 후", "3시간 10분 후", "2일 후". */
    internal fun untilText(d: Duration): String {
        val m = d.toMinutes()
        return when {
            m <= 0 -> "곧"
            m < 60 -> "${m}분 후"
            m < 48 * 60 -> if (m % 60 == 0L) "${m / 60}시간 후" else "${m / 60}시간 ${m % 60}분 후"
            else -> "${m / (24 * 60)}일 후"
        }
    }

    private fun wakeId(matchId: String) = matchId.hashCode() xor 0x5A5A

    private fun openMatch(context: Context, matchId: String) = PendingIntent.getActivity(
        context, matchId.hashCode(), MainActivity.openMatchIntent(context, matchId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(context: Context, id: Int, n: Notification) {
        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(id, n)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS revoked; nothing to do.
            }
        }
    }
}
