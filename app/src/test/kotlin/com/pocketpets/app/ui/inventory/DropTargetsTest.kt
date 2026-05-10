package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Anchors
import com.pocketpets.app.domain.behavior.HabitatBounds
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DropTargetsTest {
    private val bounds = HabitatBounds(0f, 0f, 200f, 200f)
    private val anchors = Anchors(bed = Position(160f, 160f), bowl = Position(20f, 160f))
    private val poopRects =
        listOf(
            DpRect(80f, 100f, 128f, 148f),
            DpRect(120f, 100f, 168f, 148f),
        )

    private fun resolve(
        item: Item,
        x: Float,
        y: Float,
    ) = dropTargetAt(Position(x, y), item, bounds, anchors, poopRects)

    @Test
    fun `food on the bowl resolves to Bowl`() {
        assertThat(resolve(Item.Food, anchors.bowl.x + 10f, anchors.bowl.y + 10f))
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
        assertThat(resolve(Item.Scoop, anchors.bowl.x + 10f, anchors.bowl.y + 10f)).isNull()
    }

    @Test
    fun `toy inside bounds and not on bowl resolves to Floor at the drop position`() {
        val out = resolve(Item.Toy, 100f, 50f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(100f, 50f)))
    }

    @Test
    fun `toy outside bounds resolves to null`() {
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
        val out = resolve(Item.Toy, anchors.bowl.x + 10f, anchors.bowl.y + 10f)
        assertThat(out).isEqualTo(DropTarget.Floor(Position(anchors.bowl.x + 10f, anchors.bowl.y + 10f)))
    }
}
