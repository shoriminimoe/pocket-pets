package com.pocketpets.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketpets.app.data.settings.NotificationSettings
import com.pocketpets.app.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsDataStore,
) : ViewModel() {
    private val _settings = MutableStateFlow<NotificationSettings?>(null)
    val settings: StateFlow<NotificationSettings?> = _settings
    private val _timeAccel = MutableStateFlow(false)
    val timeAccel: StateFlow<Boolean> = _timeAccel

    init {
        viewModelScope.launch {
            val snap = store.snapshot.first()
            _settings.value = snap.notificationSettings
            _timeAccel.value = snap.timeAccelerationEnabled
        }
    }

    fun update(s: NotificationSettings) {
        _settings.value = s
        viewModelScope.launch { store.setNotificationSettings(s) }
    }

    fun setTimeAcceleration(on: Boolean) {
        _timeAccel.value = on
        viewModelScope.launch { store.setTimeAcceleration(on) }
    }
}
