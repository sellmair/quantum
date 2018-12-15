package io.sellmair.quantum

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
################################################################################################
PUBLIC API
################################################################################################
*/

suspend fun <T> Owner<T>.set(
    context: CoroutineContext = EmptyCoroutineContext,
    reducer: Access<T>.() -> T) = this.suspendEnter(context) {
    this@suspendEnter.set(reducer)
}