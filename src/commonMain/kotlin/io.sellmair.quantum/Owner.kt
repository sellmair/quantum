package io.sellmair.quantum

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface Owner<T> : CoroutineScope {

    val states: ReceiveChannel<T>

    val state: State<T>

    suspend fun quit()

    companion object Factory
}




