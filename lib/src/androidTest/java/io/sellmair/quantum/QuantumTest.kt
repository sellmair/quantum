package io.sellmair.quantum

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.internal.QuantumImpl
import io.sellmair.quantum.internal.StateSubject
import io.sellmair.quantum.internal.test.BaseQuantumTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@RunWith(AndroidJUnit4::class)
class QuantumTest : BaseQuantumTest() {

    override fun createQuantum(looper: Looper): Quantum<TestState> {
        return QuantumImpl(TestState(), StateSubject(looper))
    }


    /**
     * Will test the the behaviour of the quantum for a single reducer.
     * It will assert that the listener is called exactly twice (initial state + reduced)
     * Also the initial and reduced state are asserted for their integrity.
     */
    @Test
    fun singleReducer() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)

        quantum.setState { copy(revision = 1) }
        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(2, listener.states.size)
        assertEquals(TestState(), listener.states[0])
        assertEquals(TestState(1), listener.states[1])
    }


    /**
     * Will enqueue multiple reducers and assert the integrity
     * of the initial and end state.
     */
    @Test
    fun multipleReducers() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)

        quantum.setState { copy(revision = 1) }
        quantum.setState { copy(revision = 2) }
        quantum.setState { copy(revision = 3) }
        quantum.setState { copy(revision = 4) }
        quantum.setState { copy(revision = 5) }
        quantum.setState { copy(revision = 6) }
        quantum.setState { copy(revision = 7) }

        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(7), listener.states.last())
    }

    /**
     * Will enqueue many reducers from many threads.
     * The integrity of the first as well as the last state are asserted.
     * The order of published states is asserted.
     */
    @Test
    fun multipleReducers_fromRandomThreads() = repeat(REPETITIONS) {

        /*
        Defines how many threads are created at once
         */
        val nThreads = 15

        /*
        Defines how many increments one thread should perform
         */
        val nIncrementsPerThread = 10000


        setup()
        quantum.addListener(listener)


        /*
        Hold a reference to all created threads to join them later
         */
        val threads = mutableListOf<Thread>()

        /*
        Dispatch all those reducers at once
         */
        repeat(nThreads) {
            val thread = thread {
                repeat(nIncrementsPerThread) {
                    quantum.setState { copy(revision = revision + 1) }
                }
            }

            threads.add(thread)
        }

        /*
        Now join on all of those threads to
        wait for all reducers to be enqueued
         */
        for (thread in threads) {
            thread.join()
        }


        /*
        Wait for shutdown
         */
        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()


        /*
        At least the initial and the final reduced state are expected.
         */
        assertTrue(listener.states.size >= 2)


        /*
        We expect each published state to have a higher revision number than the
        previous one. Otherwise the order would be bad!
         */
        listener.states.asSequence()
            .zipWithNext()
            .forEach { adjacentStates ->
                assertTrue(adjacentStates.first.revision < adjacentStates.second.revision)
            }


        /*
        Finally assert the initial and the final reduced state to
        be what we expect
         */
        assertEquals(TestState(), listener.states.first())
        assertEquals(
            TestState(revision = nThreads * nIncrementsPerThread),
            listener.states.last())
    }


    @Test
    fun quit_doesNotExecutePendingReducers() = repeat(REPETITIONS) {

        /*
        Lock and condition used to halt the first reducer.
        This is necessary to have pending reducers stacking up.
         */
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        /* SETUP */
        setup()
        quantum.addListener(listener)


        /*
        Test thread entering the lock
         */
        lock.withLock {

            /*
            Dispatch first reducer
             */
            quantum.setState {
                lock.withLock {

                    /*
                    Signal outer closure that the first reducer is running now
                     */
                    condition.signalAll()

                    /*
                   Wait for test thread to signal this reducer to finish
                    */
                    condition.await()
                    copy(revision = 1)
                }
            }

            /*
            Add pending reducers
             */
            quantum.setState { throw AssertionError("Pending reducer called") }
            quantum.setState { throw AssertionError("Pending reducer called") }
            quantum.setState { throw AssertionError("Pending reducer called") }
            quantum.setState { throw AssertionError("Pending reducer called") }
            quantum.setState { throw AssertionError("Pending reducer called") }
            quantum.setState { throw AssertionError("Pending reducer called") }

            /*
            Wait for the first reducer to run to ensure, that the quantum is not
            quitted  before any reducer gets worked on.
             */
            condition.await()

            /*
            Quit the quantum now
             */
            quantum.quit().also {
                /*
                Let the first reducer finish now
                 */
                condition.signalAll()
            }
        } /*
        Finally join the quantum to die
        */.join()

        listenerThread.quitSafely()
        listenerThread.join()

        /* Expect initial state and reducer */
        assertEquals(2, listener.states.size)
        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(1), listener.states.last())
    }


    @Test
    fun quitSafely_executesAllPendingReducers() {
        /* SETUP */
        setup()
        quantum.addListener(listener)

        quantum.setState { copy(revision = revision + 1) } // 1
        quantum.setState { copy(revision = revision + 1) } // 2
        quantum.setState { copy(revision = revision + 1) } // 3
        quantum.setState { copy(revision = revision + 1) } // 4
        quantum.setState { copy(revision = revision + 1) } // 5
        quantum.setState { copy(revision = revision + 1) } // 6
        quantum.setState { copy(revision = revision + 1) } // 7


        val joinable = quantum.quitSafely()

        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }

        joinable.join()
        listenerThread.quitSafely()
        listenerThread.join()

        Assert.assertEquals(TestState(), listener.states.first())
        Assert.assertEquals(TestState(7), listener.states.last())
    }
}


