package io.sellmair.quantum.internal

import io.sellmair.quantum.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.newCoroutineContext
import kotlin.coroutines.CoroutineContext

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class ProjectionOwner<T, Projection>(
    private val owner: Owner<T>,
    private val projection: (T) -> Projection,
    private val connection: T.(Projection) -> T) : ChronologicalOwner<Projection> {

    /*
    ################################################################################################
    API
    ################################################################################################
    */

    override val coroutineContext: CoroutineContext = owner.newCoroutineContext(owner.coroutineContext + Job())

    override val states: ReceiveChannel<Projection>
        get() = createProjectionReceiveChannel()

    override val state: State<Projection> by lazy {
        State(
            access = this.createProjectionAccess(),
            mutex = owner.state.mutex,
            onState = { state -> onState(state) })
    }

    override suspend fun quit() {
        this.coroutineContext.cancel()
    }

    override suspend fun history(): History<Projection> {
        if (owner is ChronologicalOwner<T>) {
            val history = owner.history()
            return DefaultHistory(
                setting = history.setting,
                elements = history.map(projection))
        }

        return NoHistory()
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION
    ################################################################################################
    */

    private fun createProjectionReceiveChannel(): ReceiveChannel<Projection> {
        return owner.states.map { t -> projection(t) }
    }

    private fun createProjectionAccess() = object : Access<Projection> {

        /**
         * Getter is expected to be still called inside the mutex
         */
        override val state: Projection get() = projection(owner.state.value)
    }

    /**
     * Method is expected to be still called by inside of the mutex
     */
    private suspend fun onState(projection: Projection) {
        val state = owner.state.value
        val newState = connection(state, projection)
        owner.state.onState(newState)
    }
}