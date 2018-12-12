package io.sellmair.quantum.internal

import io.sellmair.quantum.History

internal class NoHistory<T> : History<T> {
    override fun next(state: T): History<T> {
        return this
    }

    override fun iterator(): Iterator<T> {
        return emptyList<T>().iterator()
    }
}