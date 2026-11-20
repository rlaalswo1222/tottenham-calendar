package dev.coys.calendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.coys.calendar.Settings
import dev.coys.calendar.data.Match
import dev.coys.calendar.data.MatchDetail
import dev.coys.calendar.data.MatchRepository
import dev.coys.calendar.sync.Sync
import kotlinx.coroutines.launch

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    val repo = MatchRepository.get(app)
    val settings = Settings(app)

    var matches by mutableStateOf(repo.cachedMatches()); private set
    var refreshing by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var wakeIds by mutableStateOf(settings.wakeAlarmMatchIds); private set
    var lastRefresh by mutableStateOf(settings.lastRefreshMillis); private set

    val details = mutableStateMapOf<String, MatchDetail>()
    val detailLoading = mutableStateMapOf<String, Boolean>()
    val detailError = mutableStateMapOf<String, String>()

    fun match(id: String): Match? = matches.firstOrNull { it.id == id }

    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            runCatching { repo.refreshSchedule() }
                .onSuccess {
                    matches = it
                    settings.lastRefreshMillis = System.currentTimeMillis()
                    lastRefresh = settings.lastRefreshMillis
                    error = null
                }
                .onFailure { error = "새로고침 실패: 인터넷 연결을 확인해 주세요" }
            refreshing = false
            Sync.afterDataChange(app)
        }
    }

    fun loadDetail(match: Match) {
        if (match.id !in details) repo.cachedDetail(match.id)?.let { details[match.id] = it }
        if (detailLoading[match.id] == true) return
        viewModelScope.launch {
            detailLoading[match.id] = true
            runCatching { repo.refreshDetail(match) }
                .onSuccess { details[match.id] = it; detailError.remove(match.id) }
                .onFailure { detailError[match.id] = "불러오기 실패" }
            detailLoading[match.id] = false
        }
    }

    fun setWakeAlarm(matchId: String, on: Boolean) {
        settings.setWakeAlarm(matchId, on)
        wakeIds = settings.wakeAlarmMatchIds
        onAlarmSettingsChanged()
    }

    private fun nextMatch(): Match? =
        matches.firstOrNull { it.state == dev.coys.calendar.data.MatchState.SCHEDULED && it.kickoff.isAfter(java.time.Instant.now()) }

    /** Posts the real kickoff notification for the next match, right now. */
    fun testMatchNotification() {
        val m = nextMatch() ?: return
        viewModelScope.launch {
            val d = runCatching { repo.refreshDetail(m) }.getOrNull() ?: repo.cachedDetail(m.id)
            dev.coys.calendar.alarm.Notifier.showMatchNotification(app, m, d, reminder = true)
        }
    }

    /** Schedules a real wake alarm for the next match 10 s from now, through the same AlarmManager path. */
    fun testWakeAlarm() {
        val m = nextMatch() ?: return
        dev.coys.calendar.alarm.AlarmScheduler.schedule(
            app,
            dev.coys.calendar.alarm.PlannedAlarm(
                dev.coys.calendar.alarm.AlarmKind.WAKE, m.id, java.time.Instant.now().plusSeconds(10),
            ),
        )
    }

    fun onAlarmSettingsChanged() {
        viewModelScope.launch { Sync.afterDataChange(app) }
    }
}
