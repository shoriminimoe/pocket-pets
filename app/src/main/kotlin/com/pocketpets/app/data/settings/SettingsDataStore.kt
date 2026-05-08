package com.pocketpets.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pocket_pets_settings")

data class NotificationSettings(
    val masterOn: Boolean,
    val hungryOn: Boolean,
    val dirtyOn: Boolean,
    val sadOn: Boolean,
    val quietStartHour: Int,
    val quietEndHour: Int,
)

data class FlagsSnapshot(
    val notificationSettings: NotificationSettings,
    val lastSeenPetId: Long?,
    val timeAccelerationEnabled: Boolean,
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val MASTER = booleanPreferencesKey("notif_master_on")
        val HUNGRY = booleanPreferencesKey("notif_hungry_on")
        val DIRTY = booleanPreferencesKey("notif_dirty_on")
        val SAD = booleanPreferencesKey("notif_sad_on")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val LAST_SEEN_PET = longPreferencesKey("last_seen_pet_id")
        val TIME_ACCEL = booleanPreferencesKey("time_acceleration_enabled")
        fun notifyFlag(petId: Long, kind: String) = booleanPreferencesKey("notif_flag_${petId}_$kind")
    }

    val snapshot: Flow<FlagsSnapshot> = context.dataStore.data.map { prefs ->
        FlagsSnapshot(
            notificationSettings = NotificationSettings(
                masterOn = prefs[Keys.MASTER] ?: true,
                hungryOn = prefs[Keys.HUNGRY] ?: true,
                dirtyOn = prefs[Keys.DIRTY] ?: true,
                sadOn = prefs[Keys.SAD] ?: true,
                quietStartHour = prefs[Keys.QUIET_START] ?: 22,
                quietEndHour = prefs[Keys.QUIET_END] ?: 7,
            ),
            lastSeenPetId = prefs[Keys.LAST_SEEN_PET],
            timeAccelerationEnabled = prefs[Keys.TIME_ACCEL] ?: false,
        )
    }

    suspend fun setNotificationSettings(s: NotificationSettings) {
        context.dataStore.edit {
            it[Keys.MASTER] = s.masterOn
            it[Keys.HUNGRY] = s.hungryOn
            it[Keys.DIRTY] = s.dirtyOn
            it[Keys.SAD] = s.sadOn
            it[Keys.QUIET_START] = s.quietStartHour
            it[Keys.QUIET_END] = s.quietEndHour
        }
    }

    suspend fun setLastSeenPet(id: Long) {
        context.dataStore.edit { it[Keys.LAST_SEEN_PET] = id }
    }

    suspend fun setTimeAcceleration(on: Boolean) {
        context.dataStore.edit { it[Keys.TIME_ACCEL] = on }
    }

    suspend fun isNotifyFlagSet(petId: Long, kind: String): Boolean =
        context.dataStore.data.map { it[Keys.notifyFlag(petId, kind)] ?: false }.first()

    suspend fun setNotifyFlag(petId: Long, kind: String, value: Boolean) {
        context.dataStore.edit { it[Keys.notifyFlag(petId, kind)] = value }
    }
}
