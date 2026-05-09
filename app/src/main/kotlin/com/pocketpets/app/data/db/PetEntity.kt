package com.pocketpets.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.PetStats
import com.pocketpets.app.domain.Species
import kotlinx.datetime.Instant

@Entity(tableName = "pets")
data class PetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: Species,
    val bornAt: Instant,
    val hunger: Float,
    val cleanliness: Float,
    val happiness: Float,
    val energy: Float,
    val lastTickAt: Instant,
    val isActive: Boolean,
    val poopCount: Int,
    val lastFedAt: Instant?,
) {
    fun toDomain(): Pet = Pet(
        id = id, name = name, species = species, bornAt = bornAt,
        stats = PetStats(hunger, cleanliness, happiness, energy),
        lastTickAt = lastTickAt, isActive = isActive,
        poopCount = poopCount, lastFedAt = lastFedAt,
    )
    companion object {
        fun fromDomain(p: Pet) = PetEntity(
            id = p.id, name = p.name, species = p.species, bornAt = p.bornAt,
            hunger = p.stats.hunger, cleanliness = p.stats.cleanliness,
            happiness = p.stats.happiness, energy = p.stats.energy,
            lastTickAt = p.lastTickAt, isActive = p.isActive,
            poopCount = p.poopCount, lastFedAt = p.lastFedAt,
        )
    }
}
