package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
################################################################################################
PUBLIC API
################################################################################################
*/

@QuantumDsl
operator fun <T> Owner<T>.invoke(
    context: CoroutineContext = EmptyCoroutineContext,
    action: suspend State<T>.() -> Unit) = this.launch(context) {
    action(state)
}