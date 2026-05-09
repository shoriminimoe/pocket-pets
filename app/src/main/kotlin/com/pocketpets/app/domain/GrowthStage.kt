package com.pocketpets.app.domain

import kotlinx.datetime.Instant

enum class GrowthStage {
    BABY,
    JUVENILE,
    ADULT;

    companion object {
        private const val MS_PER_DAY = 24L * 60 * 60 * 1000

        fun fromAge(bornAt: Instant, now: Instant): GrowthStage {
            val days = (now.toEpochMilliseconds() - bornAt.toEpochMilliseconds()) / MS_PER_DAY
            return when {
                days < 3 -> BABY
                days < 7 -> JUVENILE
                else -> ADULT
            }
        }
    }
}
