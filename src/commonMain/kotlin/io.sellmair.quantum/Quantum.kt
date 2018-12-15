package io.sellmair.quantum

import io.sellmair.quantum.internal.invoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/*
################################################################################################
PUBLIC API
################################################################################################
*/


@UseExperimental(ExperimentalCoroutinesApi::class)
class Quantum<T> constructor(
    initial: T,
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
    private val mutex: Mutex = Mutex(),
    history: History<T> = History.none()) :
    ChronologicalOwner<T>, CoroutineScope {

    /*
    ################################################################################################
    API
    ################################################################################################
    */

    override fun enter(
        context: CoroutineContext,
        action: suspend State<T>.() -> Unit) = launch(context) {
        action(_state)
    }


    override suspend fun history(): History<T> = mutex { _history }

    override val states: ReceiveChannel<T> get() = broadcast.openSubscription()

    override suspend fun quit() = mutex {
        broadcast.close()
        coroutineContext.cancel()
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION
    ################################################################################################
    */

    private val _state = State(
        initial = initial,
        mutex = mutex,
        onState = { state -> onState(state) })

    private var _history = history.next(initial)

    private var broadcast: BroadcastChannel<T> = ConflatedBroadcastChannel(initial)

    private suspend fun onState(state: T) {
        this.setHistory(state)
        this.broadcast(state)
    }

    private fun setHistory(state: T) {
        this._history = _history.next(state)
    }

    private suspend fun broadcast(state: T) {
        broadcast.send(state)
    }

}

