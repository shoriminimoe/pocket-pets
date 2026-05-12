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
                b,
                t0,
                0.016f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
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
                b,
                t0,
                0.016f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
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

    @Test
    fun `toy in world preempts random idle wander`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(toy)
    }

    @Test
    fun `toy in world preempts hungry+filled-bowl pull`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(bowlFilled = true, toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(toy)
    }

    @Test
    fun `sleepy beats toy — sleepy cat still goes to bed even with toy out`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.SLEEPY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.target).isEqualTo(anchors.bed)
    }

    @Test
    fun `walking cat redirects to a toy thrown mid-walk`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b = behavior(state = CatState.Walking, x = 50f, y = 50f, targetX = 100f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.target).isEqualTo(toy)
    }

    @Test
    fun `walking hungry cat that arrives at filled bowl becomes Eating with stateUntil 5 seconds out`() {
        val world = HabitatWorld(bowlFilled = true)
        val b =
            behavior(
                state = CatState.Walking,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            )
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.1f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Eating)
        assertThat(out.stateUntil).isNotNull()
        val deltaSec = (out.stateUntil!!.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isEqualTo(CatBehaviorRules.EATING_DURATION_SECONDS)
    }

    @Test
    fun `walking cat that arrives at bowl spot but bowl is empty still becomes Idle`() {
        val world = HabitatWorld(bowlFilled = false)
        val b =
            behavior(
                state = CatState.Walking,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            )
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.1f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
    }

    @Test
    fun `walking cat that arrives at toy becomes Playing with stateUntil 10 seconds out`() {
        val toy = Position(60f, 70f)
        val world = HabitatWorld(toy = toy)
        val b =
            behavior(
                state = CatState.Walking,
                x = toy.x,
                y = toy.y,
                targetX = toy.x,
                targetY = toy.y,
            )
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.1f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Playing)
        assertThat(out.stateUntil).isNotNull()
        val deltaSec = (out.stateUntil!!.toEpochMilliseconds() - t0.toEpochMilliseconds()) / 1000L
        assertThat(deltaSec).isEqualTo(CatBehaviorRules.PLAYING_DURATION_SECONDS)
    }

    @Test
    fun `eating cat exits to Idle when stateUntil is reached`() {
        val until = t0.plusSeconds(5)
        val b =
            behavior(
                state = CatState.Eating,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            ).copy(stateUntil = until)
        val later = until.plusSeconds(1)
        val out =
            CatBehaviorRules.tick(
                b,
                later,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = HabitatWorld(bowlFilled = false),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
        assertThat(out.stateUntil).isNull()
        val nextDelta = (out.nextWanderAt.toEpochMilliseconds() - later.toEpochMilliseconds()) / 1000L
        assertThat(nextDelta).isAtLeast(CatBehaviorRules.MIN_WANDER_SECONDS)
        assertThat(nextDelta).isAtMost(CatBehaviorRules.MAX_WANDER_SECONDS)
    }

    @Test
    fun `eating cat stays Eating before stateUntil`() {
        val until = t0.plusSeconds(5)
        val b =
            behavior(
                state = CatState.Eating,
                x = anchors.bowl.x,
                y = anchors.bowl.y,
                targetX = anchors.bowl.x,
                targetY = anchors.bowl.y,
            ).copy(stateUntil = until)
        val partway = t0.plusSeconds(2)
        val out =
            CatBehaviorRules.tick(
                b,
                partway,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = HabitatWorld(),
            )
        assertThat(out.state).isEqualTo(CatState.Eating)
        assertThat(out.stateUntil).isEqualTo(until)
    }

    @Test
    fun `tick clamps a position above bounds back inside bounds`() {
        // Initial position above the floor (e.g., screen layout shrank the floor
        // upward). The cat must snap back into bounds rather than render above
        // the play area while it lerps toward an in-bounds target.
        val b = behavior(state = CatState.Idle, x = 100f, y = bounds.minY - 50f)
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.position.y).isAtLeast(bounds.minY)
        assertThat(out.position.y).isAtMost(bounds.maxY)
        assertThat(out.position.x).isAtLeast(bounds.minX)
        assertThat(out.position.x).isAtMost(bounds.maxX)
    }

    @Test
    fun `tick clamps a position right of bounds back inside bounds`() {
        // Bounds shrunk (e.g., growth stage bumped the sprite size, reducing
        // the playable rectangle). The cat must snap left into bounds.
        val b = behavior(state = CatState.Idle, x = bounds.maxX + 80f, y = 50f)
        val out = CatBehaviorRules.tick(b, t0, 0.016f, Mood.IDLE, bounds, anchors, Random(0))
        assertThat(out.position.x).isAtMost(bounds.maxX)
    }

    // ----- toy clamping (issue #28) ----------------------------------------

    @Test
    fun `toy dropped past right edge clamps cat target to bounds, toy world position untouched`() {
        // Toy dropped where the cat sprite top-left could not fit (right of maxX).
        val rawToy = Position(bounds.maxX + 80f, 50f)
        val world = HabitatWorld(toy = rawToy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        // Cat target must be inside walkable bounds.
        assertThat(out.target.x).isAtMost(bounds.maxX)
        assertThat(out.target.x).isAtLeast(bounds.minX)
        assertThat(out.target.y).isAtMost(bounds.maxY)
        assertThat(out.target.y).isAtLeast(bounds.minY)
    }

    @Test
    fun `toy dropped below bottom edge clamps cat target to bounds`() {
        // playAreaRect extends below bounds.maxY (cat top-left can't sit that low).
        val rawToy = Position(50f, bounds.maxY + 60f)
        val world = HabitatWorld(toy = rawToy)
        val b = behavior(state = CatState.Idle, x = 10f, y = 10f, targetX = 10f, targetY = 10f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target.y).isAtMost(bounds.maxY)
        assertThat(out.target.y).isAtLeast(bounds.minY)
    }

    @Test
    fun `toy dropped past left and top edges clamps cat target to bounds`() {
        // Toy dropped above and left of bounds (raw playAreaRect can extend past
        // minX/minY just as it can past maxX/maxY). Symmetric coverage with the
        // right/bottom cases above to lock in both ends of the coerceIn clamp.
        val rawToy = Position(-50f, -50f)
        val world = HabitatWorld(toy = rawToy)
        val b = behavior(state = CatState.Idle, x = 100f, y = 50f, targetX = 100f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target.x).isAtLeast(bounds.minX)
        assertThat(out.target.x).isAtMost(bounds.maxX)
        assertThat(out.target.y).isAtLeast(bounds.minY)
        assertThat(out.target.y).isAtMost(bounds.maxY)
    }

    @Test
    fun `walking cat that reaches clamped toy anchor becomes Playing even when toy is out of bounds`() {
        // Cat already at the clamped anchor (right edge); toy itself is past the edge.
        val rawToy = Position(bounds.maxX + 80f, 50f)
        val world = HabitatWorld(toy = rawToy)
        val anchor = bounds.clamp(rawToy)
        val b =
            behavior(
                state = CatState.Walking,
                x = anchor.x,
                y = anchor.y,
                targetX = anchor.x,
                targetY = anchor.y,
            )
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.1f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        // Without the fix the cat oscillates and never enters Playing.
        assertThat(out.state).isEqualTo(CatState.Playing)
        assertThat(out.stateUntil).isNotNull()
    }

    @Test
    fun `hungry cat targets world bowlPosition, not the cached anchors bowl`() {
        // Stale anchor case: the world's bowlPosition has moved to x=150, but
        // the anchor cache still points to x=20. The cat must follow the world's
        // bowl, not the stale anchor.
        val world = HabitatWorld(bowlFilled = true, bowlPosition = Position(150f, 60f))
        val b = behavior(state = CatState.Idle, x = 50f, y = 50f, targetX = 50f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target.x).isEqualTo(150f)
        // Floor-y from anchors.bowl is preserved so the cat stands on the floor.
        assertThat(out.target.y).isEqualTo(anchors.bowl.y)
    }

    @Test
    fun `hungry cat falls back to anchors bowl when world bowlPosition is null`() {
        // Early-measurement case: the bowl has been filled but the screen
        // hasn't reported its rendered position yet (world.bowlPosition is
        // null), so bowlAnchor falls back to the cached anchors.bowl.
        val world = HabitatWorld(bowlFilled = true, bowlPosition = null)
        val b = behavior(state = CatState.Idle, x = 100f, y = 50f, targetX = 100f, targetY = 50f)
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.016f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Walking)
        assertThat(out.target).isEqualTo(anchors.bowl)
    }

    @Test
    fun `cat arriving at world bowlPosition transitions to Eating`() {
        val world = HabitatWorld(bowlFilled = true, bowlPosition = Position(150f, 60f))
        val targetX = 150f
        val targetY = anchors.bowl.y
        val b =
            behavior(
                state = CatState.Walking,
                x = targetX,
                y = targetY,
                targetX = targetX,
                targetY = targetY,
            )
        val out =
            CatBehaviorRules.tick(
                b,
                t0,
                0.1f,
                Mood.HUNGRY,
                bounds,
                anchors,
                Random(0),
                world = world,
            )
        assertThat(out.state).isEqualTo(CatState.Eating)
    }

    @Test
    fun `playing cat exits to Idle when stateUntil is reached`() {
        val until = t0.plusSeconds(10)
        val b =
            behavior(
                state = CatState.Playing,
                x = 60f,
                y = 70f,
                targetX = 60f,
                targetY = 70f,
            ).copy(stateUntil = until)
        val later = until.plusSeconds(1)
        val out =
            CatBehaviorRules.tick(
                b,
                later,
                0.016f,
                Mood.IDLE,
                bounds,
                anchors,
                Random(0),
                world = HabitatWorld(),
            )
        assertThat(out.state).isEqualTo(CatState.Idle)
        assertThat(out.stateUntil).isNull()
    }
}
