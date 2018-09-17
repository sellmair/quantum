package io.sellmair.quantum.livedata

import android.os.Handler
import android.os.Looper
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.create
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.Repeat
import io.sellmair.quantum.test.common.TestListener
import io.sellmair.quantum.test.common.executor
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LiveDataTest : BaseQuantumTest() {
    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return Quantum.create(TestState(), callbackExecutor = looper.executor())
    }


    @Repeat(REPETITIONS)
    @Test
    fun liveData_receivesLastUpdate() {

        val liveListener = TestListener()
        quantum.addStateListener(listener)
        quantum.live.observeForever { state ->
            if (state != null) liveListener(state)
        }

        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }

        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        val lock = ReentrantLock()
        val condition = lock.newCondition()

        /**
         * Wait for main-thread idle
         */
        lock.withLock {
            Handler(Looper.getMainLooper()).post {
                lock.withLock { condition.signalAll() }
            }

            condition.await()
        }

        assertEquals(TestState(7), listener.states.last())
        assertEquals(TestState(7), liveListener.states.last())

    }

    @Repeat(REPETITIONS)
    @Test
    fun liveData_isSameInstanceForSameQuantum() {
        assertEquals(quantum.live, quantum.live)
        assertEquals(quantum.live, quantum.live)
        quantum.quit().join()
    }

    @Repeat(REPETITIONS)
    @Test
    fun liveData_isNotSameInstanceForNotSameQuantum() {
        val otherQuantum = Quantum.create(TestState())
        Assert.assertNotEquals(quantum.live, otherQuantum.live)
        Assert.assertNotEquals(quantum.live, otherQuantum.live)
        quantum.quit().join()
        otherQuantum.quit().join()
    }
}