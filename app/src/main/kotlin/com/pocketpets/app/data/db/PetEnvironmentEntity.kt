package com.pocketpets.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.PetEnvironment
import com.pocketpets.app.domain.behavior.Position
import com.pocketpets.app.ui.sprite.Direction

@Entity(
    tableName = "pet_environment",
    foreignKeys = [
        ForeignKey(
            entity = PetEntity::class,
            parentColumns = ["id"],
            childColumns = ["petId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PetEnvironmentEntity(
    @PrimaryKey val petId: Long,
    val catX: Float,
    val catY: Float,
    val catFacing: String,
    val catStateName: String,
    val bowlX: Float?,
    val bowlY: Float?,
    val bowlFilled: Boolean,
    val toyX: Float?,
    val toyY: Float?,
) {
    fun toDomain(): PetEnvironment {
        val rawState =
            runCatching { CatState.valueOf(catStateName) }.getOrDefault(CatState.Idle)
        // Eating/Playing are duration-bounded transient states. On reload their
        // stateUntil timer is lost, so collapse to Idle to avoid the cat being
        // stuck in a state with no exit condition.
        val safeState =
            if (rawState == CatState.Eating || rawState == CatState.Playing) {
                CatState.Idle
            } else {
                rawState
            }
        return PetEnvironment(
            catPosition = Position(catX, catY),
            catFacing = runCatching { Direction.valueOf(catFacing) }.getOrDefault(Direction.SOUTH),
            catState = safeState,
            bowlPosition = if (bowlX != null && bowlY != null) Position(bowlX, bowlY) else null,
            bowlFilled = bowlFilled,
            toyPosition = if (toyX != null && toyY != null) Position(toyX, toyY) else null,
        )
    }

    companion object {
        fun fromDomain(
            petId: Long,
            env: PetEnvironment,
        ): PetEnvironmentEntity =
            PetEnvironmentEntity(
                petId = petId,
                catX = env.catPosition.x,
                catY = env.catPosition.y,
                catFacing = env.catFacing.name,
                catStateName = env.catState.name,
                bowlX = env.bowlPosition?.x,
                bowlY = env.bowlPosition?.y,
                bowlFilled = env.bowlFilled,
                toyX = env.toyPosition?.x,
                toyY = env.toyPosition?.y,
            )
    }
}
