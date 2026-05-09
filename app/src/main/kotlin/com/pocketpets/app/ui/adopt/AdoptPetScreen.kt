package com.pocketpets.app.ui.adopt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdoptPetScreen(
    vm: AdoptViewModel,
    onAdopt: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by vm.state.collectAsState()
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Adopt a Pet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text("Cat (more species coming soon)")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = s.name,
            onValueChange = vm::setName,
            label = { Text("Name (1–20 characters)") },
            singleLine = true,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { vm.adopt(onAdopt) }, enabled = s.canSubmit) {
            Text("Adopt")
        }
    }
}
