package com.pocketpets.app.domain.behavior

import com.pocketpets.app.domain.Mood
import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure transitions for the cat behaviour state machine. All public functions
 * are deterministic given their inputs (Random must be seeded to be
 * deterministic).
 */
object CatBehaviorRules {
    const val DEFAULT_SPEED_DP_PER_SEC = 60f
    const val ARRIVAL_EPSILON_DP = 2f
    const val MIN_WANDER_SECONDS = 30L
    const val MAX_WANDER_SECONDS = 60L

    /**
     * Direction the cat would face if walking from [from] to [to]. When |dx| == |dy|
     * (45° diagonal or no movement), the vertical axis wins; equal positions return
     * SOUTH as the documented default so the renderer always has a valid facing.
     */
    fun directionOf(from: Position, to: Position): Direction {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return if (abs(dy) >= abs(dx)) {
            if (dy < 0f) Direction.NORTH else Direction.SOUTH
        } else {
            if (dx < 0f) Direction.WEST else Direction.EAST
        }
    }

    /**
     * Returns an Instant in `[now + MIN_WANDER_SECONDS, now + MAX_WANDER_SECONDS]`
     * (inclusive on both ends). Uses [rng] for jitter; deterministic given a
     * seeded Random.
     */
    fun nextWanderInstant(now: Instant, rng: Random): Instant {
        val deltaSec = rng.nextLong(MIN_WANDER_SECONDS, MAX_WANDER_SECONDS + 1)
        return Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + deltaSec * 1000L)
    }

    /**
     * Returns the target the cat should walk to next given current [mood].
     * Mood-driven destinations take priority; otherwise picks a uniform-random
     * point inside [bounds].
     */
    fun pickTarget(
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
    ): Position = when (mood) {
        Mood.SLEEPY -> anchors.bed
        Mood.HUNGRY -> anchors.bowl
        else -> Position(
            x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
            y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
        )
    }

    /**
     * Pure forward step. Given the current [b]ehavior, produces the next one.
     * The function is the only mutator of [CatBehavior] in the codebase; the
     * ViewModel calls it on a frame ticker and stores the result in a flow.
     */
    fun tick(
        b: CatBehavior,
        now: Instant,
        dtSeconds: Float,
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        speedDpPerSec: Float = DEFAULT_SPEED_DP_PER_SEC,
    ): CatBehavior {
        if (dtSeconds <= 0f) return b

        // Mood-driven anchor target preempts everything else.
        val moodAnchor: Position? = when (mood) {
            Mood.SLEEPY -> anchors.bed
            Mood.HUNGRY -> anchors.bowl
            else -> null
        }

        // Lying cat with no reason to move stays put.
        if (b.state == CatState.Lying) {
            if (moodAnchor != null && moodAnchor == b.position) return b
            if (mood == Mood.SLEEPY) return b
            // Wake up and walk somewhere.
            val target = moodAnchor ?: pickTarget(mood, bounds, anchors, rng)
            return walkingToward(b, target)
        }

        // Idle cat: start moving if mood demands it, or the wander timer fired.
        if (b.state == CatState.Idle) {
            return when {
                moodAnchor != null && moodAnchor != b.position -> walkingToward(b, moodAnchor)
                now >= b.nextWanderAt -> walkingToward(
                    b,
                    pickTarget(mood, bounds, anchors, rng),
                )
                else -> b
            }
        }

        // Walking cat. Possibly retarget to the mood anchor; then advance.
        val effectiveTarget = moodAnchor ?: b.target
        val advanced = advance(b.position, effectiveTarget, speedDpPerSec, dtSeconds)
        val arrived = isArrived(advanced, effectiveTarget)
        return when {
            !arrived -> b.copy(
                position = advanced,
                target = effectiveTarget,
                facing = directionOf(b.position, effectiveTarget),
            )
            effectiveTarget == anchors.bed -> b.copy(
                state = CatState.Lying,
                position = effectiveTarget,
                target = effectiveTarget,
                facing = directionOf(b.position, effectiveTarget),
            )
            else -> b.copy(
                state = CatState.Idle,
                position = effectiveTarget,
                target = effectiveTarget,
                facing = directionOf(b.position, effectiveTarget),
                nextWanderAt = nextWanderInstant(now, rng),
            )
        }
    }

    private fun walkingToward(b: CatBehavior, target: Position) = b.copy(
        state = CatState.Walking,
        target = target,
        facing = directionOf(b.position, target),
    )

    private fun advance(from: Position, to: Position, speed: Float, dt: Float): Position {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= ARRIVAL_EPSILON_DP) return to
        val maxStep = speed * dt
        if (maxStep >= dist) return to
        val ratio = maxStep / dist
        return Position(from.x + dx * ratio, from.y + dy * ratio)
    }

    private fun isArrived(at: Position, target: Position): Boolean {
        val dx = target.x - at.x
        val dy = target.y - at.y
        return sqrt(dx * dx + dy * dy) <= ARRIVAL_EPSILON_DP
    }
}

private fun Random.nextFloatInRange(min: Float, max: Float): Float =
    min + nextFloat() * (max - min)
