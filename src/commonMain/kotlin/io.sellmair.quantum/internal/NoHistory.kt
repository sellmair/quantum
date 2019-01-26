package io.sellmair.quantum.internal

import io.sellmair.quantum.History

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class NoHistory<T> : History<T> {

    override val setting: History.Setting = History.Setting(
        limit = History.Setting.Limit.Count(0))

    override fun next(state: T): History<T> {
        return this
    }

    override fun iterator(): Iterator<T> {
        return emptyList<T>().iterator()
    }
}