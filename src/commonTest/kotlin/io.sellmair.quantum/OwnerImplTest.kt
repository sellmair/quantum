package io.sellmair.quantum

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.toList
import kotlin.test.*
import kotlin.test.Test

class OwnerImplTest {

    private val owner = createTestOwner(TestState(0))

    @AfterTest
    fun quit() = runBlocking {
        owner.quit()
    }

    @Test
    fun `set once`() = runBlocking {
        owner.state.set { copy(value = value + 1) }
        assertEquals(TestState(1), owner.state.value)


        val history = owner.history().toList()
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
                owner.state.set { copy(value = value + 1) }
            }
        }
        for (job in jobs) job.join()

        val history = owner.history()
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
                    owner.state.set { copy(value = value + 1) }
                }
            }
        }
        for (job in jobs) job.join()

        val history = owner.history()
        assertEquals(1 + increments * coroutines, history.count())
        assertEquals(TestState(increments * coroutines), history.last())
    }


    @Test
    fun `states receives updates in order and last state`() = runBlocking {
        val increments = 10_000

        val statesAsync = async { owner.states.toList() }

        repeat(increments) { owner.state.set { copy(value = value + 1) } }
        yield()
        owner.quit()

        val history = owner.history()
        val states = statesAsync.await()

        assertEquals(1 + increments, history.count())
        assertEquals(TestState(increments), states.last())
        for ((first, second) in states.zipWithNext()) assertTrue { first.value < second.value }
    }

    @Test
    fun `states with multiple subscription receive last state`() = runBlocking {
        val increments = 1_000

        val states1Async = async { owner.states.toList() }
        val states2Async = async { owner.states.toList() }

        repeat(increments) { owner.state.set { copy(value = value + 1) } }
        yield()
        owner.quit()

        val states1 = states1Async.await()
        val states2 = states2Async.await()

        assertEquals(TestState(increments), states1.last())
        assertEquals(TestState(increments), states2.last())
    }


    @Test
    fun `set inline return`() = runBlocking {
        run {
            owner.set {
                return@run
            }

            fail("Did not return")
        }

        val history = owner.history().toList()
        assertEquals(1, history.size)
    }
}

