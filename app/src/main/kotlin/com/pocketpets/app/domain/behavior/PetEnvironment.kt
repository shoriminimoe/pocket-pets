package com.pocketpets.app.domain.behavior

import com.pocketpets.app.ui.sprite.Direction

/**
 * Persistent per-pet snapshot of the cat's place in its habitat. Captured at
 * pet-switch time and on world-changing events so re-activating a pet restores
 * "where it was and what it was doing" rather than starting fresh.
 *
 * Transient timers ([CatBehavior.target], `nextWanderAt`, `stateUntil`) are
 * intentionally omitted — they get recomputed from `catState` by the frame
 * ticker after restore. Eating/Playing collapse to Idle on save since they're
 * brief states whose timers don't survive the round trip cleanly.
 */
data class PetEnvironment(
    val catPosition: Position,
    val catFacing: Direction,
    val catState: CatState,
    val bowlPosition: Position?,
    val bowlFilled: Boolean,
    val toyPosition: Position?,
)
