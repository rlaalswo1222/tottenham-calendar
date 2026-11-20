package dev.coys.calendar.alarm

import dev.coys.calendar.data.FormationLayout
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.TeamLineup
import java.time.Duration
import java.time.Instant

enum class AlarmKind { REMINDER, LINEUP_CHECK, WAKE }

data class PlannedAlarm(val kind: AlarmKind, val matchId: String, val at: Instant) {
    val key: String get() = "${kind.name}|$matchId"
}

data class AlarmPrefs(
    val kickoffReminder: Boolean,
    val reminderMinutes: Int,
    val lineupAlert: Boolean,
    val wakeLeadMinutes: Int,
    val wakeIds: Set<String>,
    val lineupNotifiedIds: Set<String>,
)

/** Decides which alarms should exist right now. Pure, so it is unit-tested. */
object AlarmPlan {
    /** Official team sheets come out ~60 min before kickoff; start a little earlier. */
    val LINEUP_POLL_START: Duration = Duration.ofMinutes(75)
    val LINEUP_POLL_INTERVAL: Duration = Duration.ofMinutes(5)
    private val HORIZON: Duration = Duration.ofDays(14)

    fun plan(matches: List<Match>, prefs: AlarmPrefs, now: Instant): List<PlannedAlarm> {
        val out = mutableListOf<PlannedAlarm>()
        for (m in matches) {
            if (m.state != MatchState.SCHEDULED) continue
            if (!m.kickoff.isAfter(now) || m.kickoff.isAfter(now + HORIZON)) continue

            if (prefs.kickoffReminder) {
                val at = m.kickoff - Duration.ofMinutes(prefs.reminderMinutes.toLong())
                if (at.isAfter(now)) out += PlannedAlarm(AlarmKind.REMINDER, m.id, at)
            }
            if (prefs.lineupAlert && m.id !in prefs.lineupNotifiedIds) {
                out += PlannedAlarm(AlarmKind.LINEUP_CHECK, m.id, maxOf(m.kickoff - LINEUP_POLL_START, now.plusSeconds(5)))
            }
            if (m.id in prefs.wakeIds) {
                val at = m.kickoff - Duration.ofMinutes(prefs.wakeLeadMinutes.toLong())
                if (at.isAfter(now)) out += PlannedAlarm(AlarmKind.WAKE, m.id, at)
            }
        }
        return out
    }

    /** Next line-up poll after a miss, or null once kickoff has passed. */
    fun nextLineupPoll(kickoff: Instant, now: Instant): Instant? {
        val next = now + LINEUP_POLL_INTERVAL
        return if (next.isBefore(kickoff)) next else null
    }

    /** "4-3-3 · Kinsky / Porro van Hecke van de Ven Robertson / …", back line first. */
    fun lineupSummary(lineup: TeamLineup): String {
        fun surname(short: String) = short.substringAfter(". ", short)
        val slots = FormationLayout.layout(lineup.formation, lineup.starters)
        val body = if (slots != null) {
            slots.groupBy { it.y }.toSortedMap().values.joinToString(" / ") { row ->
                row.sortedBy { it.x }.joinToString(", ") { surname(it.player.shortName) }
            }
        } else {
            lineup.starters.joinToString(", ") { surname(it.shortName) }
        }
        return listOfNotNull(lineup.formation, body).joinToString(" · ")
    }
}
