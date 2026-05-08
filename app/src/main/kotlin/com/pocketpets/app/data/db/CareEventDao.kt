package com.pocketpets.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CareEventDao {
    @Insert
    suspend fun insert(event: CareEventEntity)

    @Query("DELETE FROM care_events WHERE petId = :petId AND id NOT IN " +
           "(SELECT id FROM care_events WHERE petId = :petId ORDER BY id DESC LIMIT 100)")
    suspend fun pruneToLast100(petId: Long)
}
