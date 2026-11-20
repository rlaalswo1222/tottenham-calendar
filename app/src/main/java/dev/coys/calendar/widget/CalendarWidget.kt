package dev.coys.calendar.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.width
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.coys.calendar.MainActivity
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchRepository
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.Outcome
import dev.coys.calendar.ui.Format
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CalendarWidget()
}

/** 4×4 month calendar: opponent crest on match days, score/result colour once played. */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = MatchRepository.get(context)
        val matches = repo.cachedMatches()
        // All crests up front (small, cached on disk) so month paging needs no reload.
        val crests = matches.map { it.opponent }.distinctBy { it.id }
            .associate { it.id to repo.cachedCrest(it) }
        val next = matches.firstOrNull {
            it.state != MatchState.FINISHED && it.kickoff.isAfter(Instant.now() - Duration.ofHours(2))
        }
        provideContent {
            val offset = currentState(MonthOffsetKey) ?: 0
            val today = LocalDate.now()
            val month = YearMonth.from(today).plusMonths(offset.toLong())
            val byDay = matches.filter { YearMonth.from(Format.localDate(it.kickoff)) == month }
                .groupBy { Format.localDate(it.kickoff).dayOfMonth }
                .mapValues { it.value.first() }
            WidgetContent(context, month, today, byDay, crests, next)
        }
    }
}

/** Months away from the current one; stored per widget instance. */
internal val MonthOffsetKey = intPreferencesKey("month_offset")
private val DeltaKey = ActionParameters.Key<Int>("delta")

/** ‹ / › page by one month; delta 0 jumps back to the current month. */
class ChangeMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DeltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MonthOffsetKey] = if (delta == 0) 0 else (prefs[MonthOffsetKey] ?: 0) + delta
        }
        CalendarWidget().update(context, glanceId)
    }
}

private fun changeMonth(delta: Int) = actionRunCallback<ChangeMonthAction>(actionParametersOf(DeltaKey to delta))

private object W {
    val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF141A2E))
    val header = ColorProvider(day = Color(0xFF132257), night = Color(0xFF0B1020))
    val onHeader = ColorProvider(day = Color.White, night = Color.White)
    val text = ColorProvider(day = Color(0xFF1B1F2A), night = Color(0xFFE6E8F0))
    val muted = ColorProvider(day = Color(0xFF8A8F98), night = Color(0xFF8A90A6))
    val sunday = ColorProvider(day = Color(0xFFD64545), night = Color(0xFFFF7A7A))
    val today = ColorProvider(day = Color(0xFFDDE2F3), night = Color(0xFF2A3A7A))
    val home = ColorProvider(day = Color(0xFFF1F3FA), night = Color(0xFF1C2440))
    val win = ColorProvider(day = Color(0xFF2E9E4F), night = Color(0xFF5BC67A))
    val draw = ColorProvider(day = Color(0xFF8A8F98), night = Color(0xFFA0A6B4))
    val loss = ColorProvider(day = Color(0xFFD64545), night = Color(0xFFFF7A7A))
}

@Composable
private fun WidgetContent(
    context: Context,
    month: YearMonth,
    today: LocalDate,
    byDay: Map<Int, Match>,
    crests: Map<String, Bitmap?>,
    next: Match?,
) {
    val blanks = Format.leadingBlanks(month)
    val weeks = (blanks + month.lengthOfMonth() + 6) / 7
    val size = LocalSize.current
    // 34dp header + 26dp weekday row + 10dp bottom padding.
    val rowHeight = (size.height - 70.dp) / weeks
    val cellWidth = size.width / 7
    // Leave room for the day number and label; about two-thirds of a cell's width.
    val crestSize = minOf(rowHeight - 28.dp, cellWidth * 0.65f).coerceIn(12.dp, 24.dp)

    Column(
        GlanceModifier.fillMaxSize().background(W.bg).cornerRadius(16.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Row(
            GlanceModifier.fillMaxWidth().height(34.dp).background(W.header).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavArrow("‹", changeMonth(-1))
            Text(
                if (month.year == today.year) "${month.monthValue}월" else "${month.year}년 ${month.monthValue}월",
                modifier = GlanceModifier.clickable(changeMonth(0)),
                style = TextStyle(color = W.onHeader, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            NavArrow("›", changeMonth(1))
            Text(
                next?.let { "다음 ${Format.shortDate(it.kickoff)} ${Format.time(it.kickoff)} ${if (it.spursIsHome) "vs" else "@"} ${it.opponent.abbreviation}" }
                    ?: "",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = W.onHeader, fontSize = 11.sp, textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
        Row(
            GlanceModifier.fillMaxWidth().height(26.dp).padding(start = 2.dp, end = 2.dp, top = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Format.WEEKDAYS.forEachIndexed { i, d ->
                Text(
                    d,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = if (i == 0) W.sunday else W.muted,
                        fontSize = 10.sp, textAlign = TextAlign.Center,
                    ),
                )
            }
        }
        Column(GlanceModifier.fillMaxWidth().defaultWeight().padding(start = 2.dp, end = 2.dp, bottom = 10.dp)) {
            for (w in 0 until weeks) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (d in 0 until 7) {
                        val day = w * 7 + d - blanks + 1
                        val inMonth = day in 1..month.lengthOfMonth()
                        DayCell(
                            context = context,
                            day = if (inMonth) day else null,
                            sunday = d == 0,
                            isToday = inMonth && month.atDay(day) == today,
                            match = if (inMonth) byDay[day] else null,
                            crests = crests,
                            crestSize = crestSize,
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/** Full-height, 34dp-wide tap target so the arrows are easy to hit. */
@Composable
private fun NavArrow(glyph: String, action: androidx.glance.action.Action) {
    Box(
        GlanceModifier.width(34.dp).fillMaxHeight().clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TextStyle(color = W.onHeader, fontSize = 22.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun DayCell(
    context: Context,
    day: Int?,
    sunday: Boolean,
    isToday: Boolean,
    match: Match?,
    crests: Map<String, Bitmap?>,
    crestSize: androidx.compose.ui.unit.Dp,
    modifier: GlanceModifier,
) {
    Box(modifier.padding(1.dp)) {
        var inner = GlanceModifier.fillMaxSize().cornerRadius(6.dp)
        when {
            isToday -> inner = inner.background(W.today)
            match != null && match.spursIsHome -> inner = inner.background(W.home)
        }
        if (match != null) {
            inner = inner.clickable(actionStartActivity(MainActivity.openMatchIntent(context, match.id)))
        }
        Column(inner, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day?.toString() ?: "",
                style = TextStyle(
                    color = if (sunday) W.sunday else W.text,
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            if (match != null) {
                val crest = crests[match.opponent.id]
                if (crest != null) {
                    Image(
                        ImageProvider(crest),
                        contentDescription = match.opponent.name,
                        modifier = GlanceModifier.size(crestSize),
                    )
                } else {
                    Text(
                        match.opponent.abbreviation,
                        style = TextStyle(color = W.text, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    )
                }
                val (label, color) = when (match.state) {
                    MatchState.FINISHED -> (match.spursScoreline ?: "") to when (match.outcome) {
                        Outcome.WIN -> W.win
                        Outcome.LOSS -> W.loss
                        else -> W.draw
                    }
                    MatchState.LIVE -> "LIVE" to W.loss
                    MatchState.POSTPONED -> "연기" to W.muted
                    MatchState.SCHEDULED -> Format.time(match.kickoff) to W.muted
                }
                Text(
                    label,
                    style = TextStyle(
                        color = color, fontSize = 9.sp,
                        fontWeight = if (match.state == MatchState.FINISHED) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
