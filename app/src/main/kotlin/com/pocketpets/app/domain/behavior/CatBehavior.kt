package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction
import kotlinx.datetime.Instant

/**
 * Snapshot of the cat's behaviour state. Mutations go through
 * [CatBehaviorRules.tick] only.
 */
data class CatBehavior(
    val state: CatState,
    val position: Position,
    val target: Position,
    val facing: Direction,
    val nextWanderAt: Instant,
)
