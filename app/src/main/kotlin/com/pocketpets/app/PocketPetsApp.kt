package com.pocketpets.app

import android.app.Application
import com.pocketpets.app.di.AppContainer

class PocketPetsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
