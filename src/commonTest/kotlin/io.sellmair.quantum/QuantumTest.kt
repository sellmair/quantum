package io.sellmair.quantum


import kotlinx.coroutines.*
import kotlinx.coroutines.channels.toList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuantumTest {

    private val quant = Quantum(initial = TestState(value = 0), history = History())

    @AfterTest
    fun quit() = runBlocking {
        quant.quit()
    }

    @Test
    fun `set once`() = runBlocking {
        quant.enterBlocking {
            set { state.copy(value = state.value + 1) }
            assertEquals(TestState(1), state)
        }

        val history = quant.history().toList()
        assertEquals(2, history.count())
        assertEquals(TestState(0), history[0])
        assertEquals(TestState(1), history[1])

    }

    @Test
    fun `set increase count from many coroutines`() = runBlocking {
        val increments = 10_000

        var jobs = arrayOf<Job>()
        repeat(increments) {
            jobs += launch(Dispatchers.Default) {
                quant.set { state.copy(value = state.value + 1) }
            }
        }
        for (job in jobs) job.join()

        val history = quant.history()
        assertEquals(1 + increments, history.count())
        assertEquals(TestState(0), history.first())
        assertEquals(increments, history.last().value)
    }

    @Test
    fun `set increase count from many coroutines with bulk`() = runBlocking {
        val coroutines = 100
        val increments = 100

        var jobs = arrayOf<Job>()
        repeat(coroutines) {
            jobs += launch(Dispatchers.Default) {
                repeat(increments) {
                    quant.set { state.copy(value = state.value + 1) }
                }
            }
        }
        for (job in jobs) job.join()

        val history = quant.history()
        assertEquals(1 + increments * coroutines, history.count())
        assertEquals(TestState(increments * coroutines), history.last())
    }


    @Test
    fun `states receives updates in order and last state`() = runBlocking {
        val increments = 10_000

        val statesAsync = async { quant.states.toList() }

        repeat(increments) { quant.set { state.copy(value = state.value + 1) } }
        quant.quit()

        val history = quant.history()
        val states = statesAsync.await()

        assertEquals(1 + increments, history.count())
        assertEquals(TestState(increments), states.last())
        for ((first, second) in states.zipWithNext()) assertTrue { first.value < second.value }
    }

    @Test
    fun `states with multiple subscription receive last state`() = runBlocking {
        val increments = 10_000

        val states1Async = async { quant.states.toList() }
        val states2Async = async { quant.states.toList() }

        repeat(increments) { quant.set { state.copy(value = state.value + 1) } }
        quant.quit()

        val states1 = states1Async.await()
        val states2 = states2Async.await()

        assertEquals(TestState(increments), states1.last())
        assertEquals(TestState(increments), states2.last())
    }
}

