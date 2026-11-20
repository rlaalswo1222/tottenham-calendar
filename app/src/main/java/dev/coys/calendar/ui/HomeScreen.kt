package dev.coys.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.ui.theme.LossColor
import dev.coys.calendar.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenMatch: (String) -> Unit, onOpenSettings: () -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val inMonth = vm.matches.filter { YearMonth.from(Format.localDate(it.kickoff)) == month }
    val next = vm.matches.firstOrNull { it.state != MatchState.FINISHED && it.kickoff.isAfter(Instant.now().minusSeconds(7200)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("토트넘 일정", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                actions = {
                    if (vm.refreshing) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "새로고침") }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "설정") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.error?.let { err ->
                item { Text(err, color = LossColor, fontSize = 13.sp) }
            }
            if (next != null) {
                item { NextMatchCard(next, onClick = { onOpenMatch(next.id) }) }
            }
            item {
                MonthCalendar(
                    month = month,
                    matches = inMonth,
                    onPrev = { monthText = month.minusMonths(1).toString() },
                    onNext = { monthText = month.plusMonths(1).toString() },
                    onOpenMatch = onOpenMatch,
                )
            }
            if (inMonth.isEmpty() && !vm.refreshing) {
                item {
                    Text(
                        if (vm.matches.isEmpty()) "아직 데이터가 없어요. 새로고침을 눌러 주세요." else "이 달에는 경기가 없어요.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(inMonth, key = { it.id }) { m -> MatchRow(m, onClick = { onOpenMatch(m.id) }) }
        }
    }
}

@Composable
private fun NextMatchCard(m: Match, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (m.state == MatchState.LIVE) "진행 중" else "다음 경기",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), fontSize = 12.sp,
                )
                Text(
                    "${if (m.spursIsHome) "vs" else "@"} ${m.opponent.name}",
                    color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(
                    "${Format.dateTime(m.kickoff)} · ${m.competition}",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f), fontSize = 13.sp,
                )
            }
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Crest(m.opponent, 40.dp)
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    matches: List<Match>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenMatch: (String) -> Unit,
) {
    val byDay = matches.associateBy { Format.localDate(it.kickoff).dayOfMonth }
    val blanks = Format.leadingBlanks(month)
    val weeks = (blanks + month.lengthOfMonth() + 6) / 7
    val today = LocalDate.now()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
                Text(
                    Format.month(month), Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
            }
            Row {
                Format.WEEKDAYS.forEachIndexed { i, d ->
                    Text(
                        d, Modifier.weight(1f), fontSize = 12.sp,
                        color = if (i == 0) LossColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            for (w in 0 until weeks) {
                Row {
                    for (d in 0 until 7) {
                        val day = w * 7 + d - blanks + 1
                        val valid = day in 1..month.lengthOfMonth()
                        val match = if (valid) byDay[day] else null
                        val isToday = valid && month.atDay(day) == today
                        Column(
                            Modifier.weight(1f).aspectRatio(0.78f).padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primaryContainer
                                        match != null -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Transparent
                                    },
                                )
                                .then(if (match != null) Modifier.clickable { onOpenMatch(match.id) } else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (valid) {
                                Text(
                                    "$day", fontSize = 11.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (d == 0) LossColor else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (match != null) {
                                Crest(match.opponent, 24.dp)
                                CellLabel(match)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellLabel(m: Match) {
    val (text, color) = when (m.state) {
        MatchState.FINISHED -> (m.spursScoreline ?: "") to m.outcome.color()
        MatchState.LIVE -> "LIVE" to LossColor
        MatchState.POSTPONED -> "연기" to MaterialTheme.colorScheme.onSurfaceVariant
        MatchState.SCHEDULED -> Format.time(m.kickoff) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text, fontSize = 10.sp, color = color, maxLines = 1,
        fontWeight = if (m.state == MatchState.FINISHED) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
fun MatchRow(m: Match, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(Format.shortDate(m.kickoff), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (m.spursIsHome) "홈" else "원정", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Crest(m.opponent, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.opponent.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.competition, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            when (m.state) {
                MatchState.FINISHED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.spursScoreline ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    OutcomeBadge(m.outcome)
                }
                MatchState.LIVE -> Text("LIVE", color = LossColor, fontWeight = FontWeight.Bold)
                MatchState.POSTPONED -> Text("연기", color = MaterialTheme.colorScheme.onSurfaceVariant)
                MatchState.SCHEDULED -> Text(Format.time(m.kickoff), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
