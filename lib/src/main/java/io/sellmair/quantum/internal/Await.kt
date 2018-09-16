package io.sellmair.quantum.internal

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition

internal interface Await {
    /**
     * Will wait the current thread for unspecific time.
     * @return true if the waiting was successful, false if the waiting was aborted
     * or if some timeout was reached.
     */
    fun await(): Boolean

    /**
     * @see await
     */
    operator fun invoke(): Boolean = await()
}

internal fun Condition.asAwait(): Await {
    return object : Await {
        override fun await(): Boolean {
            this@asAwait.await()
            return true
        }
    }
}

internal fun Condition.asAwait(timeout: Long, unit: TimeUnit): Await {
    return TimeoutConditionAwait(this, timeout, unit)
}


internal fun Condition.asAwait(timeout: Long?, unit: TimeUnit = TimeUnit.SECONDS): Await {
    return if (timeout != null) this.asAwait(timeout, unit)
    else this.asAwait()
}

private class TimeoutConditionAwait(
    private val condition: Condition,
    private val timeout: Long,
    private val unit: TimeUnit) : Await {

    private val start = System.currentTimeMillis()

    private val timeoutMillis = unit.toMillis(timeout)

    override fun await(): Boolean {
        val remaining = remaining()
        if (remaining <= 0) return false
        return condition.await(remaining, TimeUnit.MILLISECONDS)
    }

    private fun elapsed(): Long {
        return System.currentTimeMillis() - start
    }

    private fun remaining(): Long {
        return timeoutMillis - elapsed()
    }

}