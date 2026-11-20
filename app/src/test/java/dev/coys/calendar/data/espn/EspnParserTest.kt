package dev.coys.calendar.data.espn

import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EspnParserTest {

    private fun fixture(name: String): String =
        javaClass.getResource("/espn/$name")!!.readText()

    @Test fun `results include finished league, cup and friendly matches`() {
        val matches = EspnParser.parseSchedule(fixture("team_results.json"))
        assertEquals(17, matches.size)
        assertTrue(matches.all { it.state == MatchState.FINISHED })
        assertEquals(setOf("eng.1", "eng.league_cup", "club.friendly"), matches.map { it.leagueSlug }.toSet())
    }

    @Test fun `finished away match has score, venue and outcome from Spurs view`() {
        val leeds = EspnParser.parseSchedule(fixture("team_results.json")).first { it.id == "401878758" }
        assertEquals(Instant.parse("2026-11-07T12:30:00Z"), leeds.kickoff)
        assertEquals("Leeds United", leeds.home.name)
        assertFalse(leeds.spursIsHome)
        assertEquals("Leeds United", leeds.opponent.name)
        assertEquals("Elland Road", leeds.venue)
        assertEquals("Premier League", leeds.competition)
        assertNotNull(leeds.home.logoUrl)
        // Leeds were marked winner.
        assertEquals(Outcome.LOSS, leeds.outcome)
    }

    @Test fun `cup loss uses winner flag`() {
        val liv = EspnParser.parseSchedule(fixture("team_results.json"))
            .first { it.leagueSlug == "eng.league_cup" && it.home.abbreviation == "LIV" }
        assertEquals(3, liv.homeScore)
        assertEquals(1, liv.awayScore)
        assertEquals("1-3", liv.spursScoreline)
        assertEquals(Outcome.LOSS, liv.outcome)
    }

    @Test fun `fixtures are scheduled with no score`() {
        val fixtures = EspnParser.parseSchedule(fixture("team_fixtures.json"))
        assertEquals(28, fixtures.size)
        val ipswich = fixtures.first { it.id == "401878754" }
        assertEquals(MatchState.SCHEDULED, ipswich.state)
        assertTrue(ipswich.spursIsHome)
        assertEquals("Ipswich Town", ipswich.opponent.name)
        assertNull(ipswich.homeScore)
        assertNull(ipswich.outcome)
        assertEquals(Instant.parse("2026-11-21T15:00:00Z"), ipswich.kickoff)
    }

    @Test fun `finished summary has both lineups, form, head to head and table`() {
        val d = EspnParser.parseSummary(fixture("summary_finished.json"))
        assertEquals("401878758", d.match?.id)
        assertEquals(MatchState.FINISHED, d.match?.state)

        assertTrue(d.lineupsAnnounced)
        val spurs = d.lineups.first { it.team.id == "367" }
        assertEquals("4-3-3", spurs.formation)
        assertEquals(11, spurs.starters.size)
        assertEquals("Antonín Kinsky", spurs.starters.first().name)
        assertEquals(9, spurs.subs.size)

        assertEquals(2, d.form.size)
        val spursForm = d.form.first { it.teamId == "367" }
        assertEquals(5, spursForm.games.size)
        // Most recent first: 2-0 home win vs Palace.
        assertEquals("Crystal Palace", spursForm.games.first().opponentName)
        assertEquals("2-0", spursForm.games.first().scoreline)
        assertEquals(Outcome.WIN, spursForm.games.first().outcome)
        // 3-2 home defeat to Villa reads as 2-3 for Spurs.
        val villa = spursForm.games.first { it.opponentName == "Aston Villa" }
        assertEquals(Outcome.LOSS, villa.outcome)

        val h2h = d.headToHead!!
        assertEquals(5, h2h.games.size)
        assertEquals("TOT leads series 4-0-1", h2h.summary)

        assertEquals(20, d.standings.size)
        assertEquals(1, d.standings.first().rank)
        assertEquals("Arsenal", d.standings.first().teamName)
        assertEquals(27, d.standings.first().points)
    }

    @Test fun `upcoming summary has no lineups yet but still has form and table`() {
        val d = EspnParser.parseSummary(fixture("summary_upcoming.json"))
        assertEquals(MatchState.SCHEDULED, d.match?.state)
        assertFalse(d.lineupsAnnounced)
        assertEquals(2, d.form.size)
        assertTrue(d.standings.isNotEmpty())
    }

    @Test fun `parses timestamps with and without seconds`() {
        assertEquals(Instant.parse("2026-11-21T15:00:00Z"), EspnParser.parseInstant("2026-11-21T15:00Z"))
        assertEquals(Instant.parse("2026-05-11T19:00:00Z"), EspnParser.parseInstant("2026-05-11T19:00:00Z"))
        assertNull(EspnParser.parseInstant("nope"))
    }
}
