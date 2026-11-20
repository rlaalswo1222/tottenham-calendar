package dev.coys.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.coys.calendar.Settings
import dev.coys.calendar.alarm.AlarmScheduler
import dev.coys.calendar.data.MatchRepository
import dev.coys.calendar.widget.CalendarWidget
import java.util.concurrent.TimeUnit

/** Refreshes the schedule, then re-derives everything that depends on it. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = runCatching { MatchRepository.get(applicationContext).refreshSchedule() }.isSuccess
        if (ok) Settings(applicationContext).lastRefreshMillis = System.currentTimeMillis()
        Sync.afterDataChange(applicationContext)
        return if (ok || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}

object Sync {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Twice a day is plenty: fixtures move rarely and match-day polling is alarm-driven. */
    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "refresh-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(12, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    fun refreshNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "refresh-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build(),
        )
    }

    /** Call whenever cached matches or alarm settings change. */
    suspend fun afterDataChange(context: Context) {
        AlarmScheduler.rescheduleAll(context)
        CalendarWidget().updateAll(context)
    }
}

/** Alarms don't survive reboots or app updates; rebuild them from cache. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.rescheduleAll(context)
        Sync.schedulePeriodic(context)
        Sync.refreshNow(context)
    }
}
