package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl
import io.sellmair.quantum.internal.invoke
import kotlinx.coroutines.sync.Mutex

/*
################################################################################################
PUBLIC API
################################################################################################
*/

@QuantumDsl
class State<T> internal constructor(
    @PublishedApi internal val access: Access<T>,
    @PublishedApi internal val onState: suspend (state: T) -> Unit,
    @PublishedApi internal val mutex: Mutex) {

    /*
    ################################################################################################
    API
    ################################################################################################
    */

    @QuantumDsl
    suspend inline fun set(reducer: T.() -> T) = mutex {
        val value = reducer(access.state)
        onState(value)
    }

    @QuantumDsl
    @PublishedApi
    internal suspend inline fun setWithAccess(reducer: Access<T>.() -> T) = mutex {
        val value = access.reducer()
        onState(value)
    }


    internal val value: T get() = access.state
}