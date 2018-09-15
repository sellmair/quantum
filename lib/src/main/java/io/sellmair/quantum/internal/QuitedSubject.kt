package io.sellmair.quantum.internal

import io.sellmair.quantum.QuitedObservable
import io.sellmair.quantum.QuittedListener
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class QuitedSubject(
    private val executor: Executor) : QuitedObservable {

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