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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R
import com.pocketpets.app.domain.GrowthStage
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.behavior.CatBehavior
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.ui.inventory.DRAG_PREVIEW_SIZE_DP
import com.pocketpets.app.ui.inventory.DpRect
import com.pocketpets.app.ui.inventory.DragController
import com.pocketpets.app.ui.inventory.DropTarget
import com.pocketpets.app.ui.inventory.InventoryTray
import com.pocketpets.app.ui.inventory.Item
import com.pocketpets.app.ui.inventory.dropTargetAt
import com.pocketpets.app.ui.inventory.previewCenterFor
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
    var defaultBowlPositionState by remember { mutableStateOf<Position?>(null) }
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
        defaultBowlPositionState = habitat.defaultBowlPosition
        vm.setHabitat(
            bounds = habitat.bounds,
            anchors = habitat.anchors,
            bowlBounds = habitat.bowlClampBounds,
            defaultBowlPosition = habitat.defaultBowlPosition,
        )
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

        // Pet sprite (offsetted by behavior.position). The cat is drawn as
        // part of the Y-sorted floor decor below so it depth-sorts against
        // the bowl, toy and poops (issue #30). The speech bubble is emitted
        // *after* the floor decor (below) so it always floats above all
        // floor sprites regardless of their bottom-Y.
        val behavior = state.behavior

        if (pet != null) {
            // Poop layout offsets are deterministic per pet id. Hoisted out of the
            // bottomReservedDp gate below so the tray's onDragEnd can still resolve
            // poop drop rects via poopRectFor before any decor renders.
            val poopOffsets =
                remember(pet.id) {
                    val rng = Random(pet.id)
                    List(Pet.MAX_POOPS) { rng.nextInt(-100, 100) }
                }

            // Decor (cat, bowl, toy, poops) anchors to playAreaBottom. Until the tray's
            // onSizeChanged fires, bottomReservedDp is 0 and playAreaBottom would equal
            // screenHeightDp — drawing the decor behind the not-yet-measured tray for
            // one frame. Gate the decor block on bottomReservedDp so the flash is gone.
            if (bottomReservedDp > 0f) {
                val playAreaBottom = screenHeightDp - bottomReservedDp
                // Bowl position is always rendered (and added to the depth-sort
                // list below); resolved here once so it can be passed to the
                // BowlSprite renderer with a stable fallback chain.
                val bowlPos =
                    state.world.bowlPosition
                        ?: defaultBowlPositionState
                        ?: Position(24f, playAreaBottom - BOWL_HEIGHT_DP - 16f)

                // Assemble all floor-level sprites and sort by bottom-Y so
                // whichever sprite's feet sit lower on the screen draws on top.
                // Fixes #30: bowl no longer always covers the cat. Memoised so
                // recompositions that don't change any sprite's Y don't
                // reallocate + re-sort the list.
                val toyY = state.world.toy?.y
                val behaviorY = behavior?.position?.y
                val sortedFloorSprites =
                    remember(
                        behaviorY,
                        bowlPos.y,
                        toyY,
                        pet.poopCount,
                        state.stage,
                        playAreaBottom,
                    ) {
                        val sprites =
                            buildList<FloorSprite> {
                                if (behavior != null) {
                                    add(
                                        FloorSprite.Cat(
                                            topLeftY = behavior.position.y,
                                            spriteSizeDp = stageSpriteSize(state.stage).value,
                                        ),
                                    )
                                }
                                add(FloorSprite.Bowl(topLeftY = bowlPos.y))
                                state.world.toy?.let { toyPos ->
                                    add(FloorSprite.Toy(topLeftY = toyPos.y))
                                }
                                repeat(pet.poopCount) { i ->
                                    // The x position is derived from poopOffsets[i]
                                    // when the sprite is rendered below — only the
                                    // Y matters for sorting.
                                    val bottomMargin = 16f + i * 6f
                                    val yDp = playAreaBottom - bottomMargin - POOP_SIZE_DP
                                    add(FloorSprite.Poop(index = i, topLeftY = yDp))
                                }
                            }
                        floorSpriteOrder(sprites)
                    }
                sortedFloorSprites.forEach { sprite ->
                    when (sprite) {
                        is FloorSprite.Cat ->
                            if (behavior != null) {
                                CatSprite(
                                    behavior = behavior,
                                    stage = state.stage,
                                    mood = state.mood,
                                    onLongPress = vm::onCatHeld,
                                )
                            }
                        is FloorSprite.Bowl ->
                            BowlSprite(
                                petId = pet.id,
                                bowlPos = bowlPos,
                                bowlFilled = state.world.bowlFilled,
                                density = density,
                                onDrag = { dragAmountPx ->
                                    val current =
                                        vm.state.value.world.bowlPosition
                                            ?: defaultBowlPositionState
                                            ?: Position(24f, playAreaBottom - BOWL_HEIGHT_DP - 16f)
                                    val dx = with(density) { dragAmountPx.x.toDp().value }
                                    val dy = with(density) { dragAmountPx.y.toDp().value }
                                    vm.onBowlMoved(Position(current.x + dx, current.y + dy))
                                },
                            )
                        is FloorSprite.Toy ->
                            state.world.toy?.let { toyPos ->
                                Image(
                                    painter = painterResource(R.drawable.toy),
                                    contentDescription = null,
                                    modifier =
                                        Modifier
                                            .offset(x = toyPos.x.dp, y = toyPos.y.dp)
                                            .size(TOY_SIZE_DP.dp),
                                )
                            }
                        is FloorSprite.Poop -> {
                            val i = sprite.index
                            val xOffset = poopOffsets[i]
                            val xDp = screenWidthDp / 2f + xOffset - POOP_SIZE_DP / 2f
                            val yDp = sprite.topLeftY
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
                                        }.size(POOP_SIZE_DP.dp),
                            )
                        }
                    }
                }
            }

            // Speech bubble — emitted after the floor decor so it always
            // floats above every floor sprite regardless of bottom-Y. Anchored
            // above the cat at its current position. The bubble measures its
            // own width and height via onSizeChanged, then
            // computeSpeechBubblePlacement clamps its X within the screen,
            // re-anchors the tail toward the cat, and derives Y from the
            // measured bubble height so the tail tip lands a fixed gap above
            // the cat's head regardless of how many lines the text wraps to
            // after the horizontal clamp (issue #37). It stays in composition
            // while unmeasured (alpha 0) so onSizeChanged fires; otherwise the
            // first frame would draw the tail at the bubble's left corner
            // before the clamp settles.
            if (behavior != null) {
                val spriteDpValue = stageSpriteSize(state.stage).value
                var bubbleWidthDp by remember { mutableFloatStateOf(0f) }
                var bubbleHeightDp by remember { mutableFloatStateOf(0f) }
                val placement =
                    remember(
                        behavior.position.x,
                        behavior.position.y,
                        spriteDpValue,
                        bubbleWidthDp,
                        bubbleHeightDp,
                        screenWidthDp,
                    ) {
                        if (bubbleWidthDp <= 0f || bubbleHeightDp <= 0f || screenWidthDp <= 0f) {
                            null
                        } else {
                            computeSpeechBubblePlacement(
                                catX = behavior.position.x,
                                catY = behavior.position.y,
                                catWidth = spriteDpValue,
                                bubbleWidth = bubbleWidthDp,
                                bubbleHeight = bubbleHeightDp,
                                screenWidth = screenWidthDp,
                                horizontalPadding = SPEECH_BUBBLE_EDGE_PADDING_DP,
                                tailMargin = SPEECH_BUBBLE_TAIL_MARGIN_DP,
                                tailTipGap = SPEECH_BUBBLE_TAIL_TIP_GAP_DP,
                            )
                        }
                    }
                val measured = placement != null
                val bubbleX = placement?.bubbleX ?: behavior.position.x
                val bubbleY = placement?.bubbleY ?: behavior.position.y
                val tailDp = placement?.tailX?.dp ?: (bubbleWidthDp / 2f).dp
                Box(
                    modifier =
                        Modifier
                            .offset(x = bubbleX.dp, y = bubbleY.dp)
                            .alpha(if (measured) 1f else 0f)
                            .onSizeChanged { sizePx ->
                                with(density) {
                                    bubbleWidthDp = sizePx.width.toDp().value
                                    bubbleHeightDp = sizePx.height.toDp().value
                                }
                            },
                ) {
                    SpeechBubble(
                        phrase = state.activePhrase,
                        onDismiss = vm::dismissPhrase,
                        tailX = tailDp,
                    )
                }
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
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val fingerStartDp =
                                    with(density) {
                                        Position(
                                            (down.position.x + trayRootOffsetPx.x).toDp().value,
                                            (down.position.y + trayRootOffsetPx.y).toDp().value,
                                        )
                                    }
                                val pickedItem =
                                    slotRects.entries
                                        .firstOrNull { (_, r) -> r.contains(fingerStartDp) }
                                        ?.key ?: return@awaitEachGesture
                                dragController.start(pickedItem, previewCenterFor(fingerStartDp))
                                down.consume()

                                var lifted = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val fingerDp =
                                        with(density) {
                                            Position(
                                                (change.position.x + trayRootOffsetPx.x).toDp().value,
                                                (change.position.y + trayRootOffsetPx.y).toDp().value,
                                            )
                                        }
                                    dragController.move(previewCenterFor(fingerDp))
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
                                val activeBowlPos =
                                    state.world.bowlPosition
                                        ?: defaultBowlPositionState
                                        ?: Position(24f, playAreaBottomLocal - BOWL_HEIGHT_DP - 16f)
                                val bowlRect =
                                    DpRect(
                                        left = activeBowlPos.x,
                                        top = activeBowlPos.y,
                                        right = activeBowlPos.x + BOWL_WIDTH_DP,
                                        bottom = activeBowlPos.y + BOWL_HEIGHT_DP,
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

        // Drag overlay — icon centred on the lifted preview position, above the finger.
        dragController.inFlight?.let { drag ->
            val drawableId =
                when (drag.item) {
                    Item.Food -> R.drawable.food
                    Item.Scoop -> R.drawable.scoop
                    Item.Toy -> R.drawable.toy
                    Item.Brush -> R.drawable.brush
                }
            val half = DRAG_PREVIEW_SIZE_DP / 2f
            Image(
                painter = painterResource(drawableId),
                contentDescription = null,
                modifier =
                    Modifier
                        .offset(x = (drag.position.x - half).dp, y = (drag.position.y - half).dp)
                        .size(DRAG_PREVIEW_SIZE_DP.dp),
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
 * Cat sprite + mood overlay at [behavior.position]. Extracted from [PetScreen]
 * so the cat can be emitted from the Y-sorted floor-decor list (issue #30).
 */
@Composable
private fun CatSprite(
    behavior: CatBehavior,
    stage: GrowthStage,
    mood: Mood,
    onLongPress: () -> Unit,
) {
    val animation = CatAnimations.forState(behavior.state)
    val spriteSize = stageSpriteSize(stage)
    val breathingScale = rememberBreathingScale()
    val applyBreathing = behavior.state != CatState.Walking
    Box(
        modifier =
            Modifier
                .offset(x = behavior.position.x.dp, y = behavior.position.y.dp)
                .size(spriteSize)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
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
            mood = mood,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Bowl sprite with long-press drag handler. Extracted from [PetScreen] so it
 * can be emitted from the Y-sorted floor-decor list (issue #30).
 */
@Composable
private fun BowlSprite(
    petId: Long,
    bowlPos: Position,
    bowlFilled: Boolean,
    density: Density,
    onDrag: (Offset) -> Unit,
) {
    Image(
        painter =
            painterResource(
                if (bowlFilled) R.drawable.bowl_full else R.drawable.bowl,
            ),
        contentDescription = null,
        modifier =
            Modifier
                .offset {
                    with(density) {
                        IntOffset(
                            x = bowlPos.x.dp.roundToPx(),
                            y = bowlPos.y.dp.roundToPx(),
                        )
                    }
                }.size(width = BOWL_WIDTH_DP.dp, height = BOWL_HEIGHT_DP.dp)
                .pointerInput(petId) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmountPx ->
                            change.consume()
                            onDrag(dragAmountPx)
                        },
                    )
                },
    )
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
