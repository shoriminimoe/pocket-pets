package com.pocketpets.app.ui.select

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketpets.app.domain.Pet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetSelectorSheet(
    vm: PetSelectorViewModel,
    onSelected: () -> Unit,
    onAdopt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val pets by vm.pets.collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your pets", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.padding(4.dp))
            pets.forEach { pet ->
                PetRow(pet, isActive = pet.isActive) {
                    vm.select(pet.id); onSelected()
                }
                HorizontalDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAdopt() }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("+ Adopt new pet")
            }
        }
    }
}

@Composable
private fun PetRow(pet: Pet, isActive: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isActive) "★" else "  ")
        Spacer(Modifier.width(8.dp))
        Text(pet.name)
        Spacer(Modifier.width(8.dp))
        Text("(${pet.species.name.lowercase()})", color = Color.Gray)
    }
}
