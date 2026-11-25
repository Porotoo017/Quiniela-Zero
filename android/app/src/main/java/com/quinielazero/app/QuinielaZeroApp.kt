package com.quinielazero.app

import android.app.Application
import com.quinielazero.app.notifications.NotificationChannels

class QuinielaZeroApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
