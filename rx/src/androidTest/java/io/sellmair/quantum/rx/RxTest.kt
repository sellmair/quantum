package io.sellmair.quantum.rx

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.create
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.TestListener
import io.sellmair.quantum.test.common.asExecutor
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RxTest : BaseQuantumTest() {
    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return Quantum.create(TestState(), callback = looper.asExecutor())
    }

    @Test
    fun rxListener_receivesSameEventsThanRegularListener() = test {
        val rxListener = TestListener()
        quantum.addListener(listener)
        quantum.rx.subscribe(rxListener)

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

        assertArrayEquals(
            listener.states.toTypedArray(),
            rxListener.states.toTypedArray())

    }
}