package com.pocketpets.app.data.repo

import com.pocketpets.app.data.db.CareEventDao
import com.pocketpets.app.data.db.CareEventEntity
import com.pocketpets.app.data.db.PetDao
import com.pocketpets.app.data.db.PetEntity
import com.pocketpets.app.domain.Pet
import com.pocketpets.app.domain.Species
import com.pocketpets.app.domain.StatDecay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

interface PetRepo {
    fun observeActive(): Flow<Pet?>
    fun observeAll(): Flow<List<Pet>>
    suspend fun getById(id: Long): Pet?
    suspend fun adopt(name: String, species: Species): Long
    suspend fun setActive(id: Long)
    suspend fun feed(id: Long)
    suspend fun clean(id: Long)
    suspend fun pet(id: Long)
    suspend fun talk(id: Long)
    suspend fun runDecayTick(id: Long)
}

class PetRepository(
    private val petDao: PetDao,
    private val careDao: CareEventDao,
    private val clock: Clock,
) : PetRepo {

    override fun observeAll(): Flow<List<Pet>> = petDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<Pet?> = petDao.observeActive().map { it?.toDomain() }

    override suspend fun getById(id: Long): Pet? = petDao.getById(id)?.toDomain()

    override suspend fun adopt(name: String, species: Species): Long {
        val now = clock.now()
        val id = petDao.insert(
            PetEntity(
                name = name.trim(),
                species = species,
                bornAt = now,
                hunger = 100f, cleanliness = 100f,
                happiness = 100f, energy = 100f,
                lastTickAt = now,
                isActive = false,
                poopCount = 0,
                lastFedAt = null,
            )
        )
        petDao.setActiveExclusive(id)
        return id
    }

    override suspend fun setActive(id: Long) = petDao.setActiveExclusive(id)

    override suspend fun feed(id: Long) {
        mutate(id, "feed") { ticked ->
            val newStats = ticked.stats.copy(
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
                val newStats = ticked.stats.copy(
                    cleanliness = (ticked.stats.cleanliness + 10f).coerceAtMost(100f)
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
            val newStats = ticked.stats.copy(
                happiness = (ticked.stats.happiness + 5f).coerceAtMost(100f)
            )
            ticked.copy(stats = newStats)
        }
    }

    override suspend fun talk(id: Long) {
        mutate(id, "talk") { ticked ->
            val newStats = ticked.stats.copy(
                happiness = (ticked.stats.happiness + 2f).coerceAtMost(100f)
            )
            ticked.copy(stats = newStats)
        }
    }

    override suspend fun runDecayTick(id: Long) {
        mutate(id, "auto_tick") { it }
    }

    private suspend fun mutate(id: Long, kind: String, transform: (Pet) -> Pet) {
        val raw = petDao.getById(id) ?: return
        val ticked = StatDecay.tick(raw.toDomain(), clock.now())
        val mutated = transform(ticked)
        petDao.update(PetEntity.fromDomain(mutated))
        careDao.insert(CareEventEntity(petId = id, kind = kind, at = clock.now()))
        careDao.pruneToLast100(id)
    }
}
