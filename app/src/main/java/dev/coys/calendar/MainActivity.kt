package dev.coys.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.coys.calendar.sync.Sync
import dev.coys.calendar.ui.AppViewModel
import dev.coys.calendar.ui.HomeScreen
import dev.coys.calendar.ui.MatchScreen
import dev.coys.calendar.ui.SettingsScreen
import dev.coys.calendar.ui.theme.SpursTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingMatchId by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMatchId = intent.getStringExtra(EXTRA_MATCH_ID)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Sync.schedulePeriodic(this)
        val stale = System.currentTimeMillis() - vm.settings.lastRefreshMillis > 60 * 60 * 1000
        if (vm.matches.isEmpty() || stale) vm.refresh()

        setContent {
            SpursTheme {
                AppRoot(vm, pendingMatchId) { pendingMatchId = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_MATCH_ID)?.let { pendingMatchId = it }
    }

    companion object {
        const val EXTRA_MATCH_ID = "match_id"

        fun openMatchIntent(context: Context, matchId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("coys://match/$matchId"))
                .putExtra(EXTRA_MATCH_ID, matchId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/** Tiny back stack: "home", "settings" or "match:<id>". */
@Composable
private fun AppRoot(vm: AppViewModel, openMatchId: String?, onOpened: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf("home")) }
    fun push(s: String) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }

    LaunchedEffect(openMatchId) {
        if (openMatchId != null) {
            stack = listOf("home", "match:$openMatchId")
            onOpened()
        }
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    val top = stack.last()
    when {
        top == "settings" -> SettingsScreen(vm, onBack = ::pop)
        top.startsWith("match:") -> MatchScreen(vm, top.removePrefix("match:"), onBack = ::pop)
        else -> HomeScreen(
            vm,
            onOpenMatch = { push("match:$it") },
            onOpenSettings = { push("settings") },
        )
    }
}
