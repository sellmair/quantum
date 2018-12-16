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
    initial: T,
    @PublishedApi internal val onState: suspend (state: T) -> Unit,
    @PublishedApi internal val mutex: Mutex) {

    /*
    ################################################################################################
    API
    ################################################################################################
    */

    @QuantumDsl
    suspend inline fun set(reducer: T.() -> T) = mutex {
        value = reducer(value)
        onState(value)
    }

    @QuantumDsl
    @PublishedApi
    internal suspend inline fun setWithAccess(reducer: Access<T>.() -> T) = mutex {
        value = access.reducer()
        onState(value)
    }


    /*
    ###########################################f#####################################################
    PRIVATE IMPLEMENTATION
    ################################################################################################
    */

    @PublishedApi
    internal var value: T = initial

    @PublishedApi
    internal val access: Access<T> = object : Access<T> {
        override val state: T get() = this@State.value
    }
}