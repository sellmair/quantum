package io.sellmair.quantum.internal

import io.sellmair.quantum.Joinable
import io.sellmair.quantum.Quitable
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

/**
 * [Quitable] and [Executor] backed by a single, running thread.
 * The thread will be kept alive until [quit] or [quitSafely] are called.
 *
 */
internal class SingleThreadExecutor : Executor, Quitable {

    /*
    ################################################################################################
    API
    ################################################################################################
    */
    override fun execute(command: Runnable): Unit = queueLock.withLock {
        if (quitted || quittedSafely) return@withLock
        queue.add(command)
        queueCondition.signalAll()
    }

    override fun quit(): Joinable = queueLock.withLock {
        quitted = true
        queueCondition.signalAll()
        return worker.asJoinable()
    }

    override fun quitSafely(): Joinable = queueLock.withLock {
        quittedSafely = true
        queueCondition.signalAll()
        return worker.asJoinable()
    }

    /*
    ################################################################################################
    PRIVATE: Implementation
    ################################################################################################
    */

    /**
     * Lock used to to synchronize the access to the [queue]
     */
    private val queueLock = ReentrantLock(true)

    /**
     * Condition used to notify the [worker] about a new task
     * Or that it should reevaluate the running status
     */
    private val queueCondition = queueLock.newCondition()

    /**
     * Signals whether or not [quit] was called.
     * No more tasks shall be executed once this flag is set to true
     */
    private var quitted = false

    /**
     * Signals whether or not [quitSafely] was called.
     * No more tasks shall be added to the [queue] once this flag is set to true
     * The thread finishes once all tasks in the [queue] are executed
     */
    private var quittedSafely = (false)


    /**
     * The working queue.
     * Holds all tasks that should be executed.
     * Has to be accessed only when [queueLock] is held.
     */
    private val queue: Queue<Runnable> = LinkedList()


    /**
     * Worker thread executing the queue.
     */
    private val worker = object : Thread("QuantumSingleThread-Worker") {
        override fun run() {
            while (running()) {
                next()?.run()
            }
        }


        private fun next(): Runnable? = queueLock.withLock {
            val next = queue.poll()
            if (next == null && !quittedSafely && !quitted ) queueCondition.await()
            next
        }

        private fun running(): Boolean = queueLock.withLock {
            if (quitted) return false
            if (quittedSafely && queue.isEmpty()) return false
            return true
        }
    }

    init {
        worker.start()
    }

}