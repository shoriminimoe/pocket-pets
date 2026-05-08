package com.pocketpets.app.domain

data class PetStats(
    val hunger: Float,
    val cleanliness: Float,
    val happiness: Float,
    val energy: Float,
) {
    init {
        require(hunger in 0f..100f) { "hunger out of range: $hunger" }
        require(cleanliness in 0f..100f) { "cleanliness out of range: $cleanliness" }
        require(happiness in 0f..100f) { "happiness out of range: $happiness" }
        require(energy in 0f..100f) { "energy out of range: $energy" }
    }

    companion object {
        val FULL = PetStats(100f, 100f, 100f, 100f)
    }
}
