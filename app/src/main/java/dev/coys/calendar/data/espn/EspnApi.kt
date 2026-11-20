package dev.coys.calendar.data.espn

import dev.coys.calendar.data.SPURS_ID
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** ESPN's unofficial, keyless site API. Endpoints can change without notice. */
object EspnApi {
    private const val BASE = "https://site.api.espn.com/apis/site/v2/sports/soccer"

    /** Competitions whose upcoming ties don't always show in the "all" fixtures list. */
    val CUP_LEAGUES = listOf("eng.fa", "eng.league_cup", "uefa.champions", "uefa.europa", "uefa.europa.conf")

    const val FRIENDLY_SLUG = "club.friendly"

    fun resultsUrl() = "$BASE/all/teams/$SPURS_ID/schedule"
    fun fixturesUrl(league: String = "all") = "$BASE/$league/teams/$SPURS_ID/schedule?fixture=true"
    fun summaryUrl(league: String, eventId: String) = "$BASE/$league/summary?event=$eventId"

    /** ESPN's image resizer; turns a 500px crest into a widget-sized one. */
    fun smallLogoUrl(logoUrl: String, px: Int = 96): String {
        val prefix = "https://a.espncdn.com"
        return if (logoUrl.startsWith(prefix)) {
            "$prefix/combiner/i?img=${logoUrl.removePrefix(prefix)}&w=$px&h=$px"
        } else logoUrl
    }

    fun getText(url: String): String = String(getBytes(url), Charsets.UTF_8)

    fun getBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android)")
        conn.setRequestProperty("Accept", "application/json, image/*")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }
}
