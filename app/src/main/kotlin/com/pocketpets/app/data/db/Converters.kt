package com.pocketpets.app.data.db

import androidx.room.TypeConverter
import com.pocketpets.app.domain.Species
import kotlinx.datetime.Instant

class Converters {
    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilliseconds()

    @TypeConverter fun longToInstant(l: Long?): Instant? = l?.let { Instant.fromEpochMilliseconds(it) }

    @TypeConverter fun speciesToString(s: Species): String = s.name

    @TypeConverter fun stringToSpecies(s: String): Species = Species.valueOf(s)
}
