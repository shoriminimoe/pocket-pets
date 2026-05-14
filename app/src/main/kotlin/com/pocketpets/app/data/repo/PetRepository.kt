package com.pocketpets.app.data.repo

import com.pocketpets.app.data.db.CareEventDao
import com.pocketpets.app.data.db.CareEventEntity
import com.pocketpets.app.data.db.PetDao
import com.pocketpets.app.data.db.PetEntity
import com.pocketpets.app.data.db.PetEnvironmentDao
import com.pocketpets.app.data.db.PetEnvironmentEntity
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.Species
import com.pocketpets.app.domain.StatDecay
import com.pocketpets.app.domain.behavior.CatState
import com.pocketpets.app.domain.behavior.PetEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

interface PetRepo {
    fun observeActive(): Flow<Pet?>

    fun observeAll(): Flow<List<Pet>>

    suspend fun getById(id: Long): Pet?

    suspend fun adopt(
        name: String,
        species: Species,
    ): Long

    suspend fun setActive(id: Long)

    suspend fun feed(id: Long)

    suspend fun clean(id: Long)

    suspend fun pet(id: Long)

    suspend fun talk(id: Long)

    suspend fun groom(id: Long)

    suspend fun runDecayTick(id: Long)

    /**
     * Latest persisted habitat snapshot for [id], or `null` if none has been
     * saved yet. Mirrors what [saveEnvironment] wrote, with transient
     * [CatState.Eating]/[CatState.Playing] never appearing because the writer
     * collapses them to [CatState.Idle].
     */
    suspend fun getEnvironment(id: Long): PetEnvironment?

    /**
     * Upserts the per-pet habitat snapshot for [id]. [CatState.Eating] and
     * [CatState.Playing] are duration-bounded by a `stateUntil` timer that
     * isn't part of the snapshot, so the writer collapses them to
     * [CatState.Idle] before storage — that's the only sanitisation; all other
     * fields are stored as-is.
     */
    suspend fun saveEnvironment(
        id: Long,
        env: PetEnvironment,
    )
}

class PetRepository(
    private val petDao: PetDao,
    private val careDao: CareEventDao,
    private val envDao: PetEnvironmentDao,
    private val clock: Clock,
) : PetRepo {
    override fun observeAll(): Flow<List<Pet>> = petDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<Pet?> = petDao.observeActive().map { it?.toDomain() }

    override suspend fun getById(id: Long): Pet? = petDao.getById(id)?.toDomain()

    override suspend fun adopt(
        name: String,
        species: Species,
    ): Long {
        val now = clock.now()
        val id =
            petDao.insert(
                PetEntity(
                    name = name.trim(),
                    species = species,
                    bornAt = now,
                    hunger = 100f,
                    cleanliness = 100f,
                    happiness = 100f,
                    energy = 100f,
                    lastTickAt = now,
                    isActive = false,
                    poopCount = 0,
                    lastFedAt = null,
                ),
            )
        petDao.setActiveExclusive(id)
        return id
    }

    override suspend fun setActive(id: Long) = petDao.setActiveExclusive(id)

    override suspend fun feed(id: Long) {
        mutate(id, "feed") { ticked ->
            val newStats =
                ticked.stats.copy(
                    hunger = (ticked.stats.hunger + 40f).coerceAtMost(100f),
                    happiness = (ticked.stats.happiness + 5f).coerceAtMost(100f),
                )
            ticked.copy(stats = newStats, lastFedAt = clock.now())
        }
    }

    override suspend fun clean(id: Long) {
        mutate(id, "clean") { ticked ->
            if (ticked.poopCount > 0) {
                ticked.copy(poopCount = ticked.poopCount - 1)
            } else {
                val newStats =
                    ticked.stats.copy(
                        cleanliness = (ticked.stats.cleanliness + 10f).coerceAtMost(100f),
                    )
                ticked.copy(stats = newStats)
            }
        }
    }

    private val petTimestamps = mutableMapOf<Long, ArrayDeque<Long>>()
    private val petWindowMs = 10L * 60 * 1000
    private val petMaxInWindow = 5

    override suspend fun pet(id: Long) {
        val now = clock.now().toEpochMilliseconds()
        val window = petTimestamps.getOrPut(id) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() > petWindowMs) window.removeFirst()
        if (window.size >= petMaxInWindow) return
        window.addLast(now)
        mutate(id, "pet") { ticked ->
            val newStats =
                ticked.stats.copy(
                    happiness = (ticked.stats.happiness + 5f).coerceAtMost(100f),
                )
            ticked.copy(stats = newStats)
        }
    }

    override suspend fun talk(id: Long) {
        mutate(id, "talk") { ticked ->
            val newStats =
                ticked.stats.copy(
                    happiness = (ticked.stats.happiness + 2f).coerceAtMost(100f),
                )
            ticked.copy(stats = newStats)
        }
    }

    private val groomTimestamps = mutableMapOf<Long, ArrayDeque<Long>>()
    private val groomWindowMs = 10L * 60 * 1000
    private val groomMaxInWindow = 3

    override suspend fun groom(id: Long) {
        val now = clock.now().toEpochMilliseconds()
        val window = groomTimestamps.getOrPut(id) { ArrayDeque() }
        while (window.isNotEmpty() && now - window.first() > groomWindowMs) window.removeFirst()
        if (window.size >= groomMaxInWindow) return
        window.addLast(now)
        mutate(id, "groom") { ticked ->
            val newStats =
                ticked.stats.copy(
                    cleanliness = (ticked.stats.cleanliness + 25f).coerceAtMost(100f),
                    happiness = (ticked.stats.happiness + 2f).coerceAtMost(100f),
                )
            ticked.copy(stats = newStats)
        }
    }

    override suspend fun runDecayTick(id: Long) {
        mutate(id, "auto_tick") { it }
    }

    override suspend fun getEnvironment(id: Long): PetEnvironment? = envDao.getByPetId(id)?.toDomain()

    override suspend fun saveEnvironment(
        id: Long,
        env: PetEnvironment,
    ) {
        val sanitised =
            if (env.catState == CatState.Eating || env.catState == CatState.Playing) {
                env.copy(catState = CatState.Idle)
            } else {
                env
            }
        envDao.upsert(PetEnvironmentEntity.fromDomain(id, sanitised))
    }

    private suspend fun mutate(
        id: Long,
        kind: String,
        transform: (Pet) -> Pet,
    ) {
        val raw = petDao.getById(id) ?: return
        val ticked = StatDecay.tick(raw.toDomain(), clock.now())
        val mutated = transform(ticked)
        petDao.update(PetEntity.fromDomain(mutated))
        careDao.insert(CareEventEntity(petId = id, kind = kind, at = clock.now()))
        careDao.pruneToLast100(id)
    }
}
