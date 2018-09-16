package io.sellmair.quantum.test.common

import android.os.HandlerThread
import android.os.Looper
import io.sellmair.quantum.Quantum

abstract class BaseQuantumTest {

    companion object {
        /**
         * Tests in this class are executed multiple times to ensure coverage for
         * race conditions and other strange behaviour.
         */
        const val REPETITIONS = 100
    }

    /**
     * The state used to test the quantum against.
     */
    data class TestState(val revision: Int = 0, val payload: Any? = null)

    /**
     * Quantum instance to test
     */
    protected lateinit var quantum: Quantum<TestState>

    /**
     * Can be used to receive events and test against.
     */
    protected lateinit var listener: TestListener

    /**
     * Thread that invokes the listener.
     * Needs to be closed within the test.
     */
    protected lateinit var listenerThread: HandlerThread

    open fun setup() {
        listener = TestListener()
        listenerThread = HandlerThread("Listener-Thread").also(Thread::start)
        quantum = createQuantum(listenerThread.looper)
    }

    open fun cleanup() {
        quantum.quit().join()
        listenerThread.quit()
        listenerThread.join()
    }

    fun test(repetitions: Int = REPETITIONS, block: () -> Unit) {
        repeat(repetitions) {
            setup()
            try {
                block()
            } finally {
                cleanup()
            }
        }
    }

    abstract fun createQuantum(looper: Looper): Quantum<TestState>
}