package com.pocketpets.app.ui.pet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.Species
import kotlin.random.Random

@Composable
fun PetScreen(
    vm: PetViewModel,
    onOpenSettings: () -> Unit,
    onOpenSelector: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val pet = state.pet

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE9C9B6))) {
        // Background room art
        Image(
            painter = painterResource(R.drawable.room_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenSelector) {
                Icon(Icons.Default.Menu, contentDescription = "Switch pet")
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pet?.name ?: "—")
                if (pet != null) {
                    Text(stageLabel(state.stage), color = Color(0xFF555555))
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Stat chips just under the top bar
        if (pet != null) {
            Row(
                modifier = Modifier
                    .padding(top = 60.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip("🍗", pet.stats.hunger, Color(0xFFE6843D))
                StatChip("🛁", pet.stats.cleanliness, Color(0xFF7AB7E8))
                StatChip("💗", pet.stats.happiness, Color(0xFFE86A8D))
                StatChip("⚡", pet.stats.energy, Color(0xFFE8C13D))
            }
        }

        // Pet sprite (centered) + speech bubble above it
        if (pet != null) {
            val spriteRes = spriteFor(pet.species, state.stage, state.mood)
            val frames = frameCountFor(state.mood)

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpeechBubble(
                        phrase = state.activePhrase,
                        onDismiss = vm::dismissPhrase,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(256.dp)
                            .clickable { vm.talk(); vm.pet() },
                    ) {
                        SpriteView(
                            spriteResId = spriteRes,
                            frameCount = frames,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Poops on the floor — deterministic per pet id
            val poopOffsets = remember(pet.id) {
                val rng = Random(pet.id)
                List(Pet.MAX_POOPS) { rng.nextInt(-100, 100) }
            }
            repeat(pet.poopCount) { i ->
                val xOffset = poopOffsets[i]
                Image(
                    painter = painterResource(R.drawable.poop),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = (110 + i * 6).dp, start = if (xOffset > 0) xOffset.dp else 0.dp, end = if (xOffset < 0) (-xOffset).dp else 0.dp)
                        .size(48.dp),
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = vm::feed) { Text("Feed") }
            Button(onClick = vm::clean) { Text("Clean") }
            Button(onClick = vm::pet) { Text("Pet") }
            Button(onClick = vm::talk) { Text("Talk") }
        }
    }
}

private fun stageLabel(s: GrowthStage): String = when (s) {
    GrowthStage.BABY -> "Baby"
    GrowthStage.JUVENILE -> "Juvenile"
    GrowthStage.ADULT -> "Adult"
}

private fun spriteFor(species: Species, stage: GrowthStage, mood: Mood): Int {
    val stageStr = when (stage) {
        GrowthStage.BABY -> "baby"
        GrowthStage.JUVENILE -> "juvenile"
        GrowthStage.ADULT -> "adult"
    }
    val moodStr = when (mood) {
        Mood.IDLE -> "idle"
        Mood.HAPPY -> "happy"
        Mood.HUNGRY -> "hungry"
        Mood.GROSSED_OUT -> "dirty"
        Mood.SAD -> "sad"
        Mood.SLEEPY -> "sleep"
    }
    val name = "cat_${stageStr}_$moodStr"
    return runCatching {
        R.drawable::class.java.getField(name).getInt(null)
    }.getOrElse { R.drawable.cat_baby_idle }
}

private fun frameCountFor(mood: Mood): Int = when (mood) {
    Mood.IDLE -> 4
    Mood.HAPPY -> 3
    Mood.SLEEPY -> 4
    Mood.HUNGRY, Mood.GROSSED_OUT, Mood.SAD -> 1
}
