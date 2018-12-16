package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl
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


@QuantumDsl
suspend inline fun <T> Owner<T>.set(reducer: Access<T>.() -> T) {
    state.setWithAccess(reducer)
}

