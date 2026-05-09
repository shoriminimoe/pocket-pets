package com.pocketpets.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.domain.Species
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PetDao

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.petDao()
    }

    @After fun teardown() {
        db.close()
    }

    private fun newPet(
        name: String = "Whiskers",
        active: Boolean = false,
    ) = PetEntity(
        name = name,
        species = Species.CAT,
        bornAt = Instant.parse("2026-01-01T00:00:00Z"),
        hunger = 100f,
        cleanliness = 100f,
        happiness = 100f,
        energy = 100f,
        lastTickAt = Instant.parse("2026-01-01T00:00:00Z"),
        isActive = active,
        poopCount = 0,
        lastFedAt = null,
    )

    @Test fun `insert and observeAll`() =
        runTest {
            val id = dao.insert(newPet())
            assertThat(id).isGreaterThan(0L)
            val all = dao.observeAll().first()
            assertThat(all).hasSize(1)
            assertThat(all[0].name).isEqualTo("Whiskers")
        }

    @Test fun `setActiveExclusive flips one to active and others to inactive`() =
        runTest {
            val a = dao.insert(newPet("A", active = true))
            val b = dao.insert(newPet("B", active = false))
            dao.setActiveExclusive(b)
            val petA = dao.getById(a)!!
            val petB = dao.getById(b)!!
            assertThat(petA.isActive).isFalse()
            assertThat(petB.isActive).isTrue()
            assertThat(dao.observeActive().first()?.id).isEqualTo(b)
        }

    @Test fun `update persists stat changes`() =
        runTest {
            val id = dao.insert(newPet())
            val pet = dao.getById(id)!!.copy(hunger = 42f)
            dao.update(pet)
            assertThat(dao.getById(id)!!.hunger).isWithin(0.01f).of(42f)
        }
}
