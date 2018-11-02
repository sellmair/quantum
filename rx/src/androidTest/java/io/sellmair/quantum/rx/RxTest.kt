package io.sellmair.quantum.rx

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.Threading
import io.sellmair.quantum.create
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.Repeat
import io.sellmair.quantum.test.common.TestListener
import io.sellmair.quantum.test.common.executor
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

@RunWith(AndroidJUnit4::class)
class RxTest : BaseQuantumTest() {

    override fun createExecutor(): Executor {
        return Executor(Runnable::run)
    }

    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return Quantum.create(
            initial = TestState(),
            threading = Threading.Multi.Pool(
                callbackExecutor = looper.executor()))
    }

    @Repeat(REPETITIONS)
    @Test
    fun rxListener_receivesSameEventsThanRegularListener() {
        val rxListener = TestListener()
        quantum.addStateListener(listener)
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