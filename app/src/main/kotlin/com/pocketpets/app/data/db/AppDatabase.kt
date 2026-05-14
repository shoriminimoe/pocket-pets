package com.pocketpets.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PetEntity::class, CareEventEntity::class, PetEnvironmentEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao

    abstract fun careEventDao(): CareEventDao

    abstract fun petEnvironmentDao(): PetEnvironmentDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pet_environment (
                            petId INTEGER PRIMARY KEY NOT NULL,
                            catX REAL NOT NULL,
                            catY REAL NOT NULL,
                            catFacing TEXT NOT NULL,
                            catStateName TEXT NOT NULL,
                            bowlX REAL,
                            bowlY REAL,
                            bowlFilled INTEGER NOT NULL,
                            toyX REAL,
                            toyY REAL,
                            FOREIGN KEY(petId) REFERENCES pets(id) ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
