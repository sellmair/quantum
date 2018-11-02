package io.sellmair.quantum.internal

import io.sellmair.quantum.ForbiddenThreadException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock

internal class SingleThreadLock(
    private val thread: Thread) : Lock {
    override fun lock() {
        requireCorrectThread()
    }

    override fun tryLock(): Boolean {
        return Thread.currentThread() == thread
    }

    override fun tryLock(time: Long, unit: TimeUnit?): Boolean {
        return tryLock()
    }

    override fun unlock() = Unit

    override fun lockInterruptibly() {
        requireCorrectThread()
    }

    override fun newCondition(): Condition {
        requireCorrectThread()
        throw UnsupportedOperationException("" +
            ".newCondition() is not available for SingleThreadLock")
    }


    /*
    ################################################################################################
    HELPER
    ################################################################################################
    */

    private fun requireCorrectThread() {
        if (Thread.currentThread() != thread) {
            throw ForbiddenThreadException("" +
                "Expected thread: ${thread.name} " +
                "Found: ${Thread.currentThread().name}")
        }
    }

}