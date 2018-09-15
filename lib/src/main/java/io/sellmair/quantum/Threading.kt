package io.sellmair.quantum

import java.util.concurrent.Executor

/*
################################################################################################
PUBLIC API
################################################################################################
*/

sealed class Threading {
    object Sync : Threading()
    object Pool : Threading()
    object Thread : Threading()
    data class Custom(val executor: Executor) : Threading()
    companion object
}