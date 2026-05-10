package com.pocketpets.app.ui.inventory

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pocketpets.app.R

/**
 * Bottom-of-screen tray with three layout-only slots: Food, Scoop, Toy.
 * Each slot reports its screen-relative rect (in dp) via [onSlotPositionChange]
 * so the parent screen can resolve a long-press start back to the picked-up
 * item without owning the gesture handler itself.
 */
@Composable
fun InventoryTray(
    onSlotPositionChange: (Item, leftDp: Float, topDp: Float, sizeDp: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0x66000000))
                .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TraySlot(item = Item.Food, drawable = R.drawable.food, onSlotPositionChange = onSlotPositionChange)
        TraySlot(item = Item.Scoop, drawable = R.drawable.scoop, onSlotPositionChange = onSlotPositionChange)
        TraySlot(item = Item.Toy, drawable = R.drawable.toy, onSlotPositionChange = onSlotPositionChange)
    }
}

@Composable
private fun TraySlot(
    item: Item,
    drawable: Int,
    onSlotPositionChange: (Item, leftDp: Float, topDp: Float, sizeDp: Float) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    with(density) {
                        onSlotPositionChange(
                            item,
                            pos.x.toDp().value,
                            pos.y.toDp().value,
                            coords.size.width
                                .toDp()
                                .value,
                        )
                    }
                },
    ) {
        Image(
            painter = painterResource(drawable),
            contentDescription = item.name,
            modifier = Modifier.size(64.dp),
        )
    }
}
