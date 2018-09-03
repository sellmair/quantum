package io.sellmair.quantum.internal

import android.os.Handler
import android.os.Looper
import io.sellmair.quantum.StateListener
import io.sellmair.quantum.StateObservable
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

class StateSubject<T> : StateObservable<T> {

    private val handler = Handler(Looper.getMainLooper())

    private val listeners = mutableListOf<StateListener<T>>()

    private var state: T? = null

    private var lock = ReentrantLock()


    override fun addListener(listener: StateListener<T>): Unit = lock.withLock {
        listeners.add(listener)
        val state = this.state
        if (state != null) {
            handler.post { listener(state) }
        }
    }

    override fun removeListener(listener: StateListener<T>): Unit = lock.withLock {
        listeners.remove(listener)
    }

    fun publish(state: T): Unit = lock.withLock {
        this.state = state
        handler.post {
            lock.withLock {
                for (listener in listeners) {
                    listener(state)
                }
            }
        }
    }

}