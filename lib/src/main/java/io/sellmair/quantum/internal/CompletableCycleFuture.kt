package io.sellmair.quantum.internal

import io.sellmair.quantum.CycleFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class CompletableCycleFuture(
    val callbackExecutor: Executor = Executor { it.run() }) : CycleFuture {

    /*
    ################################################################################################
    PUBLIC API
    ################################################################################################
    */

    override fun after(action: () -> Unit) {
        addAfterListener(action)
    }

    override fun completed(action: () -> Unit) {
        addCompletionListener(action)
    }

    override fun rejected(action: () -> Unit) {
        addRejectionListener(action)
    }

    override fun join() {
        doJoin(null, null)
    }

    override fun join(timeout: Long, unit: TimeUnit): Boolean {
        return doJoin(timeout, unit)
    }

    /*
    ################################################################################################
    INTERNAL API
    ################################################################################################
    */

    fun completed() {
        notifyCompletion()
        notifyAfter()
    }

    fun rejected() {
        notifyRejection()
        notifyAfter()
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Members
    ################################################################################################
    */

    private class Members {
        var isCompleted = false
        var isRejected = false
        val afterListeners = mutableListOf<() -> Unit>()
        val completionListeners = mutableListOf<() -> Unit>()
        val rejectionListeners = mutableListOf<() -> Unit>()
    }

    private val members = Locked(Members())


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Listeners
    ################################################################################################
    */

    private fun notifyCompletion() {
        val listeners = members {
            isCompleted = true
            completionListeners.poll()
        }

        callbackExecutor.execute {
            for (listener in listeners) {
                listener()
            }
        }
    }

    private fun notifyRejection() {
        val listeners = members {
            isRejected = true
            rejectionListeners.poll()
        }

        callbackExecutor.execute {
            for (listener in listeners) {
                listener()
            }
        }
    }

    private fun notifyAfter() {
        val listeners = members { afterListeners.poll() }

        callbackExecutor.execute {
            for (listener in listeners) {
                listener()
            }
        }
    }

    private fun addAfterListener(listener: () -> Unit) = members {
        if (isRejected || isCompleted) {
            callbackExecutor.execute(listener)
            return@members
        }

        afterListeners.add(listener)
    }

    private fun addCompletionListener(listener: () -> Unit) = members {
        if (isCompleted) {
            callbackExecutor.execute(listener)
            return@members
        }

        completionListeners.add(listener)

    }

    private fun addRejectionListener(listener: () -> Unit) = members {
        if (isRejected) {
            callbackExecutor.execute(listener)
            return@members
        }

        rejectionListeners.add(listener)
    }

    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Join
    ################################################################################################
    */

    private val joinCondition = members.newCondition()

    private fun doJoin(timeout: Long?, unit: TimeUnit?): Boolean = members {
        if (isRejected || isCompleted) return@members true

        val await = when {
            timeout != null && unit != null -> joinCondition.asAwait(timeout, unit)
            else -> joinCondition.asAwait()
        }

        after { members { joinCondition.signalAll() } }
        return await()
    }
}