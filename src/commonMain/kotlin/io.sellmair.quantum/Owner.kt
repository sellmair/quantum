package io.sellmair.quantum

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface Owner<T> : CoroutineScope {

    val states: ReceiveChannel<T>

    fun enter(
        context: CoroutineContext = EmptyCoroutineContext,
        action: suspend State<T>.() -> Unit): Job


    suspend fun quit()

    companion object Factory
}

/*
suspend inline fun <T> Owner<T>.set(reducer: T.() -> T) {

}
*/
