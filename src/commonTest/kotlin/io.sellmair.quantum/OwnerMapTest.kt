package io.sellmair.quantum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class OwnerMapTest {
    data class ChildChildState(val value: Int)
    data class ChildState(val value: Int, val child: ChildChildState = ChildChildState(0))
    private data class State(val value: Int, val child: ChildState)

    private val master = createTestOwner(State(0, ChildState(0, ChildChildState(0))))

    private val projection = master.map(State::child).connect { copy(child = it) }

    private val projection2 = projection.map(ChildState::child).connect { copy(child = it) }


    @Test
    fun `projection -- set -- is reflected in master history`() = runBlocking {
        projection.state.set { copy(value = 1) }

        val history = master.history().toList()
        assertEquals(2, history.count())
        assertEquals(State(0, ChildState(0)), history[0])
        assertEquals(State(0, ChildState(1)), history[1])
    }

    @Test
    fun `projection -- set -- from multiple coroutines`() = runBlocking {
        val increments = 100
        val coroutines = 50

        var jobs = arrayOf<Job>()

        repeat(coroutines) {
            jobs += launch(Dispatchers.Default) {
                repeat(increments) {
                    projection.state.set { copy(value = value + 1) }
                }
            }
        }

        for (job in jobs) job.join()

        val history = master.history().toList()
        assertEquals(1 + increments * coroutines, history.size)
        assertEquals(State(0, ChildState(increments * coroutines)), history.last())
    }


    @Test
    fun `projection -- set -- from multiple coroutines and competition`() = runBlocking {
        val increments = 100
        val coroutines = 50

        var jobs = arrayOf<Job>()

        repeat(coroutines) {
            jobs += launch(Dispatchers.Default) {
                repeat(increments) {
                    projection.state.set { copy(value = value + 1) }
                }
            }
        }

        repeat(coroutines) {
            jobs += launch(Dispatchers.Default) {
                repeat(increments) {
                    master.state.set { copy(value = value + 1) }
                }
            }
        }

        for (job in jobs) job.join()

        val history = master.history().toList()
        assertEquals(1 + 2 * coroutines * increments, history.size)
        assertEquals(State(coroutines * increments, ChildState(coroutines * increments)), history.last())
    }


    @Test
    fun `projection2 -- set -- is reflected in master history`() = runBlocking {
        projection2.set { ChildChildState(1) }

        val history = master.history().toList()
        assertEquals(2, history.size)
        assertEquals(State(0, ChildState(0, ChildChildState(1))), history.last())
    }


    @Test
    fun `projection projection2 and master -- set -- is reflected in master history`() = runBlocking {
        projection2.state.set { copy(value = 1) }
        master.state.set { copy(value = 1) }
        projection.state.set { copy(value = 1) }

        val history = master.history().toList()
        assertEquals(4, history.size)
        assertEquals(State(0, ChildState(0, ChildChildState(0))), history[0])
        assertEquals(State(0, ChildState(0, ChildChildState(1))), history[1])
        assertEquals(State(1, ChildState(0, ChildChildState(1))), history[2])
        assertEquals(State(1, ChildState(1, ChildChildState(1))), history[3])
    }
}