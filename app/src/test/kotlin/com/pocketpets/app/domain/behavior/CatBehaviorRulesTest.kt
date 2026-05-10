package com.pocketpets.app.domain.behavior

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.Mood
import com.pocketpets.app.domain.behavior.HabitatWorld
import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.random.Random

class CatBehaviorRulesTest {
    private val bounds = HabitatBounds(0f, 0f, 200f, 100f)
    private val anchors = Anchors(bed = Position(180f, 80f), bowl = Position(20f, 80f))
    private val t0 = Instant.parse("2026-05-09T12:00:00Z")

    private fun behavior(
        state: CatState = CatState.Idle,
        x: Float = 100f,
        y: Float = 50f,
        targetX: Float = 100f,
        targetY: Float = 50f,
        facing: Direction = Direction.SOUTH,
        nextWanderAt: Instant = t0.plusSeconds(45),
    ) = CatBehavior(
        state = state,
        position = Position(x, y),
        target = Position(targetX, targetY),
        facing = facing,
        nextWanderAt = nextWanderAt,
    )

    private fun Instant.plusSeconds(s: Long): Instant = Instant.fromEpochMilliseconds(toEpochMilliseconds() + s * 1000)

    // ----- directionOf ------------------------------------------------------

    @Test
    fun `direction east when target is purely right`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(10f, 0f)))
            .isEqualTo(Direction.EAST)
    }

    @Test
    fun `direction west when target is purely left`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(-10f, 0f)))
            .isEqualTo(Direction.WEST)
    }

    @Test
    fun `direction north when target is purely up`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(0f, -10f)))
            .isEqualTo(Direction.NORTH)
    }

    @Test
    fun `direction south when target is purely down`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(0f, 10f)))
            .isEqualTo(Direction.SOUTH)
    }

    @Test
    fun `tie favours vertical axis`() {
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(10f, 10f)))
            .isEqualTo(Direction.SOUTH)
        assertThat(CatBehaviorRules.directionOf(Position(0f, 0f), Position(-10f, -10f)))
            .isEqualTo(Direction.NORTH)
    }

    @Test
    fun `equal positions return SOUTH as default`() {
        assertThat(CatBehaviorRules.directionOf(Position(5f, 5f), Position(5f, 5f)))
            .isEqualTo(Direction.SOUTH)
    }

    // ----- nextWanderInstant -----------------------------------------------

    @Test
    fun `next wander instant is in the configured window`() {
        repeat(50) { seed ->
            val next = CatBehaviorRules.nextWanderInstant(t0, Random(seed.toLong()))
            val deltaSec = (next.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
            assertThat(deltaSec).isIn(
                Range.closed(
                    CatBehaviorRules.MIN_WANDER_SECONDS,
                    CatBehaviorRules.MAX_WANDER_SECONDS,
                ),
            )
        }
    }

    // ----- pickTarget ------------------------------------------------------

    @Test
    fun `pickTarget chooses bed when sleepy`() {
        assertThat(CatBehaviorRules.pickTarget(Mood.SLEEPY, bounds, anchors, Random(0)))
            .isEqualTo(anchors.bed)
    }

    @Test
    fun `pickTarget chooses bowl when hungry and bowl is filled`() {
        val world = HabitatWorld(bowlFilled = true)
        assertThat(CatBehaviorRules.pickTarget(Mood.HUNGRY, bounds, anchors, Random(0), world))
            .isEqualTo(anchors.bowl)
    }

    @Test
    fun `pickTarget hungry with empty bowl falls through to a random point in bounds`() {
        val world = HabitatWorld(bowlFilled = false)
        for (seed in 0..20) {
            val t = CatBehaviorRules.pickTarget(Mood.HUNGRY, bounds, anchors, Random(seed.toLong()), world)
            assertThat(t).isNotEqualTo(anchors.bowl)
            assertThat(t.x).isAtLeast(bounds.minX)
            assertThat(t.x).isAtMost(bounds.maxX)
            assertThat(t.y).isAtLeast(bounds.minY)
            assertThat(t.y).isAtMost(bounds.maxY)
        }
    }

    @Test
    fun `pickTarget returns a point inside bounds for non-anchor moods`() {
        val nonAnchorMoods = listOf(Mood.IDLE, Mood.HAPPY, Mood.SAD, Mood.GROSSED_OUT)
        for (mood in nonAnchorMoods) {
            for (seed in 0..20) {
                val t = CatBehaviorRules.pickTarget(mood, bounds, anchors, Random(seed.toLong()))
                assertThat(t.x).isAtLeast(bounds.minX)
                assertThat(t.x).isAtMost(bounds.maxX)
                assertThat(t.y).isAtLeast(bounds.minY)
                assertThat(t.y).isAtMost(bounds.maxY)
            }
        }
    }

    @Test
    fun `pickTarget is deterministic given the same seed`() {
        val a = CatBehaviorRules.pickTarget(Mood.IDLE, bounds, anchors, Random(42))
        val b = CatBehaviorRules.pickTarget(Mood.IDLE, bounds, anchors, Random(42))
        assertThat(a).isEqualTo(b)
    }

    // ----- tick ------------------------------------------------------------

    @Test
    fun `tick with dt=0 is a no-op`() {
        val b = behavior(state = CatState.Walking, x = 0f, targetX = 100f)
        val out = CatBehaviorRules.tick(b, t0, 0f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out).isEqualTo(b)
    }

    @Test
    fun `walking cat advances toward target by speed times dt`() {
        val b = behavior(state = CatState.Walking, x = 0f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(b, t0, 1f, Mood.IDLE, bounds, anchors, Random(0), speedDpPerSec = 60f)
        assertThat(out.position.x).isWithin(0.01f).of(60f)
        assertThat(out.position.y).isEqualTo(0f)
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.facing).isEqualTo(Direction.EAST)
    }

    @Test
    fun `walking cat clamps to target when next step would overshoot`() {
        val b = behavior(state = CatState.Walking, x = 95f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(b, t0, 1f, Mood.IDLE, bounds, anchors, Random(0), speedDpPerSec = 60f)
        assertThat(out.position).isEqualTo(Position(100f, 0f))
    }

    @Test
    fun `walking cat that arrives at non-bed target becomes Idle and reschedules wander`() {
        val arrivedAt = Position(40f, 40f)
        val b =
            behavior(
                state = CatState.Walking,
                x = arrivedAt.x,
                y = arrivedAt.y,
                targetX = arrivedAt.x,
                targetY = arrivedAt.y,
            )
        val out = CatBehaviorRules.tick(b, t0, 0.1f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.state).isEqualTo(CatState.Idle)
        val deltaSec = (out.nextWanderAt.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isAtLeast(CatBehaviorRules.MIN_WANDER_SECONDS)
        assertThat(deltaSec).isAtMost(CatBehaviorRules.MAX_WANDER_SECONDS)
    }

    @Test
    fun `walking cat that arrives at bed becomes Lying`() {
        val b =
            behavior(
                state = CatState.Walking,
                x = anchors.bed.x,
                y = anchors.bed.y,
                targetX = anchors.bed.x,
                targetY = anchors.bed.y,
            )
        val out = CatBehaviorRules.tick(b, t0, 0.1f, Mood.SLEEPY, bounds, anchors, Random(0))
        assertThat(out.state).isEqualTo(CatState.Lying)
    }

    @Test
    fun `idle cat that becomes sleepy walks toward bed`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.SLEEPY, bounds, anchors, Random(0))
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test
    fun `idle cat that becomes hungry walks toward filled bowl`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = HabitatWorld(bowlFilled = true),
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bowl)
    }

    @Test
    fun `idle cat that becomes hungry but bowl is empty does not walk to bowl`() {
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        // nextWanderAt is in the future, so without the bowl pull the cat stays idle.
        val out =
            CatBehaviorRules.tick(
                b, t0, 0.016f, Mood.HUNGRY, bounds, anchors, Random(0),
                world = HabitatWorld(bowlFilled = false),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
    }

    @Test
    fun `idle cat starts wandering when nextWanderAt has passed`() {
        val due = t0.plusSeconds(-1)
        val b = behavior(state = CatState.Idle, nextWanderAt = due)
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target.x).isAtLeast(bounds.minX)
        assertThat(out.target.x).isAtMost(bounds.maxX)
    }

    @Test
    fun `lying cat wakes up and walks when no longer sleepy`() {
        val b =
            behavior(
                state = CatState.Lying,
                x = anchors.bed.x,
                y = anchors.bed.y,
                targetX = anchors.bed.x,
                targetY = anchors.bed.y,
            )
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.state).isEqualTo(CatState.Walking)
    }

    @Test
    fun `mood flip mid-walk preempts current target`() {
        val b = behavior(state = CatState.Walking, x = 50f, y = 50f, targetX = 100f, targetY = 50f)
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.SLEEPY, bounds, anchors, Random(0))
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test
    fun `huge dt does not teleport past the target`() {
        val b = behavior(state = CatState.Walking, x = 95f, y = 0f, targetX = 100f, targetY = 0f)
        val out = CatBehaviorRules.tick(b, t0, 100f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.position).isEqualTo(Position(100f, 0f))
    }
}
