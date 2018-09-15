package io.sellmair.quantum.internal

import android.util.Log
import io.sellmair.quantum.*
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock

internal class ExecutorQuantum<T>(
    initial: T,
    private val subject: StateSubject<T>,
    private val executor: Executor) :
    Quantum<T>,
    StateObservable<T> by subject {

    /*
    ################################################################################################
    API: Quantum
    ################################################################################################
    */

    override fun setState(reducer: Reducer<T>): Unit = members {
        if (quitted || quittedSafely) return@members
        pendingReducers.add(reducer)
        notifyWork()
    }

    override fun withState(action: Action<T>): Unit = members {
        if (quitted || quittedSafely) return@members
        pendingActions.add(action)
        notifyWork()
    }

    override fun quit(): Joinable = members {
        quitted = true
        notifyWork()
        createJoinable()
    }

    override fun quitSafely(): Joinable = members {
        quittedSafely = true
        notifyWork()
        createJoinable()
    }

    override val history = SynchronizedHistory(initial).apply { enabled = false }

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
        val pendingReducers = mutableListOf<Reducer<T>>()

        val pendingActions = mutableListOf<Action<T>>()

        var quitted = false

        var quittedSafely = false

        var isExecuting = false

        var isStarting = false

    }

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

    private var internalState: T = initial

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
            execute() ?: Log.w(QuantumImpl.LOG_TAG, "Execution occupied")
        }
    }

    private fun execute() = cycleLock.tryWithLock {
        members {
            isExecuting = true
            isStarting = false
        }


        var cycles = 0

        fun running(): Boolean = members {
            val hasWorkload = pendingReducers.isNotEmpty() || pendingActions.isNotEmpty()

            if (!hasWorkload) {
                isExecuting = false
                workloadFinished.signalAll()
            }

            hasWorkload
        }

        while (running()) {
            cycle()
            cycles++
        }

        log("finished workload after $cycles cycles")
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
            log("publish new state: $internalState")
            subject.publish(internalState)
        }
    }

    private fun pollCycle() = members {
        Cycle(reducers = pendingReducers.poll(), actions = pendingActions.poll()).apply {
            log("cycle with ${reducers.size} reducers, ${actions.size} actions")
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

    private fun log(message: String) {
        if (BuildConfig.DEBUG && 1 == { 3 }()) {
            Log.d(QuantumImpl.LOG_TAG, message)
        }
    }

    init {
        subject.publish(initial)
    }

}

