package com.pocketpets.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun ticker(periodMs: Long): Flow<Long> = flow {
    var i = 0L
    while (true) {
        emit(i++)
        delay(periodMs)
    }
}
