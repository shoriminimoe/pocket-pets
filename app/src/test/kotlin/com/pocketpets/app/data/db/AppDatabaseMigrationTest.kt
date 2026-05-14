package com.pocketpets.app.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun openV1InMemory() =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(ctx)
                .name(null) // in-memory
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            // Mirror Room's v1 schema for the two existing tables. Room generates
                            // these DDLs from the entity definitions; we restate them here so the
                            // migration runs against a representative v1 database.
                            db.execSQL(
                                "CREATE TABLE pets (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "name TEXT NOT NULL, " +
                                    "species TEXT NOT NULL, " +
                                    "bornAt INTEGER NOT NULL, " +
                                    "hunger REAL NOT NULL, " +
                                    "cleanliness REAL NOT NULL, " +
                                    "happiness REAL NOT NULL, " +
                                    "energy REAL NOT NULL, " +
                                    "lastTickAt INTEGER NOT NULL, " +
                                    "isActive INTEGER NOT NULL, " +
                                    "poopCount INTEGER NOT NULL, " +
                                    "lastFedAt INTEGER)",
                            )
                            db.execSQL(
                                "CREATE TABLE care_events (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "petId INTEGER NOT NULL, " +
                                    "kind TEXT NOT NULL, " +
                                    "at INTEGER NOT NULL)",
                            )
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build(),
        )

    @Test fun `MIGRATION_1_2 creates pet_environment with expected columns`() {
        val helper = openV1InMemory()
        val db = helper.writableDatabase
        AppDatabase.MIGRATION_1_2.migrate(db)

        // Table exists.
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pet_environment'").use {
            assertThat(it.moveToFirst()).isTrue()
        }

        // Expected columns are present, with the correct nullability and primary key.
        data class Column(
            val name: String,
            val notNull: Boolean,
            val pk: Boolean,
        )
        val columns = mutableListOf<Column>()
        db.query("PRAGMA table_info('pet_environment')").use {
            while (it.moveToNext()) {
                columns +=
                    Column(
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        notNull = it.getInt(it.getColumnIndexOrThrow("notnull")) == 1,
                        pk = it.getInt(it.getColumnIndexOrThrow("pk")) > 0,
                    )
            }
        }
        assertThat(columns.map { it.name }).containsExactly(
            "petId",
            "catX",
            "catY",
            "catFacing",
            "catStateName",
            "bowlX",
            "bowlY",
            "bowlFilled",
            "toyX",
            "toyY",
        )
        val byName = columns.associateBy { it.name }
        assertThat(byName["petId"]!!.pk).isTrue()
        assertThat(byName["catX"]!!.notNull).isTrue()
        assertThat(byName["catFacing"]!!.notNull).isTrue()
        assertThat(byName["bowlFilled"]!!.notNull).isTrue()
        assertThat(byName["bowlX"]!!.notNull).isFalse()
        assertThat(byName["toyY"]!!.notNull).isFalse()

        helper.close()
    }

    @Test fun `MIGRATION_1_2 preserves existing pets row`() {
        val helper = openV1InMemory()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO pets (name, species, bornAt, hunger, cleanliness, happiness, energy, " +
                "lastTickAt, isActive, poopCount, lastFedAt) " +
                "VALUES ('Whiskers', 'CAT', 0, 100.0, 100.0, 100.0, 100.0, 0, 1, 0, NULL)",
        )

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT name FROM pets WHERE name='Whiskers'").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("Whiskers")
        }
        helper.close()
    }

    @Test fun `pet_environment row insert and select round-trips after migration`() {
        val helper = openV1InMemory()
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO pets (name, species, bornAt, hunger, cleanliness, happiness, energy, " +
                "lastTickAt, isActive, poopCount, lastFedAt) " +
                "VALUES ('Whiskers', 'CAT', 0, 100.0, 100.0, 100.0, 100.0, 0, 1, 0, NULL)",
        )
        AppDatabase.MIGRATION_1_2.migrate(db)

        val petId =
            db.query("SELECT id FROM pets WHERE name='Whiskers'").use {
                it.moveToFirst()
                it.getLong(0)
            }
        db.execSQL(
            "INSERT INTO pet_environment (petId, catX, catY, catFacing, catStateName, " +
                "bowlX, bowlY, bowlFilled, toyX, toyY) " +
                "VALUES ($petId, 10.0, 20.0, 'SOUTH', 'Idle', NULL, NULL, 0, NULL, NULL)",
        )
        db.query("SELECT catX, catY, catFacing FROM pet_environment WHERE petId=$petId").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getFloat(0)).isEqualTo(10f)
            assertThat(it.getFloat(1)).isEqualTo(20f)
            assertThat(it.getString(2)).isEqualTo("SOUTH")
        }

        helper.close()
    }
}
