package io.sellmair.quantum.internal

import io.sellmair.quantum.StateListener
import io.sellmair.quantum.StateObservable
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class StateSubject<T>(
    /**
     * Executor used to notify all listeners
     */
    private val executor: Executor) : StateObservable<T> {


    /**
     * All registered listeners.
     * Synchronized via [lock]
     */
    private val listeners = mutableListOf<StateListener<T>>()

    /**
     * The current state.
     * This will be delivered to any listener immediately on [addListener]
     * Synchronized via [lock]
     */
    private var state: T? = null


    /**
     * Lock used to synchronize [listeners] & [state] with.
     */
    private var lock = ReentrantLock()


    /**
     * Registers a listener.
     * The listener will be invoked with the last state (if present)
     */
    override fun addListener(listener: StateListener<T>): Unit = lock.withLock {
        listeners.add(listener)

        /*
        State has to be captured inside the lock, but outside of the post
        to ensure integrity
         */
        val state = this.state
        if (state != null) {
            executor.execute { listener(state) }
        }
    }

    override fun removeListener(listener: StateListener<T>): Unit = lock.withLock {
        listeners.remove(listener)
    }

    /**
     * Notifies all listeners.
     * Listeners are notified asynchronously by a common looper.
     *
     * This function is locked with [lock]
     */
    fun publish(state: T): Unit = lock.withLock {
        this.state = state

        /*
        Listeners have to be captured inside this lock, but outside
        of the post to ensure integrity.
        Capturing all listeners inside the post statement could lead
        to listeners getting invoked twice with the initial state.
         */
        val listeners = lock.withLock {
            listeners.toTypedArray()
        }

        executor.execute {
            for (listener in listeners) {
                listener(state)
            }
        }
    }

}