package com.pocketpets.app.domain

import kotlinx.datetime.Instant

data class Pet(
    val id: Long,
    val name: String,
    val species: Species,
    val bornAt: Instant,
    val stats: PetStats,
    val lastTickAt: Instant,
    val isActive: Boolean,
    val poopCount: Int,
    val lastFedAt: Instant?,
) {
    init {
        require(poopCount in 0..MAX_POOPS) { "poopCount out of range: $poopCount" }
    }

    companion object {
        const val MAX_POOPS = 4
    }
}
