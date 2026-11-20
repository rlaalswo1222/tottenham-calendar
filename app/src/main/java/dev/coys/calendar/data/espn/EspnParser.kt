package dev.coys.calendar.data.espn

import dev.coys.calendar.data.FormGame
import dev.coys.calendar.data.HeadToHead
import dev.coys.calendar.data.HeadToHeadGame
import dev.coys.calendar.data.LineupPlayer
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchDetail
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.Outcome
import dev.coys.calendar.data.StandingRow
import dev.coys.calendar.data.Team
import dev.coys.calendar.data.TeamForm
import dev.coys.calendar.data.TeamLineup
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Turns ESPN's unofficial site API JSON into app models.
 * Pure (no Android or network), so it is unit-tested against saved responses.
 * Every field is read defensively: ESPN changes shape without notice.
 */
object EspnParser {

    /** `/teams/{id}/schedule` (results or `?fixture=true`). */
    fun parseSchedule(json: String): List<Match> {
        val events = JSONObject(json).optJSONArray("events") ?: return emptyList()
        return events.objects().mapNotNull { parseEvent(it) }
    }

    /** `/{league}/summary?event={id}`. */
    fun parseSummary(json: String): MatchDetail {
        val root = JSONObject(json)
        return MatchDetail(
            match = parseHeader(root.optJSONObject("header")),
            lineups = root.optJSONArray("rosters")?.objects()?.mapNotNull { parseLineup(it) }.orEmpty(),
            form = root.optJSONArray("lastFiveGames")?.objects()?.mapNotNull { parseForm(it) }.orEmpty(),
            headToHead = root.optJSONArray("seasonseries")?.optJSONObject(0)?.let { parseHeadToHead(it) },
            standingsTitle = root.optJSONObject("standings")?.optStringOrNull("header"),
            standings = parseStandings(root.optJSONObject("standings")),
        )
    }

