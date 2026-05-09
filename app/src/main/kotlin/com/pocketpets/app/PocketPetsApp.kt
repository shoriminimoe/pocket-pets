package com.pocketpets.app

import android.app.Application
import androidx.work.Configuration
import com.pocketpets.app.di.AppContainer
import com.pocketpets.app.work.NotificationHelper
import com.pocketpets.app.work.WorkScheduler

class PocketPetsApp :
    Application(),
    Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration =
        Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper(this, container.settings).ensureChannel()
        WorkScheduler.schedule(this)
    }
}
