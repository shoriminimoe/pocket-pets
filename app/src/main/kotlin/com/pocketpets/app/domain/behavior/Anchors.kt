package com.pocketpets.app.domain.behavior

/** Fixed habitat positions the cat can navigate to deliberately. */
data class Anchors(
    val bed: Position,
    val bowl: Position,
)
