package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl
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
    action: suspend State<T>.() -> Unit) = this.enter(context, action)