package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.Joinable
import io.sellmair.quantum.Threading
import io.sellmair.quantum.noop
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun Threading.Single.joinable(block: () -> Unit): Joinable {
    return when (this) {
        is Threading.Single.Throw -> joinable(block)
        is Threading.Single.Post -> joinable(block)
    }
}

/*
################################################################################################
PRIVATE IMPLEMENTATION
################################################################################################
*/

private fun Threading.Single.Throw.joinable(block: () -> Unit): Joinable {
    this { block() }
    return Joinable.noop()
}


private fun Threading.Single.Post.joinable(block: () -> Unit): Joinable {
    if (Looper.myLooper() == this.looper) {
        block()
        return Joinable.noop()
    }


    val lock = ReentrantLock()
    val condition = lock.newCondition()
    var executed = false

    val joinable = object : Joinable {
        override fun join() = lock.withLock {
            if (executed) return
            condition.asAwait().await()
            Unit
        }

        override fun join(timeout: Long, unit: TimeUnit): Boolean = lock.withLock {
            if (executed) return true
            condition.asAwait(timeout, unit).await()
        }

    }

    this {
        block()
        lock.withLock {
            executed = true
            condition.signalAll()
        }
    }

    return joinable
}

