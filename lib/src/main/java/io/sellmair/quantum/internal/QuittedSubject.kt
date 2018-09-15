package io.sellmair.quantum.internal

import io.sellmair.quantum.QuittedListener
import io.sellmair.quantum.QuittedObservable
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class QuittedSubject(
    private val executor: Executor) : QuittedObservable {

    private val lock = ReentrantLock()

    private var quitted = false

    private val listeners = mutableListOf<QuittedListener>()

    override fun addQuittedListener(listener: QuittedListener): Unit = lock.withLock {
        if (quitted) {
            executor.execute(listener)
            return@withLock
        }

        listeners.add(listener)
    }

    override fun removeQuittedListener(listener: QuittedListener): Unit = lock.withLock {
        listeners.remove(listener)
    }


    fun quitted() = lock.withLock {
        quitted = true
        val listeners = listeners.poll()
        executor.execute {
            for (listener in listeners) {
                listener()
            }
        }
    }
}