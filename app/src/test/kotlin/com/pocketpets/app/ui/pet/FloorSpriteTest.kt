package com.pocketpets.app.ui.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests the pure depth-sorting helper used by [PetScreen] so floor-level
 * sprites (cat, bowl, toy, poop) draw in painter's order by their bottom-Y
 * anchor — smaller Y first, larger Y last (on top).
 *
 * See issue #30: prior to the fix the bowl always rendered above the cat
 * regardless of relative position.
 */
class FloorSpriteTest {
    @Test
    fun `cat below bowl on screen draws after bowl`() {
        // Cat sprite top-left y=300, sprite size 256 -> feet at 556.
        // Bowl top-left y=400, bowl height 32 -> feet at 432.
        // Cat's feet are lower on screen, so cat draws after bowl.
        val cat = FloorSprite.Cat(topLeftY = 300f, spriteSizeDp = 256f)
        val bowl = FloorSprite.Bowl(topLeftY = 400f)

        val sorted = floorSpriteOrder(listOf(bowl, cat))

        assertThat(sorted).containsExactly(bowl, cat).inOrder()
    }

    @Test
    fun `cat above bowl on screen draws before bowl`() {
        // Cat top-left y=100, sprite 256 -> feet at 356.
        // Bowl top-left y=400, height 32 -> feet at 432.
        // Bowl's feet are lower, so bowl draws after cat.
        val cat = FloorSprite.Cat(topLeftY = 100f, spriteSizeDp = 256f)
        val bowl = FloorSprite.Bowl(topLeftY = 400f)

        val sorted = floorSpriteOrder(listOf(cat, bowl))

        assertThat(sorted).containsExactly(cat, bowl).inOrder()
    }

    @Test
    fun `mixed sprites sort by bottom Y ascending`() {
        val cat = FloorSprite.Cat(topLeftY = 200f, spriteSizeDp = 256f) // feet 456
        val bowl = FloorSprite.Bowl(topLeftY = 480f) // feet 512
        val toy = FloorSprite.Toy(topLeftY = 100f) // feet 148 (toy size 48)
        val poop = FloorSprite.Poop(index = 0, topLeftY = 400f) // feet 448

        val sorted = floorSpriteOrder(listOf(bowl, toy, cat, poop))

        // Order by feet: toy(148) < poop(448) < cat(456) < bowl(512).
        assertThat(sorted).containsExactly(toy, poop, cat, bowl).inOrder()
    }

    @Test
    fun `stable order preserves input sequence on tied Y`() {
        // Two poops at identical Y must keep their input order, so index 0
        // doesn't randomly trade places with index 1 between recompositions.
        val poopA = FloorSprite.Poop(index = 0, topLeftY = 400f)
        val poopB = FloorSprite.Poop(index = 1, topLeftY = 400f)

        val sorted = floorSpriteOrder(listOf(poopA, poopB))

        assertThat(sorted).containsExactly(poopA, poopB).inOrder()
    }

    @Test
    fun `empty input returns empty list`() {
        assertThat(floorSpriteOrder(emptyList())).isEmpty()
    }

    @Test
    fun `single sprite returned as-is`() {
        val bowl = FloorSprite.Bowl(topLeftY = 100f)
        assertThat(floorSpriteOrder(listOf(bowl))).containsExactly(bowl).inOrder()
    }

    @Test
    fun `cat below toy on screen draws after toy`() {
        // Toy top-left y=100, size 48 -> feet at 148.
        // Cat top-left y=200, sprite 256 -> feet at 456.
        // Cat's feet are lower, so cat draws after toy.
        val toy = FloorSprite.Toy(topLeftY = 100f)
        val cat = FloorSprite.Cat(topLeftY = 200f, spriteSizeDp = 256f)

        val sorted = floorSpriteOrder(listOf(cat, toy))

        assertThat(sorted).containsExactly(toy, cat).inOrder()
    }
}
