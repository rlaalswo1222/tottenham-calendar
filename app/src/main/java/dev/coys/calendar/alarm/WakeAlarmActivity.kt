package dev.coys.calendar.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.coys.calendar.MainActivity
import dev.coys.calendar.data.MatchRepository
import dev.coys.calendar.ui.Format
import dev.coys.calendar.ui.theme.SpursNavy

/** Full-screen alarm shown over the lock screen for early-morning kickoffs. */
class WakeAlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val matchId = intent.getStringExtra(AlarmReceiver.EXTRA_MATCH_ID).orEmpty()
        val match = MatchRepository.get(this).cachedMatch(matchId)

        // This screen owns the sound from here on; drop the notification so it doesn't ring twice.
        Notifier.dismissWake(this, matchId)
        startRinging()
        handler.postDelayed({ dismiss(matchId) }, 10 * 60 * 1000L)

        setContent {
            Column(
                Modifier.fillMaxSize().background(SpursNavy).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⏰", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    match?.let { "${if (it.spursIsHome) "vs" else "@"} ${it.opponent.name}" } ?: "경기 알람",
                    color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                match?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${it.competition}\n${Format.dateTime(it.kickoff)} 킥오프",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(48.dp))
                Button(
                    onClick = { dismiss(matchId) },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SpursNavy),
                ) { Text("끄기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        dismiss(matchId)
                        startActivity(MainActivity.openMatchIntent(this@WakeAlarmActivity, matchId))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("끄고 라인업 보기", color = Color.White) }
            }
        }
    }

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        vibrator = (if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        })?.apply {
            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun dismiss(matchId: String) {
        stopRinging()
        Notifier.dismissWake(this, matchId)
        finish()
    }

    companion object {
        fun intent(context: Context, matchId: String) =
            Intent(context, WakeAlarmActivity::class.java)
                .putExtra(AlarmReceiver.EXTRA_MATCH_ID, matchId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
