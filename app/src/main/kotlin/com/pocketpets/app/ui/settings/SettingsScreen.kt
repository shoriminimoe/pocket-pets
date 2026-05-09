package com.pocketpets.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketpets.app.BuildConfig

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val s by vm.settings.collectAsState()
    val accel by vm.timeAccel.collectAsState()
    val current = s ?: return
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Notifications", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        ToggleRow("All notifications", current.masterOn) { vm.update(current.copy(masterOn = it)) }
        ToggleRow("Hungry alerts", current.hungryOn) { vm.update(current.copy(hungryOn = it)) }
        ToggleRow("Dirty alerts", current.dirtyOn) { vm.update(current.copy(dirtyOn = it)) }
        ToggleRow("Sad alerts", current.sadOn) { vm.update(current.copy(sadOn = it)) }
        Spacer(Modifier.height(8.dp))
        Text("Quiet hours: ${current.quietStartHour}:00–${current.quietEndHour}:00")
        if (BuildConfig.DEBUG_TIME) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Debug")
            ToggleRow("Speed up time 100×", accel) { vm.setTimeAcceleration(it) }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
