package dev.coys.calendar

import android.app.Application
import dev.coys.calendar.alarm.Notifier

class CoysApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
