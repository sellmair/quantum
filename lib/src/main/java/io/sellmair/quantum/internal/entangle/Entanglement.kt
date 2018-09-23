package io.sellmair.quantum.internal.entangle

import io.sellmair.quantum.*
import io.sellmair.quantum.internal.InternalQuantum
import io.sellmair.quantum.internal.Locked
import io.sellmair.quantum.internal.asAwait
import io.sellmair.quantum.internal.poll
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class Entanglement<Outer, Inner>(
    private val parent: InternalQuantum<Outer>,
    private val callbackExecutor: Executor = parent.callbackExecutor,
    private val connection: Connection<Outer, Inner>,
    private val projection: Projection<Outer, Inner>) : Quantum<Inner> {

    override fun setState(reducer: Reducer<Inner>) = members {
        if (quitted || quittedSafely || !isAlive) return@members

        enqueuedReducers++

        parentSetState@ parent.setState {
            val shouldRun = members {
                require(isAlive)
                !quitted
            }

            val newState = if (shouldRun) {
                val inner = projection(this)
                connection(this, reducer(inner))
            } else this

            onReducerFinished()
            newState
        }
    }

    override fun withState(action: Action<Inner>) = members {
        if (quitted || quittedSafely || !isAlive) return@members

        enqueuedActions++

        parentWithState@ parent.withState {
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
        createJoinable()
    }

    override fun quitSafely(): Joinable = members {
        quittedSafely = true
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
        check(enqueuedReducers >= 1)
        enqueuedReducers--
        considerIdle()

    }

    private fun onActionFinished() = members {
        check(enqueuedActions >= 1)
        enqueuedActions--
        considerIdle()

    }

    private fun Members.considerIdle() {
        require(members.isHeldByCurrentThread)
        check(enqueuedReducers >= 0)
        check(enqueuedActions >= 0)
        if (enqueuedReducers == 0 && enqueuedActions == 0) {
            onIdle()
        }
    }

    private fun Members.onIdle() {
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
        require(members.isHeldByCurrentThread)
        if (!isAlive) return

        parent.removeQuittedListener(::onParentQuitted)
        onQuitted()
    }

    private fun Members.onQuitted() {
        require(members.isHeldByCurrentThread)
        check(isAlive)

        isAlive = false
        notifyQuittedListeners()
    }

    private fun notifyQuittedListeners() = callbackExecutor.execute {
        val listeners = members { quittedListeners.poll() }
        for (listener in listeners) {
            listener()
        }
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
            val lock = ReentrantLock()
            val condition = lock.newCondition()

            fun onQuitted() = lock.withLock {
                condition.signalAll()
            }

            lock.withLock {
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