package io.sellmair.quantum

import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuantumJvmTest {

    private val quant = Quantum(TestState(), history = History())

    @AfterTest
    fun quit() = runBlocking {
        quant.quit()
    }

    @Test
    fun `set from many threads`() {
        val nThreads = 24
        val increments = 10_000

        var threads = arrayOf<Thread>()
        repeat(nThreads) {
            threads += thread {
                runBlocking {
                    repeat(increments) {
                        quant.state.set { copy(value = value + 1) }
                    }
                }
            }
        }
        for (thread in threads) thread.join()

        runBlocking {
            val history = quant.history()
            assertEquals(1 + nThreads * increments, history.count())
            assertEquals(TestState(nThreads * increments), history.last())
        }
    }

}