package dev.coys.calendar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.coys.calendar.data.MatchRepository
import dev.coys.calendar.data.Outcome
import dev.coys.calendar.data.Team
import dev.coys.calendar.ui.theme.color
import dev.coys.calendar.ui.theme.label

@Composable
fun Crest(team: Team, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, team.id) {
        val repo = MatchRepository.get(context)
        value = (repo.cachedCrest(team) ?: repo.crest(team))?.asImageBitmap()
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = team.name, modifier = modifier.size(size))
    } else {
        Box(
            modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(team.abbreviation.take(3), fontSize = (size.value / 3.2f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OutcomeBadge(outcome: Outcome?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(outcome.color()),
        contentAlignment = Alignment.Center,
    ) {
        Text(outcome.label(), color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