    fun parseInstant(s: String): Instant? =
        runCatching { Instant.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()

    // ---- events ----

    private fun parseEvent(e: JSONObject): Match? {
        val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        val league = e.optJSONObject("league")
        return parseCompetition(
            id = e.optStringOrNull("id") ?: comp.optStringOrNull("id") ?: return null,
            date = e.optStringOrNull("date") ?: comp.optStringOrNull("date"),
            comp = comp,
            league = league,
            fallbackCompetition = e.optJSONObject("seasonType")?.optStringOrNull("name"),
        )
    }

    private fun parseHeader(h: JSONObject?): Match? {
        h ?: return null
        val comp = h.optJSONArray("competitions")?.optJSONObject(0) ?: return null
        return parseCompetition(
            id = h.optStringOrNull("id") ?: comp.optStringOrNull("id") ?: return null,
            date = comp.optStringOrNull("date"),
            comp = comp,
            league = h.optJSONObject("league"),
            fallbackCompetition = null,
        )
    }

    private fun parseCompetition(
        id: String,
        date: String?,
        comp: JSONObject,
        league: JSONObject?,
        fallbackCompetition: String?,
    ): Match? {
        val kickoff = date?.let { parseInstant(it) } ?: return null
        val competitors = comp.optJSONArray("competitors")?.objects().orEmpty()
        val home = competitors.firstOrNull { it.optString("homeAway") == "home" } ?: return null
        val away = competitors.firstOrNull { it.optString("homeAway") == "away" } ?: return null
        val state = parseState(comp.optJSONObject("status")?.optJSONObject("type"))
        val scored = state == MatchState.FINISHED || state == MatchState.LIVE
        val homeWon = when {
            state != MatchState.FINISHED -> null
            home.optBoolean("winner") -> true
            away.optBoolean("winner") -> false
            else -> null
        }
        return Match(
            id = id,
            leagueSlug = league?.optStringOrNull("slug").orEmpty(),
            competition = league?.optStringOrNull("abbreviation")
                ?: league?.optStringOrNull("name") ?: fallbackCompetition ?: "",
            kickoff = kickoff,
            home = parseTeam(home.optJSONObject("team")) ?: return null,
            away = parseTeam(away.optJSONObject("team")) ?: return null,
            homeScore = if (scored) parseScore(home.opt("score")) else null,
            awayScore = if (scored) parseScore(away.opt("score")) else null,
            homeWon = homeWon,
            state = state,
            venue = comp.optJSONObject("venue")?.optStringOrNull("fullName"),
        )
    }

    private fun parseState(type: JSONObject?): MatchState {
        type ?: return MatchState.SCHEDULED
        val name = type.optString("name")
        return when {
            "POSTPONED" in name || "CANCELED" in name || "ABANDONED" in name || "SUSPENDED" in name ->
                MatchState.POSTPONED
            type.optString("state") == "in" -> MatchState.LIVE
            type.optString("state") == "post" && type.optBoolean("completed") -> MatchState.FINISHED
            else -> MatchState.SCHEDULED
        }
    }

    private fun parseScore(raw: Any?): Int? = when (raw) {
        is JSONObject -> raw.optStringOrNull("displayValue")?.toIntOrNull()
            ?: raw.optDouble("value").takeUnless { it.isNaN() }?.toInt()
        is String -> raw.toIntOrNull()
        is Number -> raw.toInt()
        else -> null
    }

    private fun parseTeam(t: JSONObject?): Team? {
        t ?: return null
        val id = t.optStringOrNull("id") ?: return null
        val name = t.optStringOrNull("displayName") ?: return null
        return Team(
            id = id,
            name = name,
            shortName = t.optStringOrNull("shortDisplayName") ?: name,
            abbreviation = t.optStringOrNull("abbreviation") ?: name.take(3).uppercase(),
            logoUrl = logoOf(t),
        )
    }

    /** Logo can be a string, or an array of {href} under "logos"/"logo". */
    private fun logoOf(o: JSONObject): String? {
        o.optStringOrNull("logo")?.takeIf { it.startsWith("http") }?.let { return it }
        for (key in listOf("logos", "logo")) {
            val arr = o.optJSONArray(key) ?: continue
            arr.objects().firstOrNull()?.optStringOrNull("href")?.let { return it }
        }
        return null
    }

    // ---- lineups ----

    private fun parseLineup(r: JSONObject): TeamLineup? {
        val team = parseTeam(r.optJSONObject("team")) ?: return null
        val players = r.optJSONArray("roster")?.objects()?.mapNotNull { p ->
            val a = p.optJSONObject("athlete") ?: return@mapNotNull null
            val name = a.optStringOrNull("displayName") ?: return@mapNotNull null
            LineupPlayer(
                name = name,
                shortName = a.optStringOrNull("shortName") ?: name,
                jersey = p.optString("jersey"),
                position = p.optJSONObject("position")?.optStringOrNull("abbreviation").orEmpty(),
                formationPlace = p.optString("formationPlace").toIntOrNull() ?: 0,
                starter = p.optBoolean("starter"),
                subbedIn = p.optBoolean("subbedIn"),
                subbedOut = p.optBoolean("subbedOut"),
            )
        }.orEmpty()
        return TeamLineup(
            team = team,
            formation = r.optStringOrNull("formation"),
            starters = players.filter { it.starter }.sortedBy { it.formationPlace },
            subs = players.filterNot { it.starter },
        )
    }

    // ---- form ----

    private fun parseForm(f: JSONObject): TeamForm? {
        val team = f.optJSONObject("team") ?: return null
        val teamId = team.optStringOrNull("id") ?: return null
        val games = f.optJSONArray("events")?.objects()?.mapNotNull { e ->
            val date = e.optStringOrNull("gameDate")?.let { parseInstant(it) } ?: return@mapNotNull null
            val opp = e.optJSONObject("opponent") ?: return@mapNotNull null
            val isHome = e.optString("homeTeamId") == teamId
            val hs = e.optString("homeTeamScore")
            val aws = e.optString("awayTeamScore")
            FormGame(
                date = date,
                opponentName = opp.optString("displayName"),
                opponentLogo = logoOf(opp),
                home = isHome,
                scoreline = if (isHome) "$hs-$aws" else "$aws-$hs",
                outcome = when (e.optString("gameResult")) {
                    "W" -> Outcome.WIN
                    "D" -> Outcome.DRAW
                    "L" -> Outcome.LOSS
                    else -> null
                },
            )
        }.orEmpty().sortedByDescending { it.date }
        return TeamForm(teamId, team.optString("displayName"), games)
    }

    // ---- head to head ----

    private fun parseHeadToHead(s: JSONObject): HeadToHead {
        val games = s.optJSONArray("events")?.objects()?.mapNotNull { e ->
            val date = e.optStringOrNull("date")?.let { parseInstant(it) } ?: return@mapNotNull null
            val cs = e.optJSONArray("competitors")?.objects().orEmpty()
            val h = cs.firstOrNull { it.optString("homeAway") == "home" } ?: return@mapNotNull null
            val a = cs.firstOrNull { it.optString("homeAway") == "away" } ?: return@mapNotNull null
            HeadToHeadGame(
                date = date,
                homeAbbr = h.optJSONObject("team")?.optStringOrNull("abbreviation").orEmpty(),
                awayAbbr = a.optJSONObject("team")?.optStringOrNull("abbreviation").orEmpty(),
                homeScore = h.opt("score")?.let { parseScore(it)?.toString() } ?: "-",
                awayScore = a.opt("score")?.let { parseScore(it)?.toString() } ?: "-",
                competition = e.optStringOrNull("competitionName"),
            )
        }.orEmpty().sortedByDescending { it.date }
        return HeadToHead(summary = s.optStringOrNull("summary"), games = games)
    }

    // ---- standings ----

    private fun parseStandings(s: JSONObject?): List<StandingRow> {
        val entries = s?.optJSONArray("groups")?.optJSONObject(0)
            ?.optJSONObject("standings")?.optJSONArray("entries") ?: return emptyList()
        return entries.objects().mapNotNull { e ->
            val stats = e.optJSONArray("stats")?.objects().orEmpty()
                .associate { it.optString("name") to it.optString("displayValue") }
            fun int(name: String) = stats[name]?.removePrefix("+")?.toIntOrNull() ?: 0
            StandingRow(
                rank = int("rank"),
                teamId = e.optStringOrNull("id") ?: return@mapNotNull null,
                teamName = e.optStringOrNull("team") ?: e.optJSONObject("team")?.optStringOrNull("displayName") ?: "",
                logoUrl = logoOf(e),
                played = int("gamesPlayed"),
                won = int("wins"),
                drawn = int("ties"),
                lost = int("losses"),
                goalDiff = stats["pointDifferential"] ?: "0",
                points = int("points"),
            )
        }.sortedBy { it.rank }
    }

    // ---- helpers ----

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
}
