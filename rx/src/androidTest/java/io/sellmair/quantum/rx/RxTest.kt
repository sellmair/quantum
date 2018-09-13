package io.sellmair.quantum.rx

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.internal.test.TestListener
import io.sellmair.quantum.create
import io.sellmair.quantum.internal.test.BaseQuantumTest
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RxTest : BaseQuantumTest() {
    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return Quantum.create(TestState(), looper)
    }

    @Test
    fun rxListener_receivesSameEventsThanRegularListener() {
        setup()

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