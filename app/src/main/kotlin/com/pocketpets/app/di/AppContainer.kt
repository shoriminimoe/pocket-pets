package com.pocketpets.app.di

import android.content.Context
import androidx.room.Room
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.data.repo.PetRepository
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.datetime.Clock

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val clock: Clock = Clock.System

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "pocket_pets.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val petRepository: PetRepository by lazy {
        PetRepository(database.petDao(), database.careEventDao(), clock)
    }

    val settings: SettingsDataStore by lazy {
        SettingsDataStore(appContext)
    }
}
