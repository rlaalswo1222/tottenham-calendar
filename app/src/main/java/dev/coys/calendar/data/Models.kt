package dev.coys.calendar.data

import java.time.Instant

/** ESPN team id for Tottenham Hotspur. */
const val SPURS_ID = "367"

data class Team(
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logoUrl: String?,
)

enum class MatchState { SCHEDULED, LIVE, FINISHED, POSTPONED }

/** Result from Tottenham's point of view. */
enum class Outcome { WIN, DRAW, LOSS }

data class Match(
    val id: String,
    val leagueSlug: String,
    val competition: String,
    val kickoff: Instant,
    val home: Team,
    val away: Team,
    val homeScore: Int?,
    val awayScore: Int?,
    /** Which side ESPN marked as winner (covers penalty shoot-outs). null = nobody / not finished. */
    val homeWon: Boolean?,
    val state: MatchState,
    val venue: String?,
) {
    val spursIsHome: Boolean get() = home.id == SPURS_ID
    val opponent: Team get() = if (spursIsHome) away else home

    val outcome: Outcome?
        get() {
            if (state != MatchState.FINISHED) return null
            return when (homeWon) {
                null -> Outcome.DRAW
                spursIsHome -> Outcome.WIN
                else -> Outcome.LOSS
            }
        }

    /** "2-1" from Tottenham's perspective, or null when there is no score yet. */
    val spursScoreline: String?
        get() {
            val h = homeScore ?: return null
            val a = awayScore ?: return null
            return if (spursIsHome) "$h-$a" else "$a-$h"
        }
}

data class LineupPlayer(
    val name: String,
    val shortName: String,
    val jersey: String,
    val position: String,
    /** Opta-style formation slot, 1..11 for starters, 0 for subs. */
    val formationPlace: Int,
    val starter: Boolean,
    val subbedIn: Boolean,
    val subbedOut: Boolean,
)

data class TeamLineup(
    val team: Team,
    val formation: String?,
    val starters: List<LineupPlayer>,
    val subs: List<LineupPlayer>,
) {
    val announced: Boolean get() = starters.size == 11
}

data class FormGame(
    val date: Instant,
    val opponentName: String,
    val opponentLogo: String?,
    val home: Boolean,
    /** Goals for-against, from this team's perspective. */
    val scoreline: String,
    val outcome: Outcome?,
)

data class TeamForm(val teamId: String, val teamName: String, val games: List<FormGame>)

data class HeadToHeadGame(
    val date: Instant,
    val homeAbbr: String,
    val awayAbbr: String,
    val homeScore: String,
    val awayScore: String,
    val competition: String?,
)

data class HeadToHead(val summary: String?, val games: List<HeadToHeadGame>)

data class StandingRow(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val logoUrl: String?,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalDiff: String,
    val points: Int,
)

data class MatchDetail(
    val match: Match?,
    /** Home lineup first. Empty or unannounced before the clubs publish team sheets. */
    val lineups: List<TeamLineup>,
    val form: List<TeamForm>,
    val headToHead: HeadToHead?,
    val standingsTitle: String?,
    val standings: List<StandingRow>,
) {
    val lineupsAnnounced: Boolean get() = lineups.isNotEmpty() && lineups.all { it.announced }
}
