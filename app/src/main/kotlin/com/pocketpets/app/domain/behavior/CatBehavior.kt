package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant

/**
 * Snapshot of the cat's behaviour state. Mutations go through
 * [CatBehaviorRules.tick] only.
 *
 * [stateUntil] is non-null only for duration-bounded states (Eating, Playing);
 * `tick` returns a state-exit transition when `now >= stateUntil`.
 */
data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
    val stateUntil: Instant? = null,
)
