package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DropTargetsTest {
    private val playAreaRect = DpRect(0f, 0f, 200f, 200f)
    private val bowlRect = DpRect(20f, 160f, 84f, 192f)

    // Poops are positioned away from the bowl (right side of the play area) so they
    // don't fall inside the expanded food drop zone — keeps the "food on a poop is
    // null" assertion semantic instead of accidentally claiming the bowl.
    private val poopRects =
        listOf(
            DpRect(140f, 50f, 168f, 78f),
            DpRect(160f, 50f, 188f, 78f),
        )
    private val catRect = DpRect(60f, 60f, 140f, 140f)

    private fun resolve(
        item: Item,
        x: Float,
        y: Float,
    ) = dropTargetAt(Position(x, y), item, playAreaRect, bowlRect, poopRects, catRect)

    @Test
    fun `food on the bowl resolves to Bowl`() {
        assertThat(resolve(Item.Food, bowlRect.left + 10f, bowlRect.top + 10f))
            .isEqualTo(DropTarget.Bowl)
    }

    @Test
    fun `food off the bowl resolves to null`() {
        assertThat(resolve(Item.Food, 100f, 50f)).isNull()
    }

    @Test
    fun `food just outside bowl sprite but in the expanded floor band hits the bowl`() {
        // The visible bowl sprite is bowlRect (20..84, 160..192). Drops slightly to the
        // right (16dp gap is well within the expanded zone) or just above the bowl
        // top edge should now count as hitting the bowl, even though they're outside
        // the sprite rect. This is the fix for #29 — the 64x32dp sprite is too small
        // a target to reliably hit with a finger drop.
        assertThat(resolve(Item.Food, bowlRect.right + 16f, bowlRect.top + 8f))
            .isEqualTo(DropTarget.Bowl)
        assertThat(resolve(Item.Food, bowlRect.left + 32f, bowlRect.top - 24f))
            .isEqualTo(DropTarget.Bowl)
    }

    @Test
    fun `food in the floor band below the bowl still hits the bowl`() {
        // Drops that land between the bowl and the inventory tray (i.e. in the
        // visible floor area below the bowl) should also count, since the user is
        // clearly aiming at the bowl when releasing food in that floor band.
        val (bx, _) = bowlRect.center()
        assertThat(resolve(Item.Food, bx, playAreaRect.bottom - 1f))
            .isEqualTo(DropTarget.Bowl)
    }

    @Test
    fun `food well above the bowl beyond the slack still resolves to null`() {
        // Drops far above the bowl (well outside any reasonable slack) must not
        // claim the bowl — this keeps the "far away" rejection behavior intact.
        val (bx, _) = bowlRect.center()
        assertThat(resolve(Item.Food, bx, 10f)).isNull()
    }

    @Test
    fun `food horizontally far from the bowl still resolves to null`() {
        // A drop way to the right of the bowl (well outside the horizontal band)
        // must not claim the bowl.
        assertThat(resolve(Item.Food, playAreaRect.right - 1f, bowlRect.top + 8f))
            .isNull()
    }

    @Test
    fun `food on a poop rect still resolves to null (food only goes in bowl)`() {
        val (px, py) = poopRects[0].center()
        assertThat(resolve(Item.Food, px, py)).isNull()
    }

    @Test
    fun `scoop on a poop resolves to that Poop index`() {
        val (px, py) = poopRects[1].center()
        assertThat(resolve(Item.Scoop, px, py)).isEqualTo(DropTarget.Poop(1))
    }

    @Test
    fun `scoop off any poop resolves to null`() {
        assertThat(resolve(Item.Scoop, 10f, 10f)).isNull()
    }

    @Test
    fun `scoop on the bowl resolves to null (scoop only on poops)`() {
        assertThat(resolve(Item.Scoop, bowlRect.left + 10f, bowlRect.top + 10f)).isNull()
    }

    @Test
    fun `toy inside play area and not on bowl resolves to Floor at the drop position`() {
        val out = resolve(Item.Toy, 100f, 50f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(100f, 50f)))
    }

    @Test
    fun `toy outside play area resolves to null`() {
        assertThat(resolve(Item.Toy, -10f, 50f)).isNull()
        assertThat(resolve(Item.Toy, 1000f, 50f)).isNull()
        assertThat(resolve(Item.Toy, 50f, -10f)).isNull()
        assertThat(resolve(Item.Toy, 50f, 1000f)).isNull()
    }

    @Test
    fun `toy lands on Floor even if dropped on a poop (poop doesn't claim toys)`() {
        val (px, py) = poopRects[0].center()
        val out = resolve(Item.Toy, px, py)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(px, py)))
    }

    @Test
    fun `toy on the bowl resolves to Floor at the bowl coordinates (toy doesn't claim bowl)`() {
        val out = resolve(Item.Toy, bowlRect.left + 10f, bowlRect.top + 10f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(bowlRect.left + 10f, bowlRect.top + 10f)))
    }

    @Test
    fun `brush on the cat resolves to Cat`() {
        val (cx, cy) = catRect.center()
        assertThat(resolve(Item.Brush, cx, cy)).isEqualTo(DropTarget.Cat)
    }

    @Test
    fun `brush off the cat resolves to null`() {
        assertThat(resolve(Item.Brush, 10f, 10f)).isNull()
    }

    @Test
    fun `brush on the bowl resolves to null (brush only grooms the cat)`() {
        assertThat(resolve(Item.Brush, bowlRect.left + 10f, bowlRect.top + 10f)).isNull()
    }

    @Test
    fun `toy dropped near play-area bottom (where cat top-left could not sit) still resolves to Floor`() {
        // The pre-change implementation used HabitatBounds for the toy target, so a drop
        // below `bounds.maxY` (the cat-movable maxY = playArea.bottom - spriteHeight) was
        // rejected. With `playAreaRect`, the drop is accepted because the toy itself is
        // small and the visible floor extends to playAreaRect.bottom.
        val (cx, _) = playAreaRect.center()
        val nearBottom = Position(cx, playAreaRect.bottom - 1f)
        val out = dropTargetAt(nearBottom, Item.Toy, playAreaRect, bowlRect, poopRects, catRect)
        assertThat(out).isEqualTo(DropTarget.Floor(nearBottom))
    }
}
