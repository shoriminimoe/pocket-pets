package com.pocketpets.app.domain.behavior

/**
 * 2D point in habitat-relative dp. (0,0) is the top-left of the floor area;
 * positive x grows rightward, positive y downward (matching Compose).
 */
data class Position(
    val x: Float,
    val y: Float,
)
