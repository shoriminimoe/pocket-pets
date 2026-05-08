package com.pocketpets.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "care_events")
data class CareEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val kind: String, // "feed" | "clean" | "pet" | "talk" | "auto_tick"
    val at: Instant,
)
