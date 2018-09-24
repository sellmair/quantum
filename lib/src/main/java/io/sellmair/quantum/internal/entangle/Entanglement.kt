package io.sellmair.quantum.internal.entangle

import io.sellmair.quantum.*
import io.sellmair.quantum.internal.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal class Entanglement<Outer, Inner>(
    private val parent: Quantum<Outer>,
    private val callbackExecutor: Executor = parent.config.callbackExecutor,
    private val connection: Connection<Outer, Inner>,
    private val projection: Projection<Outer, Inner>) : Quantum<Inner> {

    override fun setState(reducer: Reducer<Inner>) = members {
        if (quitted || quittedSafely || !isAlive) return@members
        enqueuedReducers++
        debug("[Entanglement]: setState $enqueuedReducers enqueuedReducers")

        parent.setState {
            val shouldRun = members {
                require(isAlive)
                !quitted
            }

            if (shouldRun) {
                val inner = projection(this)
                connection(this, reducer(inner))
            } else {
                this
            }
        }

        parent.withState { onReducerFinished() }
    }

    override fun withState(action: Action<Inner>) = members {
        if (quitted || quittedSafely || !isAlive) return@members

        enqueuedActions++
        debug("[Entanglement]: withState $enqueuedActions enqueuedActions")

        parent.withState {
            val shouldRun = members {
                require(isAlive)
                !quitted
            }

            if (shouldRun) {
                action(projection(this))
            }

            onActionFinished()
        }
    }

    override fun quit(): Joinable = members {
        quitted = true
        considerIdle()
        createJoinable()
    }

    override fun quitSafely(): Joinable = members {
        quittedSafely = true
        considerIdle()
        createJoinable()
    }

    override fun addQuittedListener(listener: QuittedListener): Unit = members {
        if (!isAlive) {
            listener()
            return@members
        }

        quittedListeners.add(listener)
    }

    override fun removeQuittedListener(listener: QuittedListener): Unit = members {
        quittedListeners.add(listener)
    }

    override fun addStateListener(listener: StateListener<Inner>): Unit = members {
        val outerListener = { outer: Outer ->
            listener(projection(outer))
        }

        parent.addStateListener(outerListener)
        stateListeners[listener] = outerListener
    }

    override fun removeStateListener(listener: StateListener<Inner>): Unit = members {
        val outerListener = stateListeners[listener]
        if (outerListener != null) {
            parent.removeStateListener(outerListener)
        }
    }

    override val history: History<Inner> = ProjectionHistory(parent.history, projection)

    override val config = object : InstanceConfig {
        override val callbackExecutor: Executor = this@Entanglement.callbackExecutor
        override val executor: Executor = this@Entanglement.parent.config.executor
    }

    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: MEMBERS
    ################################################################################################
    */
    private inner class Members {

        val stateListeners = mutableMapOf<StateListener<Inner>, StateListener<Outer>>()

        val quittedListeners = mutableListOf<QuittedListener>()

        var isAlive = true

        var quitted = false

        var quittedSafely = false

        var enqueuedReducers = 0

        var enqueuedActions = 0
    }

    private val members = Locked(Members())


/*
################################################################################################
PRIVATE IMPLEMENTATION: Keep track of enqueued reducers / actions
################################################################################################
*/

    private fun onReducerFinished() = members {
        debug("[Entanglement]: onReducerFinished")
        check(enqueuedReducers >= 1)
        enqueuedReducers--
        considerIdle()

    }

    private fun onActionFinished() = members {
        debug("[Entanglement]: onActionFinished")
        check(enqueuedActions >= 1)
        enqueuedActions--
        considerIdle()

    }

    private fun Members.considerIdle() {
        debug("[Entanglement]: considerIdle")
        require(members.isHeldByCurrentThread)
        check(enqueuedReducers >= 0)
        check(enqueuedActions >= 0)
        if (enqueuedReducers == 0 && enqueuedActions == 0) {
            onIdle()
        }
    }

    private fun Members.onIdle() {
        debug("[Entanglement]: onIdle")
        check(members.isHeldByCurrentThread)

        if (quitted || quittedSafely) {
            onChildQuitted()
        }
    }


/*
################################################################################################
PRIVATE IMPLEMENTATION: Quitting
################################################################################################
*/

    private fun onParentQuitted() = members {
        if (isAlive) {
            onQuitted()
        }
    }

    private fun Members.onChildQuitted() {
        debug("[Entanglement]: onChildQuitted")
        require(members.isHeldByCurrentThread)
        if (!isAlive) return

        parent.removeQuittedListener(::onParentQuitted)
        onQuitted()
    }

    private fun Members.onQuitted() {
        debug("[Entanglement]: onQuitted")
        require(members.isHeldByCurrentThread)
        check(isAlive)

        isAlive = false
        notifyQuittedListeners()
    }

    private fun notifyQuittedListeners() = callbackExecutor.execute {
        debug("[Entanglement]: notifyQuittedListeners")
        val listeners = members { quittedListeners.poll() }
        for (listener in listeners) {
            listener()
        }

        debug("[Entanglement]: notifyQuittedListeners [DONE]")
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Create joinable
    ################################################################################################
    */

    private fun createJoinable() = object : Joinable {

        override fun join() {
            performJoin(null, null)
        }

        override fun join(timeout: Long, unit: TimeUnit): Boolean {
            return performJoin(timeout, unit)
        }

        fun performJoin(timeout: Long?, unit: TimeUnit?): Boolean {
            val condition = members.newCondition()

            fun onQuitted() = members {
                condition.signalAll()
            }

            members {
                if (!isAlive) return true

                addQuittedListener(::onQuitted)
                val await = if (timeout != null && unit != null) {
                    condition.asAwait(timeout, unit)
                } else condition.asAwait()

                return await()
            }
        }


    }


    init {
        parent.addQuittedListener(this::onParentQuitted)
    }

}

