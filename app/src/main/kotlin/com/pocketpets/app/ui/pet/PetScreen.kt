package com.pocketpets.app.ui.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.ui.sprite.AnimatedSprite
import kotlin.random.Random

@Composable
fun PetScreen(
    vm: PetViewModel,
    onOpenSettings: () -> Unit,
    onOpenSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val pet = state.pet

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFE9C9B6))) {
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
                modifier =
                    Modifier
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
            val animation = CatAnimations.forMood(state.stage, state.mood)
            val spriteSize = stageSpriteSize(state.stage)
            val breathingScale = rememberBreathingScale()

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SpeechBubble(
                        phrase = state.activePhrase,
                        onDismiss = vm::dismissPhrase,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(spriteSize)
                                .clickable {
                                    vm.talk()
                                    vm.pet()
                                },
                    ) {
                        AnimatedSprite(
                            animation = animation,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .scale(scaleX = breathingScale, scaleY = 1f / breathingScale),
                        )
                        MoodOverlay(
                            mood = state.mood,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            // Food bowl decor sits at the bottom-left.
            Image(
                painter = painterResource(R.drawable.bowl),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 100.dp)
                        .size(width = 64.dp, height = 32.dp),
            )

            // Poops on the floor — deterministic per pet id
            val poopOffsets =
                remember(pet.id) {
                    val rng = Random(pet.id)
                    List(Pet.MAX_POOPS) { rng.nextInt(-100, 100) }
                }
            repeat(pet.poopCount) { i ->
                val xOffset = poopOffsets[i]
                Image(
                    painter = painterResource(R.drawable.poop),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = (110 + i * 6).dp,
                                start = if (xOffset > 0) xOffset.dp else 0.dp,
                                end = if (xOffset < 0) (-xOffset).dp else 0.dp,
                            ).size(48.dp),
                )
            }
        }

        // Action buttons
        Row(
            modifier =
                Modifier
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

private fun stageLabel(s: GrowthStage): String =
    when (s) {
        GrowthStage.BABY -> "Baby"
        GrowthStage.JUVENILE -> "Juvenile"
        GrowthStage.ADULT -> "Adult"
    }

private fun stageSpriteSize(stage: GrowthStage) =
    when (stage) {
        GrowthStage.BABY -> 192.dp
        GrowthStage.JUVENILE -> 224.dp
        GrowthStage.ADULT -> 256.dp
    }

/**
 * Subtle horizontal-pulse "breathing" applied to the sprite. Since the
 * current cat asset is a single static frame, this Compose-layer micro-motion
 * is what keeps the cat from looking frozen.
 */
@Composable
private fun rememberBreathingScale(): Float {
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "phase",
    )
    return 1f + 0.025f * phase
}
