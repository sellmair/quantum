package io.sellmair.quantum.internal

import io.sellmair.quantum.Joinable

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun Thread.asJoinable(): Joinable = object : Joinable {
    override fun join() {
        this@asJoinable.join()
    }
}