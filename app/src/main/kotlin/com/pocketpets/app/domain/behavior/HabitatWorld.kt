package com.pocketpets.app.domain.behavior

/**
 * World state read by [CatBehaviorRules.tick] and written by user actions
 * (drop food on bowl, drop toy on floor). Pure data; mutated only by `copy`.
 */
data class HabitatWorld(
    val bowlFilled: Boolean = false,
    val toy: Position? = null,
)
