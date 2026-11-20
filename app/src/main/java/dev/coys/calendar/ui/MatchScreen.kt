package dev.coys.calendar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.coys.calendar.data.FormationLayout
import dev.coys.calendar.data.HeadToHead
import dev.coys.calendar.data.LineupPlayer
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchDetail
import dev.coys.calendar.data.MatchState
import dev.coys.calendar.data.SPURS_ID
import dev.coys.calendar.data.StandingRow
import dev.coys.calendar.data.TeamForm
import dev.coys.calendar.data.TeamLineup
import dev.coys.calendar.ui.theme.DrawColor
import dev.coys.calendar.ui.theme.LossColor
import dev.coys.calendar.ui.theme.PitchGreen
import dev.coys.calendar.ui.theme.SpursNavy
import dev.coys.calendar.ui.theme.WinColor
import dev.coys.calendar.ui.theme.color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(vm: AppViewModel, matchId: String, onBack: () -> Unit) {
    val match = vm.match(matchId)
    LaunchedEffect(matchId, match != null) { match?.let { vm.loadDetail(it) } }
    val detail = vm.details[matchId]
    val loading = vm.detailLoading[matchId] == true
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(match?.competition ?: "경기") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else if (match != null) IconButton(onClick = { vm.loadDetail(match) }) {
                        Icon(Icons.Default.Refresh, "새로고침")
                    }
                },
            )
        },
    ) { padding ->
        if (match == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("경기 정보를 찾을 수 없어요")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { MatchHeader(match) }
            if (match.state == MatchState.SCHEDULED) {
                item {
                    WakeToggle(
                        on = matchId in vm.wakeIds,
                        lead = vm.settings.wakeLeadMinutes,
                        onChange = { vm.setWakeAlarm(matchId, it) },
                    )
                }
            }
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, onClick = { tab = 0 }, text = { Text("라인업") })
                    Tab(tab == 1, onClick = { tab = 1 }, text = { Text("상대팀 정보") })
                }
            }
            vm.detailError[matchId]?.let { err ->
                if (detail == null) item { Text("$err. 인터넷 연결을 확인해 주세요.", color = LossColor) }
            }
            if (detail == null) {
                if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            } else if (tab == 0) {
                item { LineupSection(match, detail, onRetry = { vm.loadDetail(match) }) }
            } else {
                item { OpponentSection(match, detail) }
            }
        }
    }
}

@Composable
private fun MatchHeader(m: Match) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBlock(m.home, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
                    when (m.state) {
                        MatchState.FINISHED, MatchState.LIVE -> {
                            Text("${m.homeScore ?: "-"} : ${m.awayScore ?: "-"}", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (m.state == MatchState.LIVE) "진행 중" else "종료",
                                fontSize = 12.sp, color = if (m.state == MatchState.LIVE) LossColor else m.outcome.color(),
                            )
                        }
                        MatchState.POSTPONED -> Text("연기", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        MatchState.SCHEDULED -> Text(Format.time(m.kickoff), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TeamBlock(m.away, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(Format.date(m.kickoff), fontSize = 14.sp)
            m.venue?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TeamBlock(team: dev.coys.calendar.data.Team, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Crest(team, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(team.shortName, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, fontSize = 14.sp)
    }
}

@Composable
private fun WakeToggle(on: Boolean, lead: Int, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("⏰ 기상 알람", fontWeight = FontWeight.SemiBold)
                Text(
                    if (lead == 0) "킥오프 시각에 울려요" else "킥오프 ${lead}분 전에 울려요",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = onChange)
        }
    }
}

// ---------------- Line-ups ----------------

@Composable
private fun LineupSection(match: Match, detail: MatchDetail, onRetry: () -> Unit) {
    if (!detail.lineupsAnnounced) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📋 아직 라인업이 발표되지 않았어요", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "보통 킥오프 약 1시간 전에 발표돼요.\n발표되면 알림으로 알려드릴게요.",
                    fontSize = 13.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text("다시 확인") }
            }
        }
        return
    }
    val spursFirst = detail.lineups.sortedBy { if (it.team.id == SPURS_ID) 0 else 1 }
    var selected by rememberSaveable(match.id) { mutableIntStateOf(0) }
    val lineup = spursFirst[selected.coerceIn(0, spursFirst.lastIndex)]

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spursFirst.forEachIndexed { i, l ->
                FilterChip(
                    selected = selected == i,
                    onClick = { selected = i },
                    label = {
                        val name = listOf(match.home, match.away).firstOrNull { it.id == l.team.id }?.shortName
                            ?: l.team.abbreviation
                        Text("$name ${l.formation ?: ""}", maxLines = 1)
                    },
                    leadingIcon = { Crest(l.team, 18.dp) },
                )
            }
        }
        Pitch(lineup)
        SubsList(lineup)
    }
}

