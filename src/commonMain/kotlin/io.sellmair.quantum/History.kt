package io.sellmair.quantum

import io.sellmair.quantum.internal.DefaultHistory
import io.sellmair.quantum.internal.NoHistory

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface History<T> : Iterable<T> {

    /*
    ################################################################################################
    CONFIGURATION
    ################################################################################################
    */

    data class Setting(
        val limit: Limit) {
        sealed class Limit {

            object None : Limit()

            class Count(internal val value: Int) : Limit() {
                init {
                    value >= 0
                }
            }
        }
    }

    fun next(state: T): History<T>

    companion object Factory
}


/*
################################################################################################
PUBLIC API: Factories
################################################################################################
*/

fun <T> History.Factory.none(): History<T> {
    return NoHistory()
}


operator fun <T> History.Factory.invoke(
    setting: History.Setting = History.Setting(
        limit = History.Setting.Limit.None)): History<T> {
    return DefaultHistory(setting = setting)
}