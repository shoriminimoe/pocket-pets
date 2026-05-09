package com.pocketpets.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocketpets.app.PocketPetsApp
import com.pocketpets.app.data.repo.PetRepo
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

class PetCareWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repo: PetRepo,
    private val settings: SettingsDataStore,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext, params,
        repo = (appContext.applicationContext as PocketPetsApp).container.petRepository,
        settings = (appContext.applicationContext as PocketPetsApp).container.settings,
        notifications = NotificationHelper(
            appContext,
            (appContext.applicationContext as PocketPetsApp).container.settings,
        ),
    )

    override suspend fun doWork(): Result {
        val pets = repo.observeAll().first()
        val ns = settings.snapshot.first().notificationSettings
        for (pet in pets) {
            repo.runDecayTick(pet.id)
            val refreshed = repo.getById(pet.id) ?: continue
            notifications.maybeNotify(refreshed, ns)
        }
        return Result.success()
    }
}
