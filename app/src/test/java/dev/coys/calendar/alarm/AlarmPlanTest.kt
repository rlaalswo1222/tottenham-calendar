package dev.coys.calendar.alarm

import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.Team
import dev.coys.calendar.data.espn.EspnParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlarmPlanTest {
    private val spurs = Team("367", "Tottenham Hotspur", "Spurs", "TOT", null)
    private val ips = Team("373", "Ipswich Town", "Ipswich", "IPS", null)
    private val kickoff = Instant.parse("2026-11-21T15:00:00Z")

    private fun match(id: String = "1", at: Instant = kickoff, state: MatchState = MatchState.SCHEDULED) =
        Match(id, "eng.1", "Premier League", at, spurs, ips, null, null, null, state, null)

    private val prefs = AlarmPrefs(
        kickoffReminder = true, reminderMinutes = 30, lineupAlert = true,
        wakeLeadMinutes = 15, wakeIds = emptySet(), lineupNotifiedIds = emptySet(),
    )

    @Test fun `upcoming match gets reminder and lineup poll`() {
        val now = kickoff - Duration.ofHours(5)
        val plan = AlarmPlan.plan(listOf(match()), prefs, now)
        assertEquals(
            listOf(
                PlannedAlarm(AlarmKind.REMINDER, "1", kickoff - Duration.ofMinutes(30)),
                PlannedAlarm(AlarmKind.LINEUP_CHECK, "1", kickoff - Duration.ofMinutes(75)),
            ),
            plan,
        )
    }

    @Test fun `wake alarm only for opted-in matches`() {
        val now = kickoff - Duration.ofHours(5)
        val plan = AlarmPlan.plan(listOf(match()), prefs.copy(wakeIds = setOf("1")), now)
        assertTrue(PlannedAlarm(AlarmKind.WAKE, "1", kickoff - Duration.ofMinutes(15)) in plan)
    }

    @Test fun `inside the poll window the check starts almost immediately`() {
        val now = kickoff - Duration.ofMinutes(40)
        val check = AlarmPlan.plan(listOf(match()), prefs, now).first { it.kind == AlarmKind.LINEUP_CHECK }
        assertEquals(now.plusSeconds(5), check.at)
    }

    @Test fun `no lineup poll once notified, none for past, finished or far-off matches`() {
        val now = kickoff - Duration.ofHours(5)
        val notified = AlarmPlan.plan(listOf(match()), prefs.copy(lineupNotifiedIds = setOf("1")), now)
        assertTrue(notified.none { it.kind == AlarmKind.LINEUP_CHECK })

        val others = listOf(
            match("past", kickoff - Duration.ofDays(1)),
            match("done", state = MatchState.FINISHED),
            match("far", kickoff + Duration.ofDays(30)),
        )
        assertTrue(AlarmPlan.plan(others, prefs, now).isEmpty())
    }

    @Test fun `switches turn alarms off`() {
        val now = kickoff - Duration.ofHours(5)
        val off = prefs.copy(kickoffReminder = false, lineupAlert = false)
        assertTrue(AlarmPlan.plan(listOf(match()), off, now).isEmpty())
    }

    @Test fun `polling stops at kickoff`() {
        assertEquals(kickoff - Duration.ofMinutes(15), AlarmPlan.nextLineupPoll(kickoff, kickoff - Duration.ofMinutes(20)))
        assertNull(AlarmPlan.nextLineupPoll(kickoff, kickoff - Duration.ofMinutes(3)))
    }

    @Test fun `lineup summary reads back line first`() {
        val d = EspnParser.parseSummary(javaClass.getResource("/espn/summary_finished.json")!!.readText())
        val spursXi = d.lineups.first { it.team.id == "367" }
        val text = AlarmPlan.lineupSummary(spursXi)
        assertTrue(text, text.startsWith("4-3-3 · Kinsky / Robertson, van de Ven, van Hecke, Porro / "))
    }
}
