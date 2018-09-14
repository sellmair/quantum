package io.sellmair.quantum.internal

import android.util.Log
import io.sellmair.quantum.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ExecutorServiceQuantum<T>(
    initial: T,
    private val subject: StateSubject<T>,
    private val service: ExecutorService) :
    Quantum<T>,
    StateObservable<T> by subject {

    /*
    ################################################################################################
    API: Quantum
    ################################################################################################
    */

    override fun setState(reducer: Reducer<T>): Unit = memberLock.withLock {
        if (quitted || quittedSafely) return
        pendingReducers.add(reducer)
        notifyWork()
    }

    override fun withState(action: Action<T>): Unit = memberLock.withLock {
        if (quitted || quittedSafely) return
        pendingActions.add(action)
        notifyWork()
    }

    override fun quit(): Joinable {
        quitted = true
        return createJoinable()
    }

    override fun quitSafely(): Joinable {
        quittedSafely = true
        return createJoinable()
    }

    override val history = SynchronizedHistory(initial).apply { enabled = false }


    /*
    ################################################################################################
    PRIVATE: Implementation
    ################################################################################################
    */

    /**
     * Lock used to synchronize members of this quantum
     */
    private val memberLock = ReentrantLock()

    private val finishedExecutingCondition = memberLock.newCondition()

    /**
     * Lock used to ensure that only one thread can enter a cycle
     */
    private val cycleLock = ReentrantLock()

    private var internalState: T = initial

    private val pendingReducers = mutableListOf<Reducer<T>>()

    private val pendingActions = mutableListOf<Action<T>>()

    private var quitted by AtomicBoolean(false)

    private var quittedSafely by AtomicBoolean(false)

    private var isExecuting = false

    private var isStarting = false

    private fun notifyWork() = memberLock.withLock {
        /*
        No action required if currently running
         */
        if (isExecuting) return@withLock

        /*
        No action required if currently starting
         */
        if (isStarting) return@withLock


        /*
        If not currently executing or starting:
        Start new workload execution
         */
        isStarting = true

        service.execute {
            execute()
        }
    }

    private fun execute() = cycleLock.tryWithLock {
        memberLock.withLock {
            isExecuting = true
            isStarting = false
        }

        var cycles = 0

        fun run(): Boolean = memberLock.withLock {
            val hasWorkload = pendingReducers.isNotEmpty() || pendingActions.isNotEmpty()

            if (!hasWorkload) {
                isExecuting = false
                finishedExecutingCondition.signalAll()
            }

            hasWorkload
        }

        while (run()) {
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

    private fun pollCycle() = memberLock.withLock {
        Cycle(reducers = pendingReducers.poll(), actions = pendingActions.poll())
    }

    private fun applyReducers(reducers: List<Reducer<T>>) {
        for (reducer in reducers) {

            /*
            Do not work anymore if quitted.
             */
            if (quitted) return

            internalState = reducer(internalState)
            history.add(internalState)
        }
    }

    private fun invokeActions(actions: List<Action<T>>) {
        for (action in actions) {
            /*
            Do not work anymore if quitted
             */
            if (quitted) return

            action(internalState)
        }
    }

    private fun createJoinable(): Joinable = object : Joinable {
        override fun join() = memberLock.withLock {
            if (!isExecuting) return@withLock
            finishedExecutingCondition.await()
        }
    }

    data class Cycle<T>(val reducers: List<Reducer<T>>, val actions: List<Action<T>>)

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(QuantumImpl.LOG_TAG, message)
        }
    }

    init {
        subject.publish(initial)
    }

}