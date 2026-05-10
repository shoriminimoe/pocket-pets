package com.pocketpets.app.ui.inventory

import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.behavior.Position
import org.junit.Test

class DragControllerTest {
    @Test
    fun `inFlight is null at rest`() {
        val c = DragController()
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `start sets the in-flight item with given initial position`() {
        val c = DragController()
        c.start(Item.Food, Position(40f, 50f))
        assertThat(c.inFlight?.item).isEqualTo(Item.Food)
        assertThat(c.inFlight?.position).isEqualTo(Position(40f, 50f))
    }

    @Test
    fun `move updates the position while keeping the item`() {
        val c = DragController()
        c.start(Item.Toy, Position(0f, 0f))
        c.move(Position(50f, 60f))
        assertThat(c.inFlight?.item).isEqualTo(Item.Toy)
        assertThat(c.inFlight?.position).isEqualTo(Position(50f, 60f))
    }

    @Test
    fun `move with no in-flight drag is a no-op`() {
        val c = DragController()
        c.move(Position(10f, 10f))
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `end returns the in-flight drag and clears it`() {
        val c = DragController()
        c.start(Item.Scoop, Position(0f, 0f))
        c.move(Position(5f, 5f))
        val ended = c.end()
        assertThat(ended?.item).isEqualTo(Item.Scoop)
        assertThat(ended?.position).isEqualTo(Position(5f, 5f))
        assertThat(c.inFlight).isNull()
    }

    @Test
    fun `end with no in-flight drag returns null`() {
        val c = DragController()
        assertThat(c.end()).isNull()
    }
}
