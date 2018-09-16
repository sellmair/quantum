package io.sellmair.quantum.internal

import io.sellmair.quantum.Joinable
import java.util.concurrent.TimeUnit

/*
################################################################################################
PUBLISHED INTERNAL API
################################################################################################
*/

fun Thread.asJoinable(): Joinable = object : Joinable {
    val thread = this@asJoinable

    override fun join() {
        thread.join()
    }

    override fun join(timeout: Long, unit: TimeUnit): Boolean {
        thread.join(unit.toMillis(timeout))
        return !thread.isAlive
    }
}
