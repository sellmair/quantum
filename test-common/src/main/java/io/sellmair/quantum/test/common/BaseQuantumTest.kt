package io.sellmair.quantum.test.common

import android.os.HandlerThread
import android.os.Looper
import io.sellmair.quantum.Quantum
import io.sellmair.quantum.Quitable
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

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


    protected lateinit var executor: Executor

    /**
     * Can be used to receive events and test against.
     */
    protected lateinit var listener: TestListener

    /**
     * Thread that invokes the listener.
     * Needs to be closed within the test.
     */
    protected lateinit var listenerThread: HandlerThread

    @Before
    open fun setup() {
        listener = TestListener()
        listenerThread = HandlerThread("Listener-Thread").also(Thread::start)
        executor = createExecutor()
        quantum = createQuantum(listenerThread.looper)
    }

    @After
    open fun cleanup() {
        quantum.quit().assertJoin(message = "cleanup, quantum")
        listenerThread.quit()
        listenerThread.assertJoin(message = "cleanup, listenerThread")

        val executor = executor
        when (executor) {
            is ExecutorService -> {
                executor.shutdownNow()
                executor.awaitTermination(1L, TimeUnit.SECONDS)
            }

            is Quitable -> {
                executor.quit().assertJoin()
            }
        }
    }

    @Rule
    @JvmField
    val repeat = RepeatRule()

    abstract fun createQuantum(looper: Looper): Quantum<TestState>
    abstract fun createExecutor(): Executor
}