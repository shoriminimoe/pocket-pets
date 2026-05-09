package com.pocketpets.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketpets.app.MainActivity
import com.pocketpets.app.data.settings.NotificationSettings
import com.pocketpets.app.data.settings.SettingsDataStore
import com.pocketpets.app.domain.Pet
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class NotificationHelper(
    private val context: Context,
    private val settings: SettingsDataStore,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) {
    companion object {
        const val CHANNEL_ID = "pet_care"
        const val EVT_HUNGRY = "hungry"
        const val EVT_DIRTY = "dirty"
        const val EVT_SAD = "sad"
        private const val LOW_THRESHOLD = 25f
        private const val HYSTERESIS = 10f
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pet care", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Reminders when a pet needs care." }
                )
            }
        }
    }

    /** Returns the kinds of events that fired (for tests). */
    suspend fun maybeNotify(pet: Pet, ns: NotificationSettings): List<String> {
        if (!ns.masterOn) return emptyList()
        if (inQuietHours(ns)) return emptyList()
        ensureChannel()
        val fired = mutableListOf<String>()

        suspend fun handle(kind: String, isLow: Boolean, isHigh: Boolean, on: Boolean, message: String) {
            val flag = settings.isNotifyFlagSet(pet.id, kind)
            if (isLow && !flag && on) {
                post(pet.id, kind, message)
                settings.setNotifyFlag(pet.id, kind, true)
                fired += kind
            } else if (isHigh && flag) {
                settings.setNotifyFlag(pet.id, kind, false)
            }
        }

        handle(
            EVT_HUNGRY,
            isLow = pet.stats.hunger < LOW_THRESHOLD,
            isHigh = pet.stats.hunger >= LOW_THRESHOLD + HYSTERESIS,
            on = ns.hungryOn,
            message = "${pet.name} is hungry!"
        )
        handle(
            EVT_DIRTY,
            isLow = pet.stats.cleanliness < LOW_THRESHOLD || pet.poopCount >= 2,
            isHigh = pet.stats.cleanliness >= LOW_THRESHOLD + HYSTERESIS && pet.poopCount < 2,
            on = ns.dirtyOn,
            message = "${pet.name} needs cleaning!"
        )
        handle(
            EVT_SAD,
            isLow = pet.stats.happiness < LOW_THRESHOLD,
            isHigh = pet.stats.happiness >= LOW_THRESHOLD + HYSTERESIS,
            on = ns.sadOn,
            message = "${pet.name} misses you"
        )

        return fired
    }

    private fun inQuietHours(ns: NotificationSettings): Boolean {
        val hour = clock.now().toLocalDateTime(zone).hour
        return if (ns.quietStartHour <= ns.quietEndHour) {
            hour in ns.quietStartHour until ns.quietEndHour
        } else {
            hour >= ns.quietStartHour || hour < ns.quietEndHour
        }
    }

    private fun post(petId: Long, kind: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("petId", petId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, "$petId$kind".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("Pocket Pets")
            .setContentText(message)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify("$petId$kind".hashCode(), notif) }
    }
}
