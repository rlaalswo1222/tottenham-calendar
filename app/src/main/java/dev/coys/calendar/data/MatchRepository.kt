package dev.coys.calendar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.coys.calendar.data.espn.EspnApi
import dev.coys.calendar.data.espn.EspnParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Single entry point for match data. Raw ESPN responses are cached on disk and
 * parsed on read, so everything (widget, alarms, UI) works offline from the
 * last successful refresh.
 */
class MatchRepository private constructor(context: Context) {

    private val dir = File(context.filesDir, "espn").apply { mkdirs() }
    private val crestDir = File(context.filesDir, "crests").apply { mkdirs() }

    @Volatile private var memo: Pair<Long, List<Match>>? = null

    // ---- schedule ----

    /** Fetches results + fixtures. Throws if the core lists can't be fetched. */
    suspend fun refreshSchedule(): List<Match> = withContext(Dispatchers.IO) {
        coroutineScope {
            val core = listOf(EspnApi.resultsUrl(), EspnApi.fixturesUrl())
                .map { url -> async { url to EspnApi.getText(url) } }
            val cups = EspnApi.CUP_LEAGUES.map { EspnApi.fixturesUrl(it) }
                .map { url -> async { runCatching { url to EspnApi.getText(url) }.getOrNull() } }
            val fetched = core.awaitAll() + cups.awaitAll().filterNotNull()
            // Validate before overwriting the cache.
            fetched.forEach { (_, body) -> EspnParser.parseSchedule(body) }
            fetched.forEach { (url, body) -> scheduleFile(url).writeText(body) }
        }
        memo = null
        prefetchCrests(cachedMatches())
        cachedMatches()
    }

    /** All competitive matches from cache, oldest first. Friendlies are excluded. */
    fun cachedMatches(): List<Match> {
        val files = scheduleUrls().map { scheduleFile(it) }.filter { it.exists() }
        val stamp = files.sumOf { it.lastModified() }
        memo?.let { (s, list) -> if (s == stamp) return list }
        val merged = HashMap<String, Match>()
        for (f in files) {
            val parsed = runCatching { EspnParser.parseSchedule(f.readText()) }.getOrDefault(emptyList())
            for (m in parsed) {
                if (m.leagueSlug == EspnApi.FRIENDLY_SLUG) continue
                val existing = merged[m.id]
                if (existing == null || rank(m.state) >= rank(existing.state)) merged[m.id] = m
            }
        }
        val list = merged.values.sortedBy { it.kickoff }
        memo = stamp to list
        return list
    }

    fun cachedMatch(id: String): Match? = cachedMatches().firstOrNull { it.id == id }

    private fun rank(s: MatchState) = when (s) {
        MatchState.SCHEDULED -> 0
        MatchState.POSTPONED -> 1
        MatchState.LIVE -> 2
        MatchState.FINISHED -> 3
    }

    private fun scheduleUrls() =
        listOf(EspnApi.resultsUrl(), EspnApi.fixturesUrl()) + EspnApi.CUP_LEAGUES.map { EspnApi.fixturesUrl(it) }

    private fun scheduleFile(url: String) = File(dir, "schedule_${hash(url)}.json")

    // ---- match detail ----

    suspend fun refreshDetail(match: Match): MatchDetail = withContext(Dispatchers.IO) {
        val body = EspnApi.getText(EspnApi.summaryUrl(match.leagueSlug.ifEmpty { "all" }, match.id))
        val detail = EspnParser.parseSummary(body)
        detailFile(match.id).writeText(body)
        detail
    }

    fun cachedDetail(matchId: String): MatchDetail? {
        val f = detailFile(matchId)
        if (!f.exists()) return null
        return runCatching { EspnParser.parseSummary(f.readText()) }.getOrNull()
    }

    private fun detailFile(id: String) = File(dir, "summary_$id.json")

    // ---- crests ----

    fun cachedCrest(team: Team): Bitmap? {
        val f = crestFile(team.id)
        return if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    suspend fun crest(team: Team): Bitmap? = withContext(Dispatchers.IO) {
        cachedCrest(team) ?: run {
            val url = team.logoUrl ?: return@run null
            runCatching {
                val bytes = EspnApi.getBytes(EspnApi.smallLogoUrl(url))
                crestFile(team.id).writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    suspend fun prefetchCrests(matches: List<Match>) {
        matches.flatMap { listOf(it.home, it.away) }.distinctBy { it.id }
            .filter { !crestFile(it.id).exists() }
            .forEach { crest(it) }
    }

    private fun crestFile(teamId: String) = File(crestDir, "$teamId.png")

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    companion object {
        @Volatile private var instance: MatchRepository? = null
        fun get(context: Context): MatchRepository =
            instance ?: synchronized(this) {
                instance ?: MatchRepository(context.applicationContext).also { instance = it }
            }
    }
}
