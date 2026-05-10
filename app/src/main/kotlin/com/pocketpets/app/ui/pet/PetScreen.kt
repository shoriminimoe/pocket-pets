package com.pocketpets.app.ui.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.ui.inventory.DpRect
import com.pocketpets.app.ui.inventory.DragController
import com.pocketpets.app.ui.inventory.DropTarget
import com.pocketpets.app.ui.inventory.InventoryTray
import com.pocketpets.app.ui.inventory.Item
import com.pocketpets.app.ui.inventory.dropTargetAt
import com.pocketpets.app.ui.sprite.AnimatedSprite
import kotlin.random.Random

private fun poopRectFor(
    i: Int,
    offsets: List<Int>,
    screenWidthDp: Float,
    screenHeightDp: Float,
): DpRect {
    val sizeDp = 48f
    val centerX = screenWidthDp / 2f + offsets[i] - sizeDp / 2f
    val bottomMargin = (110 + i * 6).toFloat()
    val top = screenHeightDp - bottomMargin - sizeDp
    return DpRect(
        left = centerX,
        top = top,
        right = centerX + sizeDp,
        bottom = top + sizeDp,
    )
}

@Composable
fun PetScreen(
    vm: PetViewModel,
    onOpenSettings: () -> Unit,
    onOpenSelector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val pet = state.pet

    val density = LocalDensity.current
    var habitatBoundsState by remember { mutableStateOf<HabitatBounds?>(null) }
    var habitatAnchorsState by remember { mutableStateOf<Anchors?>(null) }
    var screenWidthDp by remember { mutableFloatStateOf(0f) }
    var screenHeightDp by remember { mutableFloatStateOf(0f) }
    val dragController = remember { DragController() }
    val slotRects = remember { mutableStateMapOf<Item, DpRect>() }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFE9C9B6))) {
        // Background room art. onSizeChanged reports floor bounds + anchors
        // so the ViewModel's behavior tick can keep the cat on the floor.
        Image(
            painter = painterResource(R.drawable.room_bg),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { sizePx ->
                        with(density) {
                            val widthDp = sizePx.width.toDp().value
                            val heightDp = sizePx.height.toDp().value
                            // Floor is the lower portion of the room. Numbers
                            // keep the cat above the inventory tray.
                            val floorTopDp = heightDp * 0.40f
                            val floorBottomDp = heightDp * 0.85f
                            val spriteDp = 64f
                            val bounds =
                                HabitatBounds(
                                    minX = 0f,
                                    minY = floorTopDp,
                                    maxX = (widthDp - spriteDp).coerceAtLeast(1f),
                                    maxY = (floorBottomDp - spriteDp).coerceAtLeast(floorTopDp + 1f),
                                )
                            val anchors =
                                Anchors(
                                    bed =
                                        Position(
                                            x = (widthDp - spriteDp - 24f).coerceAtLeast(0f),
                                            y = (floorBottomDp - spriteDp - 16f).coerceAtLeast(floorTopDp),
                                        ),
                                    bowl =
                                        Position(
                                            x = 24f,
                                            y = (floorBottomDp - spriteDp - 16f).coerceAtLeast(floorTopDp),
                                        ),
                                )
                            screenWidthDp = widthDp
                            screenHeightDp = heightDp
                            habitatBoundsState = bounds
                            habitatAnchorsState = anchors
                            vm.setHabitat(bounds, anchors)
                        }
                    },
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

        // Pet sprite (offsetted by behavior.position) + speech bubble above it
        val behavior = state.behavior
        if (pet != null && behavior != null) {
            val animation = CatAnimations.forState(behavior.state)
            val spriteSize = stageSpriteSize(state.stage)
            val breathingScale = rememberBreathingScale()
            val applyBreathing = behavior.state != CatState.Walking

            Box(
                modifier =
                    Modifier
                        .offset(x = behavior.position.x.dp, y = behavior.position.y.dp)
                        .size(spriteSize)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { vm.onCatHeld() })
                        },
            ) {
                AnimatedSprite(
                    animation = animation,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .let {
                                if (applyBreathing) {
                                    it.scale(scaleX = 1f, scaleY = breathingScale)
                                } else {
                                    it
                                }
                            },
                    facing = CatAnimations.facingFor(behavior.state, behavior.facing),
                )
                MoodOverlay(
                    mood = state.mood,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Speech bubble appears above the cat at its current position.
            Box(
                modifier =
                    Modifier
                        .offset(x = behavior.position.x.dp, y = (behavior.position.y - 64f).dp),
            ) {
                SpeechBubble(
                    phrase = state.activePhrase,
                    onDismiss = vm::dismissPhrase,
                )
            }
        }

        if (pet != null) {
            // Food bowl decor sits at the bottom-left. Switches to bowl_full when filled.
            Image(
                painter =
                    painterResource(
                        if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl,
                    ),
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 100.dp)
                        .size(width = 64.dp, height = 32.dp),
            )

            // Toy on the floor when present
            state.world.toy?.let { toyPos ->
                Image(
                    painter = painterResource(R.drawable.toy),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .offset(x = toyPos.x.dp, y = toyPos.y.dp)
                            .size(48.dp),
                )
            }

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
                            .padding(bottom = (110 + i * 6).dp)
                            .offset(x = xOffset.dp)
                            .size(48.dp),
                )
            }

            // Inventory tray with drag-gesture handler
            var trayRootOffsetPx by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            trayRootOffsetPx = coords.positionInRoot()
                        }.pointerInput(pet.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { localOffset ->
                                    val rootPx =
                                        Offset(
                                            localOffset.x + trayRootOffsetPx.x,
                                            localOffset.y + trayRootOffsetPx.y,
                                        )
                                    val startDp =
                                        with(density) {
                                            Position(
                                                rootPx.x.toDp().value,
                                                rootPx.y.toDp().value,
                                            )
                                        }
                                    val pickedItem =
                                        slotRects.entries
                                            .firstOrNull { (_, r) -> r.contains(startDp) }
                                            ?.key
                                    if (pickedItem != null) dragController.start(pickedItem, startDp)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val rootPx =
                                        Offset(
                                            change.position.x + trayRootOffsetPx.x,
                                            change.position.y + trayRootOffsetPx.y,
                                        )
                                    val pos =
                                        with(density) {
                                            Position(
                                                rootPx.x.toDp().value,
                                                rootPx.y.toDp().value,
                                            )
                                        }
                                    dragController.move(pos)
                                },
                                onDragEnd = {
                                    val ended =
                                        dragController.end() ?: return@detectDragGesturesAfterLongPress
                                    val bounds =
                                        habitatBoundsState ?: return@detectDragGesturesAfterLongPress
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, screenHeightDp)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = screenHeightDp - 132f,
                                            right = 24f + 64f,
                                            bottom = screenHeightDp - 132f + 32f,
                                        )
                                    val target =
                                        dropTargetAt(
                                            position = ended.position,
                                            item = ended.item,
                                            bounds = bounds,
                                            bowlRect = bowlRect,
                                            poopRects = poopRects,
                                        ) ?: return@detectDragGesturesAfterLongPress
                                    when (target) {
                                        DropTarget.Bowl -> vm.onFoodDroppedOnBowl()
                                        is DropTarget.Poop -> vm.onScoopDroppedOnPoop(target.index)
                                        is DropTarget.Floor -> vm.onToyDropped(target.position)
                                    }
                                },
                                onDragCancel = { dragController.end() },
                            )
                        },
            ) {
                InventoryTray(
                    onSlotPositionChange = { item, leftDp, topDp, sizeDp ->
                        slotRects[item] = DpRect(leftDp, topDp, leftDp + sizeDp, topDp + sizeDp)
                    },
                )
            }
        }

        // Drag overlay — renders the in-flight item icon at the pointer position.
        dragController.inFlight?.let { drag ->
            val drawableId =
                when (drag.item) {
                    Item.Food -> R.drawable.food
                    Item.Scoop -> R.drawable.scoop
                    Item.Toy -> R.drawable.toy
                }
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                modifier =
                    Modifier
                        .offset(x = (drag.position.x - 32f).dp, y = (drag.position.y - 32f).dp)
                        .size(64.dp),
            )
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
