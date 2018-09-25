package io.sellmair.quantum.internal.entangle

import io.sellmair.quantum.*
import io.sellmair.quantum.internal.*
import java.util.concurrent.TimeUnit

internal class Entanglement<Parent, Child>(
    private val parent: Quantum<Parent>,
    private val connection: Connection<Parent, Child>,
    private val projection: Projection<Parent, Child>) : Quantum<Child> {

    override fun setState(reducer: Reducer<Child>) = members {
        /*
        Immediately reject the reducer if quitted or not alive anymore.
         */
        if (quitted || quittedSafely || !isAlive) {
            return@members CycleFuture.rejected(config.callbackExecutor)
        }

        /*
        Increase the current number of enqueued reducers
        This number is used to keep track whether or not the Quantum became idle or not
         */
        enqueuedReducers++
        debug("[Entanglement]: setState $enqueuedReducers enqueuedReducers")

        /*
        Run the actual reducer
         */
        parent
            .setState {

                /*
                Determine whether or not the reducer is allowed to run
                 */
                val shouldRun = members {
                    /*
                   It should never be possible to run a reducer when the Quantum is not alive.
                   This is due to the increment in enqueuedReducers.

                   A failing requirement here would mean, that the Quantum dies
                   before the parent died and before enqueued reducers ran.
                     */
                    require(isAlive)
                    !quitted
                }

                /*
               Return noop if the reducer should not run.
                 */
                when {
                    shouldRun -> connection(this, reducer(projection(this)))
                    else -> this
                }
            }

            /*
            Signal that the reducer is finished when the cycle of the future
            was done.
             */
            .after(::onReducerFinished)
    }

    override fun withState(action: Action<Child>) = members {
        /*
        Reject the action immediately if the Quantum was quitted, quittedSafely
        or is not alive anymore.
         */
        if (quitted || quittedSafely || !isAlive) {
            return@members CycleFuture.rejected(config.callbackExecutor)
        }

        /*
        Increase the number of currently enqueued actions.
        This number is used to keep track, whether or not the current Quantum is idle
         */
        enqueuedActions++
        debug("[Entanglement]: withState $enqueuedActions enqueuedActions")

        /*
        Perform the actual action
         */
        parent
            .withState {
                /*
                Determine whether or not the action is supposed to run
                 */
                val shouldRun = members {
                    /*
                    We require that the quantum that has enqueued the reducer
                    is still alive, because we increased the number of enqueued actions.

                    It means that this quantum dies before the parent or before pending
                    actions completed if the requirement fails
                     */
                    require(isAlive)
                    !quitted
                }

                if (shouldRun) {
                    action(projection(this))
                }
            }
            .after(::onActionFinished)
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

    override fun addStateListener(listener: StateListener<Child>): Unit = members {
        val outerListener = { outer: Parent ->
            listener(projection(outer))
        }

        parent.addStateListener(outerListener)
        stateListeners[listener] = outerListener
    }

    override fun removeStateListener(listener: StateListener<Child>): Unit = members {
        val outerListener = stateListeners[listener]
        if (outerListener != null) {
            parent.removeStateListener(outerListener)
        }
    }

    override val history: History<Child> = ProjectionHistory(parent.history, projection)

    override val config = parent.config

    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: MEMBERS
    ################################################################################################
    */
    private inner class Members {

        /**
         * Map of listeners registered to this Quantum to listeners registered to the
         * parent Quantum. Each parent quantum listener is associated by one child
         * quantum listener and only performs the projection and than calls the child.
         *
         * This mapping is necessary make it possible to remove the parent listener
         * from the parent, once the child listener wants to get removed from the child.
         *
         * This bookkeeping is necessary because we cannot add the child listener
         * to the parent directly.
         *
         * All state listeners will be invoked by the callback executor of the parent.
         */
        val stateListeners = mutableMapOf<StateListener<Child>, StateListener<Parent>>()

        /**
         * Reference to all listeners that are interested in the death of this Quantum.
         *
         * All listeners will be invoked by the callback executor of the parent.
         */
        val quittedListeners = mutableListOf<QuittedListener>()


        /**
         * Signalling whether or not this Quantum is still alive.
         * This can even be ````false```` if neither [quit] nor [quitSafely] was called,
         * if the parent Quantum died.
         */
        var isAlive = true

        /**
         * Indicates whether or not [quit] was called.
         */
        var quitted = false

        /**
         * Indicates whether or not [quitSafely] was called
         */
        var quittedSafely = false

        /**
         * Keeps track of how many reducers are currently enqueued.
         * This number is used to identify whether or not this Quantum is idle
         *
         * @see enqueuedActions
         * @see considerIdle
         */
        var enqueuedReducers = 0

        /**
         * Keeps track of how many actions are currently enqueued.
         * This number is used to identify whether or not this Quantum is idle
         *
         * @see enqueuedReducers
         * @see considerIdle
         */
        var enqueuedActions = 0
    }

    private val members = Locked(Members())


/*
################################################################################################
PRIVATE IMPLEMENTATION: Keep track of enqueued reducers / actions
################################################################################################
*/

    /**
     * Called to signal that a single reducer has finished its' job.
     * This has to be called once the whole cycle finished to ensure, that
     * not even the reducer was performed, BUT all state updates have been posted.
     *
     * This function will decrease the number of currently enqueued reducers by one
     * and call [considerIdle]
     */
    private fun onReducerFinished() = members {
        debug("[Entanglement]: onReducerFinished")
        check(enqueuedReducers >= 1)
        enqueuedReducers--
        debug("onReducerFinished: $enqueuedReducers enqueued reducers")
        considerIdle()

    }

    /**
     * Called to signal that a single action has finished its' job.
     * This has to be called once the whole cycle finished to ensure, that
     * not even the action was performed, BUT all state updates have been posted.
     *
     * This function will decrease the number of currently enqueued actions by one
     * and call [considerIdle]
     */
    private fun onActionFinished() = members {
        debug("[Entanglement]: onActionFinished")
        check(enqueuedActions >= 1)
        enqueuedActions--
        considerIdle()

    }

    /**
     * Will call [onIdle] if the number of [Members.enqueuedReducers] and
     * [Members.enqueuedActions] is 0
     *
     * Requires that the member lock is held.
     */
    private fun Members.considerIdle() {
        debug("[Entanglement]: considerIdle")
        require(members.isHeldByCurrentThread)
        check(enqueuedReducers >= 0)
        check(enqueuedActions >= 0)
        if (enqueuedReducers == 0 && enqueuedActions == 0) {
            onIdle()
        }
    }

    /**
     * Will be called if no reducers or actions are currently enqueued anymore.
     * Will call [onChildQuitted] if [quit] or [quitSafely] was called previously.
     *
     * Requires that the member lock is held.
     */
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

    /**
     * Called once the parent dies
     */
    private fun onParentQuitted() = members {
        if (isAlive) {
            onQuitted()
        }
    }

    /**
     * Called once the child dies
     */
    private fun Members.onChildQuitted() {
        debug("[Entanglement]: onChildQuitted")
        require(members.isHeldByCurrentThread)
        if (!isAlive) return

        parent.removeQuittedListener(::onParentQuitted)
        onQuitted()
    }

    /**
     * Called if either the parent or the child died.
     * Will notify all quitted listeners.
     *
     * @see notifyQuittedListeners
     */
    private fun Members.onQuitted() {
        debug("[Entanglement]: onQuitted")
        require(members.isHeldByCurrentThread)
        require(isAlive)

        isAlive = false
        notifyQuittedListeners()
    }

    /**
     * Will notify all [Members.quittedListeners] from the parents callbackExecutor
     */
    private fun notifyQuittedListeners() = parent.config.callbackExecutor.execute {
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

        /**
         * @see performJoin
         */
        override fun join() {
            performJoin(null, null)
        }


        /**
         * @see performJoin
         */
        override fun join(timeout: Long, unit: TimeUnit): Boolean {
            return performJoin(timeout, unit)
        }


        /**
         * Performs the join operation
         * [timeout] will be used if not null and [unit] not null.
         */
        fun performJoin(timeout: Long?, unit: TimeUnit?): Boolean {

            /*
            Create waiting condition.
             */
            val condition = members.newCondition()

            /*
            Function intended to wake the thread up, that is waiting for
            quitting.
             */
            fun onQuitted() = members {
                condition.signalAll()
            }

            members {

                /*
                Join can be returned immediately if the Quantum is not alive anymore.
                 */
                if (!isAlive) return true

                /*
                Register the wakeup function
                 */
                addQuittedListener(::onQuitted)

                /*
                Create the proper await respecting any given timeout
                 */
                val await = if (timeout != null && unit != null) {
                    condition.asAwait(timeout, unit)
                } else condition.asAwait()

                /*
                Perform the join
                 */
                return await()
            }
        }


    }


    init {
        parent.addQuittedListener(this::onParentQuitted)
    }

}

