package com.pocketpets.app.ui.pet

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.behavior.CatState
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
    playAreaBottom: Float,
): DpRect {
    val sizeDp = 48f
    val centerX = screenWidthDp / 2f + offsets[i] - sizeDp / 2f
    val bottomMargin = 16f + i * 6f
    val top = playAreaBottom - bottomMargin - sizeDp
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
    var screenWidthDp by remember { mutableFloatStateOf(0f) }
    var screenHeightDp by remember { mutableFloatStateOf(0f) }
    var topReservedDp by remember { mutableFloatStateOf(0f) }
    var bottomReservedDp by remember { mutableFloatStateOf(0f) }
    val dragController = remember { DragController() }
    val slotRects = remember { mutableStateMapOf<Item, DpRect>() }

    // Recompute habitat whenever the layout or growth stage changes. The
    // sprite size depends on the stage (192/224/256 dp), and HabitatBounds
    // must reserve that much room on the right and bottom — otherwise the
    // sprite's box renders past the play area when the cat sits at maxX/maxY.
    val spriteDp = stageSpriteSize(state.stage).value
    LaunchedEffect(screenWidthDp, screenHeightDp, topReservedDp, bottomReservedDp, spriteDp) {
        if (screenWidthDp <= 0f ||
            screenHeightDp <= 0f ||
            topReservedDp <= 0f ||
            bottomReservedDp <= 0f
        ) {
            return@LaunchedEffect
        }
        val habitat =
            computeHabitat(
                widthDp = screenWidthDp,
                heightDp = screenHeightDp,
                topReservedDp = topReservedDp,
                bottomReservedDp = bottomReservedDp,
                spriteDp = spriteDp,
            )
        vm.setHabitat(habitat.bounds, habitat.anchors)
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFE9C9B6))) {
        // Background room art. onSizeChanged reports the dp size of the room
        // so the LaunchedEffect above can rebuild bounds + anchors against the
        // current sprite size.
        Image(
            painter = painterResource(R.drawable.room_bg),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { sizePx ->
                        with(density) {
                            screenWidthDp = sizePx.width.toDp().value
                            screenHeightDp = sizePx.height.toDp().value
                        }
                    },
            contentScale = ContentScale.FillBounds,
        )

        // Top overlays: top bar + stat chips. Grouped into a single Column so
        // onSizeChanged reports the combined height we reserve at the top of the
        // play area.
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { sizePx ->
                        with(density) {
                            topReservedDp = sizePx.height.toDp().value
                        }
                    },
        ) {
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
            if (pet != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatChip("🍗", pet.stats.hunger, Color(0xFFE6843D))
                    StatChip("🛁", pet.stats.cleanliness, Color(0xFF7AB7E8))
                    StatChip("💗", pet.stats.happiness, Color(0xFFE86A8D))
                    StatChip("⚡", pet.stats.energy, Color(0xFFE8C13D))
                }
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
            val playAreaBottom = screenHeightDp - bottomReservedDp
            Image(
                painter =
                    painterResource(
                        if (state.world.bowlFilled) R.drawable.bowl_full else R.drawable.bowl,
                    ),
                contentDescription = null,
                modifier =
                    Modifier
                        .offset {
                            with(density) {
                                IntOffset(
                                    x = 24.dp.roundToPx(),
                                    y = (playAreaBottom - 32f - 16f).dp.roundToPx(),
                                )
                            }
                        }.size(width = 64.dp, height = 32.dp),
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
                val sizeDp = 48f
                val bottomMargin = 16f + i * 6f
                val xDp = screenWidthDp / 2f + xOffset - sizeDp / 2f
                val yDp = playAreaBottom - bottomMargin - sizeDp
                Image(
                    painter = painterResource(R.drawable.poop),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .offset {
                                with(density) {
                                    IntOffset(
                                        x = xDp.dp.roundToPx(),
                                        y = yDp.dp.roundToPx(),
                                    )
                                }
                            }.size(sizeDp.dp),
                )
            }

            // Inventory tray with drag-gesture handler
            var trayRootOffsetPx by remember { mutableStateOf(Offset.Zero) }
            
                    
                    Box(
                modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .onSizeChanged { sizePx ->
                                with(density) {
                                    bottomReservedDp = sizePx.height.toDp().value
                                }
                            }.onGloballyPositioned { coords ->
                                trayRootOffsetPx = coords.positionInRoot()
                            }.pointerInput(pet.id) {
                                awaitEachGesture   {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startDp =
                                        with(density) {
                                            Position(
                                                (down.position.x + trayRootOffsetPx.x).toDp().value,
                                                (down.position.y + trayRootOffsetPx.y).toDp().value,
                                            )
                                        }
                                    val pickedItem =
                                        slotRects.entries
                                            .firstOrNull { (_, r) -> r.contains(startDp) }
                                            ?.key ?: return@awaitEachGesture
                                    dragController.start(pickedItem, startDp)
                                    down.consume()

                                    var lifted = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        val pos =
                                            with(density) {
                                                Position(
                                                    (change.position.x + trayRootOffsetPx.x).toDp().value,
                                                    (change.position.y + trayRootOffsetPx.y).toDp().value,
                                                )
                                            }
                                        dragController.move(pos)
                                        change.consume()
                                        if (!change.pressed) {
                                            lifted = true
                                            break
                                        }
                                    }

                                    val ended = dragController.end()
                                    if (!lifted || ended == null) return@awaitEachGesture
                                    if (screenWidthDp <= 0f ||
                                        screenHeightDp <= 0f ||
                                        topReservedDp <= 0f ||
                                        bottomReservedDp <= 0f
                                    ) {
                                        return@awaitEachGesture
                                    }
                                    val playAreaBottomLocal = screenHeightDp - bottomReservedDp
                                    val playAreaRect =
                                        DpRect(
                                            left = 0f,
                                            top = topReservedDp,
                                            right = screenWidthDp,
                                            bottom = playAreaBottomLocal,
                                        )
                                    val poopRects =
                                        (0 until pet.poopCount).map { i ->
                                            poopRectFor(i, poopOffsets, screenWidthDp, playAreaBottomLocal)
                                        }
                                    val bowlRect =
                                        DpRect(
                                            left = 24f,
                                            top = playAreaBottomLocal - 32f - 16f,
                                            right = 24f + 64f,
                                            bottom = playAreaBottomLocal - 16f,
                                        )
                                    val catBehavior = state.behavior
                                    val catRect =
                                        if (catBehavior != null) {
                                            val spriteDp = stageSpriteSize(state.stage).value
                                            DpRect(
                                                left = catBehavior.position.x,
                                                top = catBehavior.position.y,
                                                right = catBehavior.position.x + spriteDp,
                                                bottom = catBehavior.position.y + spriteDp,
                                            )
                                        } else {
                                            DpRect(0f, 0f, -1f, -1f)
                                        }
                                    val target =
                                        dropTargetAt(
                                            position = ended.position,
                                            item = ended.item,
                                            playAreaRect = playAreaRect,
                                            bowlRect = bowlRect,
                                            poopRects = poopRects,
                                            catRect = catRect,
                                        ) ?: return@awaitEachGesture
                                    when (target) {
                                        DropTarget.Bowl -> vm.onFoodDroppedOnBowl()
                                        is DropTarget.Poop -> vm.onScoopDroppedOnPoop(target.index)
                                        is DropTarget.Floor -> vm.onToyDropped(target.position)
                                        DropTarget.Cat -> vm.onBrushDroppedOnCat()
                                    }
                                }
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
                    Item.Brush -> R.drawable.brush
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
