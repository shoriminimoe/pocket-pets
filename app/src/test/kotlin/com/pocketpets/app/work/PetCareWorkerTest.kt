package com.pocketpets.app.work

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.pocketpets.app.data.db.AppDatabase
import com.pocketpets.app.data.repo.PetRepository
import com.pocketpets.app.data.settings.SettingsDataStore
import com.pocketpets.app.domain.Species
import com.pocketpets.app.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PetCareWorkerTest {
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
        repo = PetRepository(db.petDao(), db.careEventDao(), db.petEnvironmentDao(), clock)
    }

    @After fun teardown() {
        db.close()
    }

    @Test fun `worker decays all pets`() =
        runTest {
            val id = repo.adopt("Whiskers", Species.CAT)
            clock.advanceBy(4L * 3_600_000)
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val worker =
                TestListenableWorkerBuilder<PetCareWorker>(ctx)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: android.content.Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker =
                                PetCareWorker(
                                    appContext = appContext,
                                    params = workerParameters,
                                    repo = repo,
                                    settings = SettingsDataStore(appContext),
                                    notifications = NotificationHelper(appContext, SettingsDataStore(appContext), clock),
                                )
                        },
                    ).build()

            val result = worker.doWork()
            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            assertThat(repo.getById(id)!!.stats.hunger).isWithin(0.01f).of(68f)
        }
}
