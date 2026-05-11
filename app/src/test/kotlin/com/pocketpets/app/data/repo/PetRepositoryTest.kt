package com.pocketpets.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.domain.Species
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: PetRepository
    private val clock = FakeClock(Instant.parse("2026-01-01T12:00:00Z"))

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = PetRepository(db.petDao(), db.careEventDao(), clock)
    }

    @After fun teardown() {
        db.close()
    }

    @Test fun `adopt creates a full-stat active pet`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val pet = repo.observeActive().first()!!
            assertThat(pet.id).isEqualTo(id)
            assertThat(pet.name).isEqualTo("Whiskers")
            assertThat(pet.stats.hunger).isEqualTo(100f)
            assertThat(pet.isActive).isTrue()
        }

    @Test fun `feed bumps hunger and sets lastFedAt`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            clock.advanceBy(2L * 3_600_000)
            repo.feed(id)
            val pet = repo.getById(id)!!
            // After 2h decay (100 - 16 = 84), feed adds 40 → capped at 100
            assertThat(pet.stats.hunger).isEqualTo(100f)
            assertThat(pet.lastFedAt).isEqualTo(clock.now())
        }

    @Test fun `clean removes a poop when present`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            val withPoop = dao.getById(id)!!.copy(poopCount = 1)
            dao.update(withPoop)
            repo.clean(id)
            assertThat(repo.getById(id)!!.poopCount).isEqualTo(0)
        }

    @Test fun `clean with no poops increases cleanliness by 10`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(cleanliness = 50f))
            repo.clean(id)
            assertThat(repo.getById(id)!!.stats.cleanliness).isWithin(0.01f).of(60f)
        }

    @Test fun `pet bumps happiness up to cap, then no-op`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(happiness = 50f))
            repeat(5) { repo.pet(id) }
            val after5 = repo.getById(id)!!.stats.happiness
            repo.pet(id)
            val after6 = repo.getById(id)!!.stats.happiness
            assertThat(after6).isEqualTo(after5)
        }

    @Test fun `groom raises cleanliness by 25 and happiness by 2`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(cleanliness = 40f, happiness = 40f))
            repo.groom(id)
            val after = repo.getById(id)!!.stats
            assertThat(after.cleanliness).isWithin(0.01f).of(65f)
            assertThat(after.happiness).isWithin(0.01f).of(42f)
        }

    @Test fun `groom clamps cleanliness at 100`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(cleanliness = 90f))
            repo.groom(id)
            assertThat(repo.getById(id)!!.stats.cleanliness).isEqualTo(100f)
        }

    @Test fun `groom caps at 3 successful invocations per 10-minute window`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(cleanliness = 0f))
            repeat(3) { repo.groom(id) }
            val after3 = repo.getById(id)!!.stats.cleanliness
            repo.groom(id)
            val after4 = repo.getById(id)!!.stats.cleanliness
            assertThat(after4).isEqualTo(after3)
        }

    @Test fun `groom window slides so it allows new grooms after 10 minutes`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            val dao = db.petDao()
            dao.update(dao.getById(id)!!.copy(cleanliness = 0f))
            repeat(3) { repo.groom(id) }
            clock.advanceBy(11L * 60 * 1000)
            dao.update(dao.getById(id)!!.copy(cleanliness = 0f))
            repo.groom(id)
            assertThat(repo.getById(id)!!.stats.cleanliness).isWithin(0.01f).of(25f)
        }

    @Test fun `setActive flips active flag exclusively`() =
        runTest {
            val a = repo.adopt("A", Species.CAT)
            val b = repo.adopt("B", Species.CAT)
            assertThat(repo.observeActive().first()!!.id).isEqualTo(b)
            repo.setActive(a)
            assertThat(repo.observeActive().first()!!.id).isEqualTo(a)
        }
}
