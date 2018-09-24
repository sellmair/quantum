package io.sellmair.quantum.internal

import io.sellmair.quantum.test.common.assertJoin
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


class CompletableCycleFutureTest {
    @Test
    fun completed_invokesAfterListener() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.after(action)
        future.completed()
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun completed_invokesCompleteListener() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.completed(action)
        future.completed()
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun rejected_invokesAfterListener() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.after(action)
        future.rejected()
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun rejected_invokesRejectionListener() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.rejected(action)
        future.rejected()
        Assert.assertEquals(1, action.executions)
    }


    @Test
    fun after_invokesListenerWhenAlreadyCompleted() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.completed()
        future.after(action)
        future.rejected(action)
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun completed_invokesListenerWhenAlreadyCompleted() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.completed()
        future.completed(action)
        future.rejected(action)
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun rejected_invokesListenerWhenAlreadyCompleted() {
        val action = TestAction()
        val future = CompletableCycleFuture()
        future.rejected()
        future.completed(action)
        future.rejected(action)
        Assert.assertEquals(1, action.executions)
    }

    @Test
    fun join() = repeat(10) {
        val future = CompletableCycleFuture(Executors.newCachedThreadPool())
        thread { future.completed() }
        future.assertJoin(100L, TimeUnit.MILLISECONDS)
    }
}

private class TestAction : () -> Unit {
    private val atomicCount = AtomicInteger()
    override fun invoke() {
        atomicCount.incrementAndGet()
    }

    val executions get() = atomicCount.get()

}