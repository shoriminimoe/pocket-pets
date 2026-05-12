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
    const val EATING_DURATION_SECONDS = 5L
    const val PLAYING_DURATION_SECONDS = 10L

    /**
     * Direction the cat would face if walking from [from] to [to]. When |dx| == |dy|
     * (45° diagonal or no movement), the vertical axis wins; equal positions return
     * SOUTH as the documented default so the renderer always has a valid facing.
     */
    fun directionOf(
        from: Position,
        to: Position,
    ): Direction {
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
    fun nextWanderInstant(
        now: Instant,
        rng: Random,
    ): Instant {
        val deltaSec = rng.nextLong(MIN_WANDER_SECONDS, MAX_WANDER_SECONDS + 1)
        return Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + deltaSec * 1000L)
    }

    /**
     * Returns the target the cat should walk to next given current [mood] and
     * [world]. World-driven destinations (toy thrown, bowl filled while hungry)
     * take priority; otherwise mood-driven destinations; otherwise a uniform-random
     * point inside [bounds]. The default empty [world] preserves the prior
     * behaviour (mood-anchor + random) until the world-aware paths are added.
     */
    fun pickTarget(
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        world: HabitatWorld = HabitatWorld(),
    ): Position =
        when (mood) {
            Mood.SLEEPY -> anchors.bed
            Mood.HUNGRY ->
                if (world.bowlFilled) {
                    bowlAnchor(anchors, bounds, world)
                } else {
                    Position(
                        x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
                        y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
                    )
                }
            else ->
                Position(
                    x = rng.nextFloatInRange(bounds.minX, bounds.maxX),
                    y = rng.nextFloatInRange(bounds.minY, bounds.maxY),
                )
        }

    /**
     * Where the cat stands to eat. [world]'s `bowlPosition` is the source of
     * truth for the bowl's location, so the cat follows the bowl as the user
     * drags it. Falls back to [anchors] `bowl` while no world position is set
     * (i.e., before the screen has measured). The y is taken from `anchors.bowl`
     * so the cat's feet remain on the floor anchor line regardless of the
     * bowl's rendered y.
     *
     * Mirrors `com.pocketpets.app.ui.pet.bowlAnchorFor` in the ui layer. The
     * two helpers are intentionally duplicated because the domain layer is
     * pure Kotlin and cannot depend on ui-layer helpers (see CLAUDE.md
     * layering). Keep both in sync when the clamp/fallback shape changes.
     */
    fun bowlAnchor(
        anchors: Anchors,
        bounds: HabitatBounds,
        world: HabitatWorld,
    ): Position =
        world.bowlPosition?.let { bp ->
            Position(x = bp.x.coerceIn(bounds.minX, bounds.maxX), y = anchors.bowl.y)
        } ?: anchors.bowl

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
        world: HabitatWorld = HabitatWorld(),
    ): CatBehavior =
        tickInternal(
            b = snapIntoBounds(b, bounds),
            now = now,
            dtSeconds = dtSeconds,
            mood = mood,
            bounds = bounds,
            anchors = anchors,
            rng = rng,
            speedDpPerSec = speedDpPerSec,
            world = world,
        )

    /**
     * Snap [b]'s position into [bounds]. Bounds can shrink between ticks
     * (growth-stage bumps enlarge the sprite, contracting the playable
     * rectangle) and the ViewModel's initial position may sit above the floor
     * before the layout pass delivers real bounds. Without this, [advance]
     * lerps from out-of-bounds → in-bounds and renders the sprite past the
     * play area for multiple frames.
     */
    private fun snapIntoBounds(
        b: CatBehavior,
        bounds: HabitatBounds,
    ): CatBehavior {
        val snapped = bounds.clamp(b.position)
        return if (snapped == b.position) b else b.copy(position = snapped)
    }

    private fun tickInternal(
        b: CatBehavior,
        now: Instant,
        dtSeconds: Float,
        mood: Mood,
        bounds: HabitatBounds,
        anchors: Anchors,
        rng: Random,
        speedDpPerSec: Float,
        world: HabitatWorld,
    ): CatBehavior {
        if (dtSeconds <= 0f) return b

        // Duration-bounded states stay put until stateUntil is reached, then
        // transition back to Idle and reschedule the wander timer. Side effects
        // (refill hunger, drop the toy) are handled by the ViewModel observing
        // the state transition.
        if (b.state == CatState.Eating || b.state == CatState.Playing) {
            val until = b.stateUntil
            if (until == null || now < until) return b
            return b.copy(
                state = CatState.Idle,
                stateUntil = null,
                nextWanderAt = nextWanderInstant(now, rng),
            )
        }

        // Cache the bowl anchor once per tick. Used by both the target-priority
        // dispatch below and the arrival equality check further down so the
        // "did we arrive at the bowl?" comparison reads the same value as the
        // target we picked, making the equality semantics explicit.
        val bAnchor = bowlAnchor(anchors, bounds, world)

        // Target priority: SLEEPY bed > thrown toy > HUNGRY+filled bowl > nothing.
        // SLEEPY beats toy because going to sleep is a stronger drive than play;
        // toy beats a hungry cat so a thrown toy redirects mid-walk to the bowl.
        // The toy's render position can land in `playAreaRect` outside the cat's
        // walkable `bounds`; targeting it raw would walk the cat off-screen and
        // oscillate (snapIntoBounds clamps the cat but not the target). Clamp the
        // toy into bounds via `playAnchorFor` so the cat stops at the nearest
        // in-bounds position while `world.toy` (the rendered toy) stays put.
        val playAnchor: Position? = world.toy?.let { playAnchorFor(it, bounds) }
        val targetOverride: Position? =
            when {
                mood == Mood.SLEEPY -> anchors.bed
                playAnchor != null -> playAnchor
                mood == Mood.HUNGRY && world.bowlFilled -> bAnchor
                else -> null
            }

        // Lying cat with no reason to move stays put.
        if (b.state == CatState.Lying) {
            if (targetOverride != null && targetOverride == b.position) return b
            if (mood == Mood.SLEEPY) return b
            // Wake up and walk somewhere.
            val target = targetOverride ?: pickTarget(mood, bounds, anchors, rng, world)
            return walkingToward(b, target)
        }

        // Idle cat: start moving if mood demands it, or the wander timer fired.
        if (b.state == CatState.Idle) {
            return when {
                targetOverride != null && targetOverride != b.position -> walkingToward(b, targetOverride)
                now >= b.nextWanderAt ->
                    walkingToward(
                        b,
                        pickTarget(mood, bounds, anchors, rng, world),
                    )
                else -> b
            }
        }

        // Walking cat. Possibly retarget to the override; then advance.
        val effectiveTarget = targetOverride ?: b.target
        val advanced = advance(b.position, effectiveTarget, speedDpPerSec, dtSeconds)
        val arrived = isArrived(advanced, effectiveTarget)
        return when {
            !arrived ->
                b.copy(
                    position = advanced,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            effectiveTarget == bAnchor && world.bowlFilled ->
                b.copy(
                    state = CatState.Eating,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    stateUntil =
                        Instant.fromEpochMilliseconds(
                            now.toEpochMilliseconds() + EATING_DURATION_SECONDS * 1000L,
                        ),
                )
            playAnchor != null && effectiveTarget == playAnchor ->
                b.copy(
                    state = CatState.Playing,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    stateUntil =
                        Instant.fromEpochMilliseconds(
                            now.toEpochMilliseconds() + PLAYING_DURATION_SECONDS * 1000L,
                        ),
                )
            effectiveTarget == anchors.bed ->
                b.copy(
                    state = CatState.Lying,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                )
            else ->
                b.copy(
                    state = CatState.Idle,
                    position = effectiveTarget,
                    target = effectiveTarget,
                    facing = directionOf(b.position, effectiveTarget),
                    nextWanderAt = nextWanderInstant(now, rng),
                )
        }
    }

    private fun walkingToward(
        b: CatBehavior,
        target: Position,
    ) = b.copy(
        state = CatState.Walking,
        target = target,
        facing = directionOf(b.position, target),
    )

    private fun advance(
        from: Position,
        to: Position,
        speed: Float,
        dt: Float,
    ): Position {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= ARRIVAL_EPSILON_DP) return to
        val maxStep = speed * dt
        if (maxStep >= dist) return to
        val ratio = maxStep / dist
        return Position(from.x + dx * ratio, from.y + dy * ratio)
    }

    private fun isArrived(
        at: Position,
        target: Position,
    ): Boolean {
        val dx = target.x - at.x
        val dy = target.y - at.y
        return sqrt(dx * dx + dy * dy) <= ARRIVAL_EPSILON_DP
    }
}

private fun Random.nextFloatInRange(
    min: Float,
    max: Float,
): Float = min + nextFloat() * (max - min)

/**
 * Cat destination derived from a toy's raw drop position. Mirrors
 * [com.pocketpets.app.ui.pet.bowlAnchorFor]: the toy's render position is left
 * alone, but the cat's target is clamped into the cat-walkable [bounds] so a
 * toy dropped past the edge of the play area still has a reachable destination
 * inside the habitat. Without this, [CatBehaviorRules.tick]'s `snapIntoBounds`
 * would pull the cat back inside bounds while leaving the target outside,
 * producing endless walk/snap oscillation and a never-arrived Playing state.
 */
fun playAnchorFor(
    toyPosition: Position,
    bounds: HabitatBounds,
): Position = bounds.clamp(toyPosition)
