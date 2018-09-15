package io.sellmair.quantum.internal

import io.sellmair.quantum.*
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

internal class ExecutorQuantum<T>(
    initial: T,
    private val stateSubject: StateSubject<T>,
    private val quittedSubject: QuittedSubject,
    private val executor: Executor) :
    Quantum<T>,
    StateObservable<T> by stateSubject,
    QuittedObservable by quittedSubject {

    /*
    ################################################################################################
    API: Quantum
    ################################################################################################
    */

    override fun setState(reducer: Reducer<T>): Unit = members {
        if (quitted || quittedSafely) return@members
        pendingReducers.add(reducer)
        verbose("Reducer enqueued. ${pendingReducers.size} reducers pending for next cycle")
        notifyWork()
    }

    override fun withState(action: Action<T>): Unit = members {
        if (quitted || quittedSafely) return@members
        pendingActions.add(action)
        verbose("Action enqueued. ${pendingActions.size} actions pending for next cycle")
        notifyWork()
    }

    override fun quit(): Joinable = members {
        debug("quit")
        quitted = true
        notifyWork()
        createJoinable()
    }

    override fun quitSafely(): Joinable = members {
        debug("quitSafely")
        quittedSafely = true
        notifyWork()
        createJoinable()
    }

    override val history = SynchronizedHistory(initial).also { history ->
        config {
            history.enabled = this.history.default.enabled
            history.limit = this.history.default.limit
        }
    }

    /*
    ################################################################################################
    PRIVATE: Definitions
    ################################################################################################
    */

    data class Cycle<T>(val reducers: List<Reducer<T>>, val actions: List<Action<T>>)


    /*
    ################################################################################################
    PRIVATE: Locked Members
    ################################################################################################
    */

    private inner class Members {
        /**
         * All reducers that are currently pending for the next cycle
         */
        val pendingReducers = mutableListOf<Reducer<T>>()

        /**
         * All actions that are currently pending for the next cycle
         */
        val pendingActions = mutableListOf<Action<T>>()

        /**
         * Indicates whether or not [quit] was called.
         * This Quantum won't execute any reducer / action if this is true
         */
        var quitted = false

        /**
         * Indicating whether or not [quittedSafely] was called.
         * This Quantum won't enqueue any new reducers or actions if true
         */
        var quittedSafely = false

        /**
         * Indicating whether or not a thread is currently working on
         * inside this quantum.
         */
        var isExecuting = false

        /**
         * Indicating whether or not a thread is already requested to work on this quantum
         * Cannot be true while [isExecuting] is also true
         */
        var isStarting = false

    }

    /**
     * Locked access to the members
     */
    private val members = Locked(Members())


    /*
    ################################################################################################
    PRIVATE: Implementation
    ################################################################################################
    */

    /**
     * Condition will signal all once the a thread finished the whole workload
     */
    private val workloadFinished = members.newCondition()

    /**
     * Lock used to ensure that only one thread can enter a cycle
     */
    private val cycleLock = ReentrantLock()

    /**
     * Represents the current state of the quantum.
     * This reference may only be touched while holing the cycle lock
     */
    private var internalState: T = initial
        set(value) {
            require(cycleLock.isHeldByCurrentThread)
            field = value
        }

    private fun notifyWork() = members {
        /*
        No action required if currently running
         */
        if (isExecuting) return@members

        /*
        No action required if currently starting
         */
        if (isStarting) return@members


        /*
        If not currently executing or starting:
        Start new workload execution
         */
        isStarting = true

        executor.execute {
            execute() ?: warn("Execution occupied")
        }
    }

    private fun execute() = cycleLock.tryWithLock {
        members {
            debug("Started")
            isExecuting = true
            isStarting = false
        }


        /*
        Counter for info purposes, that counts the amount of cycles that one workload
        executed.
         */
        var cycles = 0

        while (running()) {
            cycle()
            cycles++
        }


        info("finished workload after $cycles cycles")
        onExit()
    }


    /**
     * Will check if another cycle should be executed.
     * Will set [Members.isExecuting] to 'false' if the workload is finished.
     * @return whether or not the current thread should run another cycle.
     */
    private fun running(): Boolean = members {
        val hasWorkload = pendingReducers.isNotEmpty() || pendingActions.isNotEmpty()

        if (!hasWorkload) {
            debug("Finished")
            isExecuting = false
        }

        hasWorkload
    }


    private fun cycle() {
        /*
        Store instance of current state (before creating new states)
        */
        val previousState = internalState

        /*
        Get all reducers and actions that are supposed to run in this current cycle
         */
        val cycle = pollCycle()

        /*
        Apply all reducers (which will alter the internalState reference)
         */
        applyReducers(cycle.reducers)

        /*
        Invoke all actions with the new internal state
         */
        invokeActions(cycle.actions)

        /*
        Only publish the state if it actually changed.
        Reducers are allowed to return the current instance to
        signal that a NOOP was done!
         */
        if (previousState != internalState) {
            info("publish new state: $internalState")
            stateSubject.publish(internalState)
        }
    }

    private fun pollCycle() = members {
        Cycle(reducers = pendingReducers.poll(), actions = pendingActions.poll()).apply {
            info("cycle with ${reducers.size} reducers, ${actions.size} actions")
        }
    }

    private fun applyReducers(reducers: List<Reducer<T>>) {
        for (reducer in reducers) {

            /*
            Do not work anymore if quitted.
             */
            if (members { quitted }) return

            internalState = reducer(internalState)
            history.add(internalState)
        }
    }

    private fun invokeActions(actions: List<Action<T>>) {
        for (action in actions) {
            /*
            Do not work anymore if quitted
             */
            if (members { quitted }) return

            action(internalState)
        }
    }

    private fun createJoinable(): Joinable {
        return object : Joinable {
            override fun join() = members {
                fun isWorking() = isExecuting || isStarting
                fun isQuitting() = quitted || quittedSafely
                fun isNotQuitting() = !isQuitting()
                fun shouldWait(): Boolean {
                    check(members.isHeldByCurrentThread)
                    return isWorking() || isNotQuitting()
                }

                while (shouldWait()) {
                    workloadFinished.await()
                }
            }
        }
    }

    /**
     * Called if a thread has finished its workload and leaves the [execute] function
     */
    private fun onExit() = members {
        if (quitted || quittedSafely) {
            quittedSubject.quitted()
        }

        workloadFinished.signalAll()
    }


    init {
        stateSubject.publish(initial)
    }

}

