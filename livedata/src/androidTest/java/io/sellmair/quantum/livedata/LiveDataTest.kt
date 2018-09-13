package io.sellmair.quantum.livedata

import android.os.Handler
import android.os.Looper
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.create
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.TestListener
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class LiveDataTest : BaseQuantumTest() {
    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return Quantum.create(TestState(), looper)
    }


    @Test
    fun liveData_receivesLastUpdate() = repeat(REPETITIONS) {
        setup()

        val liveListener = TestListener()
        quantum.addListener(listener)
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
            Handler(Looper.getMainLooper()).post{
                lock.withLock { condition.signalAll() }
            }

            condition.await()
        }

        assertEquals(TestState(7), listener.states.last())
        assertEquals(TestState(7), liveListener.states.last())

    }

    @Test
    fun liveData_isSameInstanceForSameQuantum() = repeat(REPETITIONS) {
        setup()
        assertEquals(quantum.live, quantum.live)
        assertEquals(quantum.live, quantum.live)
        quantum.quit().join()
    }

    @Test
    fun liveData_isNotSameInstanceForNotSameQuantum() = repeat(REPETITIONS) {
        setup()
        val otherQuantum = Quantum.create(TestState())
        Assert.assertNotEquals(quantum.live, otherQuantum.live)
        Assert.assertNotEquals(quantum.live, otherQuantum.live)
        quantum.quit().join()
        otherQuantum.quit().join()
    }
}