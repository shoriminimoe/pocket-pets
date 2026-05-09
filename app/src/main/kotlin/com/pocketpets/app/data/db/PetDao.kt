package com.pocketpets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pets ORDER BY id ASC")
    fun observeAll(): Flow<List<PetEntity>>

    @Query("SELECT * FROM pets WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<PetEntity?>

    @Query("SELECT * FROM pets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PetEntity?

    @Query("SELECT * FROM pets")
    suspend fun getAll(): List<PetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pet: PetEntity): Long

    @Update
    suspend fun update(pet: PetEntity)

    @Query("UPDATE pets SET isActive = 0")
    suspend fun clearActiveFlag()

    @Query("UPDATE pets SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: Long)

    @Transaction
    suspend fun setActiveExclusive(id: Long) {
        clearActiveFlag()
        setActive(id)
    }
}
