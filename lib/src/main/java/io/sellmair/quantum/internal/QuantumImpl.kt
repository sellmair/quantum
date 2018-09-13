package io.sellmair.quantum.internal

import android.util.Log
import io.sellmair.quantum.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class QuantumImpl<T>(
    initial: T,
    private val subject: StateSubject<T>) :

    Quantum<T>,
    StateObservable<T> by subject {

    /*
    ################################################################################################
    API
    ################################################################################################
    */


    override fun setState(reducer: Reducer<T>) {
        if (!looping || quittedSafely) return

        lock.withLock {
            pendingReducers.add(reducer)
            condition.signalAll()
        }
    }

    override fun setStateIt(reducer: ItReducer<T>) {
        this.setState(reducer)
    }

    override fun withState(action: Action<T>) {
        if (!looping || quittedSafely) return
        lock.withLock {
            pendingActions.add(action)
            condition.signalAll()
        }
    }

    override fun withStateIt(action: ItAction<T>) {
        withState(action)
    }


    override fun quit(): Joinable {
        lock.withLock {
            log("quit")

            this.looping = false
            this.quitted = true
            condition.signalAll()
        }

        return worker.asJoinable()
    }

    override fun quitSafely(): Joinable {
        lock.withLock {
            log("quitSafely")

            quittedSafely = true
            condition.signalAll()
        }

        return worker.asJoinable()
    }

    override val history: MutableHistory<T> = SynchronizedHistory<T>().apply { enabled = false }

    /*
    ################################################################################################
    PRIVATE
    ################################################################################################
    */


    /* State */
    /**
     * Represents the current state.
     * This reference is only allowed to be altered by the [worker] thread.
     */
    private var internalState: T = initial

    /**
     * History of all states sequentially started by the initial state,
     * ended by the current [internalState]
     */
    private val internalHistory = mutableListOf<T>()


    /* CONCURRENCY MEMBERS */
    /**
     * Lock to synchronize communication between any other thread an [worker] thread.
     */
    private val lock = ReentrantLock()

    /**
     * Condition used to notify the state thread, that pending work arrived.
     */
    private val condition = lock.newCondition()

    /**
     * Flag indicating whether or not the [worker] thread should stay alive.
     * @see quit
     */
    private var looping by AtomicBoolean(true)


    /**
     * Indicating that [quitSafely] was called.
     *
     *
     * It is possible that [looping] is true while [quittedSafely] is also true.
     * This state is indicating that the last cycle is running
     * while no more actions or reducers should be enqueued
     *
     */
    private var quittedSafely by AtomicBoolean(false)


    /**
     * Indicating whether or not any work should be done by the Quantum.
     * Unless [looping], this will also prevent any reducer from
     * getting worked
     */
    private var quitted by AtomicBoolean(false)


    /* JOBS */
    /**
     * All reducers that are currently in the pipeline.
     * Reducers that should be executed last will be last in this list
     */
    private val pendingReducers = mutableListOf<Reducer<T>>()

    /**
     * All pending actions that are currently in the pipeline.
     * Actions that should be executed last will be last in this list
     */
    private val pendingActions = mutableListOf<Action<T>>()


    /**
     * The internal thread for this state holder.
     * All reducers and actions will be invoked by this thread only.
     * This thread is the only thread allowed to update the [internalHistory]
     * or set the [internalState]
     */
    private val worker = object : Thread("StateStoreWorker") {
        override fun run() {
            /*
            Loop until close is called (which then will set this flag to false)
             */
            while (looping && !quitted) {
                /*
               Stopping indicates that all reducers / actions that are currently eqnueued are
               supposed to be executed (and no more shall be enqueued).
               This means, that the thread is supposed to perform exactly one more loop
               once this flag was set.
               */
                if (quittedSafely) looping = false

                /*
                Du a full cycle:
                Apply reducers, perform actions & notify listener
                 */
                cycle()


                /*
                Enter the lock to check weather or not there is anything left to do
                 */
                lock.withLock {
                    /*
                    Do not go to sleep if there are (new) reducers pending.
                    Do not go to sleep if not looping anymore
                    Do not go to sleep if currently quittedSafely
                    Do not go to sleep if quitted.
                     */
                    if (pendingReducers.isEmpty() && looping && !quittedSafely && !quitted) {
                        log("sleeping")
                        condition.await()
                        log("woke up")
                    }
                }
            }

        }

        private fun cycle() {
            /*
            Store instance of current state (before creating new states)
             */
            val previousState = internalState

            /*
            Get all reducers and actions that are supposed to run in this currennt cycle
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

        /**
         * Creates a new cycle from all currently pending reducers and actions.
         * Those pending reducers and actions will be cleared
         */
        private fun pollCycle(): Cycle<T> = lock.withLock {
            val cycle = object : Cycle<T> {
                override val reducers = pendingReducers.poll()
                override val actions = pendingActions.poll()
            }

            log("cycle with ${cycle.reducers.size} reducers & ${cycle.actions.size} actions")
            cycle
        }


        private fun applyReducers(reducers: List<Reducer<T>>) {
            for (reducer in reducers) {

                /*
                Do not work anymore if quitted.
                 */
                if (quitted) return

                internalState = reducer(internalState)
                internalHistory.add(internalState)
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

        /**
         * Creates a copy of the current list and clears the current list afterwards
         */
        private fun <T> MutableList<T>.poll(): List<T> {
            val copy = ArrayList<T>(this.size)
            copy.addAll(this)
            this.clear()
            return copy
        }
    }

    interface Cycle<T> {
        val reducers: List<Reducer<T>>
        val actions: List<Action<T>>
    }

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, message)
        }
    }

    companion object {
        val LOG_TAG: String = QuantumImpl::class.java.simpleName
    }

    init {
        subject.publish(initial)
        worker.start()
    }

}