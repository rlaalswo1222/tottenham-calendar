package dev.coys.calendar.ui

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.coys.calendar.alarm.AlarmScheduler
import dev.coys.calendar.ui.theme.LossColor
import dev.coys.calendar.ui.theme.WinColor
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s = vm.settings
    val context = LocalContext.current
    // Re-read permission state whenever we come back from system settings.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }

    var kickoff by remember { mutableStateOf(s.kickoffReminder) }
    var reminderMin by remember { mutableIntStateOf(s.reminderMinutes) }
    var lineup by remember { mutableStateOf(s.lineupAlert) }
    var wakeLead by remember { mutableIntStateOf(s.wakeLeadMinutes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp, end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Section("알림") {
                    SwitchRow("킥오프 전 알림", "경기 시작 전에 알려드려요", kickoff) {
                        kickoff = it; s.kickoffReminder = it; vm.onAlarmSettingsChanged()
                    }
                    if (kickoff) {
                        Options(listOf(10, 30, 60), reminderMin, { "${it}분 전" }) {
                            reminderMin = it; s.reminderMinutes = it; vm.onAlarmSettingsChanged()
                        }
                    }
                    SwitchRow("라인업 발표 알림", "선발 명단이 나오면 알려드려요 (킥오프 알림과 겹치면 하나로 합쳐요)", lineup) {
                        lineup = it; s.lineupAlert = it; vm.onAlarmSettingsChanged()
                    }
                }
            }
            item {
                Section("기상 알람") {
                    Text(
                        "경기 화면에서 경기마다 켜고 끌 수 있어요. 끌 때까지 알람 소리로 울려요.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Options(listOf(0, 15, 30, 60), wakeLead, { if (it == 0) "킥오프 시각" else "${it}분 전" }) {
                        wakeLead = it; s.wakeLeadMinutes = it; vm.onAlarmSettingsChanged()
                    }
                }
            }
            item {
                // tick forces re-evaluation after returning from system settings.
                @Suppress("UNUSED_EXPRESSION") tick
                Section("권한 (알림이 제때 오려면 모두 ✅ 여야 해요)") {
                    PermissionRow(
                        "알림 표시", NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    ) { context.startActivity(appNotificationSettings(context)) }
                    PermissionRow("정확한 시간 알람", AlarmScheduler.canExact(context.getSystemService(AlarmManager::class.java))) {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                    PermissionRow("잠금화면 전체화면 알람", canFullScreen(context)) {
                        if (Build.VERSION.SDK_INT >= 34) {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                    PermissionRow("배터리 최적화 제외", ignoringBattery(context)) {
                        context.startActivity(requestIgnoreBattery(context))
                    }
                }
            }
            item {
                Section("테스트") {
                    Text(
                        "다음 경기 기준으로 알림을 보내요. 기상 알람은 10초 뒤에 울리니, 누르고 화면을 꺼서 잠금화면에서도 울리는지 확인해 보세요.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = vm::testMatchNotification) { Text("알림 테스트") }
                        FilledTonalButton(onClick = vm::testWakeAlarm) { Text("기상 알람 테스트") }
                    }
                }
            }
            item {
                Section("위젯") {
                    Text(
                        "홈 화면에 이번 달 경기 달력(4×4)을 추가해요.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { pinWidget(context) }) { Text("홈 화면에 위젯 추가") }
                }
            }
            item {
                Section("데이터") {
                    val last = vm.lastRefresh
                    Text(
                        "마지막 갱신: " + if (last == 0L) "없음" else Format.dateTime(Instant.ofEpochMilli(last)),
                        fontSize = 14.sp,
                    )
                    Text(
                        "출처: ESPN 비공식 API (개인용). 하루 2번 자동 갱신하고, 경기 당일엔 라인업이 나올 때까지 5분마다 확인해요.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = vm::refresh, enabled = !vm.refreshing) {
                        Text(if (vm.refreshing) "갱신 중…" else "지금 갱신")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Options(values: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v -> FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label(v)) }) }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (granted) "✅" else "⚠️", Modifier.padding(end = 8.dp))
        Text(title, Modifier.weight(1f), color = if (granted) WinColor else LossColor, fontWeight = FontWeight.SemiBold)
        if (!granted) FilledTonalButton(onClick = onFix) { Text("허용하기") }
    }
}

private fun pinWidget(context: Context) {
    val awm = android.appwidget.AppWidgetManager.getInstance(context)
    if (awm.isRequestPinAppWidgetSupported) {
        awm.requestPinAppWidget(
            android.content.ComponentName(context, dev.coys.calendar.widget.CalendarWidgetReceiver::class.java),
            null, null,
        )
    }
}

private fun canFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

private fun ignoringBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Personal sideloaded app; the user opts in explicitly.
private fun requestIgnoreBattery(context: Context) =
    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

private fun appNotificationSettings(context: Context) =
    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
