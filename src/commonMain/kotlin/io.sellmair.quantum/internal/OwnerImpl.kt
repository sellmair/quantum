package io.sellmair.quantum.internal

import io.sellmair.quantum.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
################################################################################################
PUBLIC API
################################################################################################
*/

operator fun <T> Owner.Factory.invoke(
    initial: T,
    coroutineContext: CoroutineContext = EmptyCoroutineContext + Job(),
    mutex: Mutex = Mutex(),
    history: History<T> = History.none()): ChronologicalOwner<T> {
    return OwnerImpl(
        initial = initial,
        coroutineContext = coroutineContext,
        mutex = mutex,
        history = history)
}


/*
################################################################################################
INTERNAL API
################################################################################################
*/

@UseExperimental(ExperimentalCoroutinesApi::class)
internal class OwnerImpl<T> constructor(
    initial: T,
    override val coroutineContext: CoroutineContext,
    private val mutex: Mutex,
    history: History<T>) :
    ChronologicalOwner<T>, CoroutineScope {

    /*
    ################################################################################################
    API
    ################################################################################################
    */

    override suspend fun history(): History<T> = mutex { _history }

    override val states: ReceiveChannel<T> get() = broadcast.openSubscription()

    override val state by lazy {
        State(
            access = this.access,
            mutex = mutex,
            onState = { state -> onState(state) })
    }

    override suspend fun quit(): Unit = mutex {
        broadcast.cancel()
        coroutineContext.cancel()
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION
    ################################################################################################
    */


    private var _history = history.next(initial)

    private var broadcast: BroadcastChannel<T> = BroadcastChannel(Channel.CONFLATED)

    private val access = MutableAccess(initial)

    private suspend fun onState(state: T) {
        this.access.state = state
        this.setHistory(state)
        this.broadcast(state)
    }

    private fun setHistory(state: T) {
        this._history = _history.next(state)
    }

    private suspend fun broadcast(state: T) {
        broadcast.send(state)
    }

    /*
    ################################################################################################
    PRIVATE API
    ################################################################################################
    */

    private inner class MutableAccess<T>(initial: T) : Access<T> {
        override var state: T = initial
    }
}