@Composable
private fun Pitch(lineup: TeamLineup) {
    val slots = FormationLayout.layout(lineup.formation, lineup.starters)
    if (slots == null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("선발", fontWeight = FontWeight.Bold)
                lineup.starters.forEach { PlayerLine(it) }
            }
        }
        return
    }
    val spurs = lineup.team.id == SPURS_ID
    val shirt = if (spurs) Color.White else Color(0xFFE53935)
    val shirtText = if (spurs) SpursNavy else Color.White

    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(12.dp)).background(PitchGreen),
    ) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val line = Color.White.copy(alpha = 0.55f)
            val s = Stroke(width = 2.dp.toPx())
            val pad = 8.dp.toPx()
            val fw = size.width - pad * 2
            val fh = size.height - pad * 2
            drawRect(line, Offset(pad, pad), Size(fw, fh), style = s)
            drawLine(line, Offset(pad, size.height / 2), Offset(size.width - pad, size.height / 2), s.width)
            drawCircle(line, radius = fw * 0.13f, center = center, style = s)
            val boxW = fw * 0.55f
            val boxH = fh * 0.14f
            drawRect(line, Offset((size.width - boxW) / 2, pad), Size(boxW, boxH), style = s)
            drawRect(line, Offset((size.width - boxW) / 2, size.height - pad - boxH), Size(boxW, boxH), style = s)
        }
        val markerW = 76.dp
        slots.forEach { slot ->
            Column(
                Modifier.width(markerW).offset(
                    x = w * slot.x - markerW / 2,
                    y = h * (1f - slot.y) - 22.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(shirt),
                    contentAlignment = Alignment.Center,
                ) { Text(slot.player.jersey, color = shirtText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                Text(
                    slot.player.shortName.substringAfter(". ", slot.player.shortName),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SubsList(lineup: TeamLineup) {
    if (lineup.subs.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("교체 명단", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            lineup.subs.forEach { PlayerLine(it) }
        }
    }
}

@Composable
private fun PlayerLine(p: LineupPlayer) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.jersey, Modifier.width(32.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(p.name, Modifier.weight(1f))
        when {
            p.subbedIn -> Text("▲ 투입", fontSize = 12.sp, color = WinColor)
            p.subbedOut -> Text("▼ 교체", fontSize = 12.sp, color = LossColor)
        }
    }
}

// ---------------- Opponent info ----------------

@Composable
private fun OpponentSection(match: Match, detail: MatchDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val opp = match.opponent
        detail.form.sortedBy { if (it.teamId == opp.id) 0 else 1 }.forEach { FormCard(it) }
        detail.headToHead?.takeIf { it.games.isNotEmpty() }?.let { HeadToHeadCard(it) }
        if (detail.standings.isNotEmpty()) StandingsCard(detail.standingsTitle, detail.standings, opp.id)
        if (detail.form.isEmpty() && detail.headToHead == null && detail.standings.isEmpty()) {
            Text("표시할 상대팀 정보가 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormCard(form: TeamForm) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${form.teamName} 최근 ${form.games.size}경기", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                form.games.reversed().forEach { OutcomeBadge(it.outcome, Modifier.padding(start = 3.dp)) }
            }
            Spacer(Modifier.height(6.dp))
            form.games.forEach { g ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.shortDate(g.date), Modifier.width(72.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (g.home) "vs" else "@", Modifier.width(24.dp), fontSize = 12.sp)
                    Text(g.opponentName, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(g.scoreline, fontWeight = FontWeight.Bold, color = g.outcome.color(), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun HeadToHeadCard(h2h: HeadToHead) {
    // Count from Spurs' side using the abbreviations in each game.
    var w = 0; var d = 0; var l = 0
    h2h.games.forEach { g ->
        val hs = g.homeScore.toIntOrNull() ?: return@forEach
        val aws = g.awayScore.toIntOrNull() ?: return@forEach
        val spursHome = g.homeAbbr == "TOT"
        val (f, a) = if (spursHome) hs to aws else aws to hs
        when { f > a -> w++; f == a -> d++; else -> l++ }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("최근 맞대결 ${h2h.games.size}경기", fontWeight = FontWeight.Bold)
            Text(
                "토트넘 ${w}승 ${d}무 ${l}패", fontSize = 13.sp,
                color = when { w > l -> WinColor; w < l -> LossColor; else -> DrawColor },
            )
            Spacer(Modifier.height(6.dp))
            h2h.games.forEach { g ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.fullDate(g.date), Modifier.width(88.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${g.homeAbbr}  ${g.homeScore} - ${g.awayScore}  ${g.awayAbbr}",
                        Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StandingsCard(title: String?, rows: List<StandingRow>, opponentId: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("순위표", fontWeight = FontWeight.Bold)
            title?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(6.dp))
            StandingLine("#", "팀", "경기", "승", "무", "패", "득실", "승점", header = true, highlight = null)
            HorizontalDivider()
            rows.forEach { r ->
                val hl = when (r.teamId) {
                    SPURS_ID -> MaterialTheme.colorScheme.primaryContainer
                    opponentId -> Color(0x33D64545)
                    else -> null
                }
                StandingLine(
                    "${r.rank}", r.teamName, "${r.played}", "${r.won}", "${r.drawn}", "${r.lost}",
                    r.goalDiff, "${r.points}", header = false, highlight = hl,
                )
            }
        }
    }
}

@Composable
private fun StandingLine(
    rank: String, team: String, p: String, w: String, d: String, l: String, gd: String, pts: String,
    header: Boolean, highlight: Color?,
) {
    val style = if (header) FontWeight.SemiBold else FontWeight.Normal
    val size = if (header) 11.sp else 12.sp
    Row(
        Modifier.fillMaxWidth()
            .then(if (highlight != null) Modifier.clip(RoundedCornerShape(4.dp)).background(highlight) else Modifier)
            .padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank, Modifier.width(24.dp), fontSize = size, fontWeight = style)
        Text(team, Modifier.weight(1f), fontSize = size, fontWeight = if (highlight != null) FontWeight.Bold else style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        listOf(p, w, d, l).forEach { Text(it, Modifier.width(26.dp), fontSize = size, textAlign = TextAlign.Center) }
        Text(gd, Modifier.width(34.dp), fontSize = size, textAlign = TextAlign.Center)
        Text(pts, Modifier.width(34.dp), fontSize = size, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
