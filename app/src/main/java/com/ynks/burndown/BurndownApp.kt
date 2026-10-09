package com.ynks.burndown

import android.app.Application
import com.ynks.burndown.sync.SyncScheduler

class BurndownApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedule(this)
    }
}
