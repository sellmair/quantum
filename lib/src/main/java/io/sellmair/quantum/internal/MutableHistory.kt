package io.sellmair.quantum.internal

import io.sellmair.quantum.History

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal interface MutableHistory<T> : History<T> {
    fun add(state: T)
}