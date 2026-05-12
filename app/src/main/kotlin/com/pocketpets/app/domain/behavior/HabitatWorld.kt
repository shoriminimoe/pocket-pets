package com.pocketpets.app.domain.behavior

/**
 * World state read by [CatBehaviorRules.tick] and written by user actions
 * (drop food on bowl, drop toy on floor). Pure data; mutated only by `copy`.
 *
 * `bowlPosition` is the bowl's top-left in dp. `null` means "use the default
 * placement derived from the current habitat layout" — the ViewModel
 * materialises it once the screen has measured itself.
 */
data class HabitatWorld(
    val bowlFilled: Boolean = false,
    val toy: Position? = null,
    val bowlPosition: Position? = null,
)
