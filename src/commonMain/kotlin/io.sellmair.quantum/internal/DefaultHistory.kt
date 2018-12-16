package io.sellmair.quantum.internal

import io.sellmair.quantum.History

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal data class DefaultHistory<T>
internal constructor(
    override val setting: History.Setting,
    private val elements: Iterable<T> = emptyList()) : History<T> {


    /*
    ################################################################################################
    API
    ################################################################################################
    */

    override fun next(state: T): History<T> {
        val appended = elements + state
        val limit = setting.limit
        val newElements = when (limit) {
            is History.Setting.Limit.None -> appended
            is History.Setting.Limit.Count -> appended.takeLast(limit.value)
        }

        return copy(elements = newElements)
    }

    override fun iterator(): Iterator<T> {
        return elements.toList().iterator()
    }
}