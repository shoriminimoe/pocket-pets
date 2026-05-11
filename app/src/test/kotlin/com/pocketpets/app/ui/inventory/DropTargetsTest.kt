package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DropTargetsTest {
    private val playAreaRect = DpRect(0f, 0f, 200f, 200f)
    private val bowlRect = DpRect(20f, 160f, 84f, 192f)
    private val poopRects =
        listOf(
            DpRect(80f, 100f, 128f, 148f),
            DpRect(120f, 100f, 168f, 148f),
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
