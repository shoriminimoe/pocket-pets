package com.pocketpets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PetEnvironmentDao {
    @Query("SELECT * FROM pet_environment WHERE petId = :petId LIMIT 1")
    suspend fun getByPetId(petId: Long): PetEnvironmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(env: PetEnvironmentEntity)
}
