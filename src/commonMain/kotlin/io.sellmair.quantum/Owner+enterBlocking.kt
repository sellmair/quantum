package io.sellmair.quantum

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <T> Owner<T>.enterBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    action: suspend State<T>.() -> Unit) =
    this.enter(context, action).join()
