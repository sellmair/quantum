package io.sellmair.quantum

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.internal.QuantumImpl
import io.sellmair.quantum.internal.StateSubject
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.TestListener
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

    /**
     * Ensures that no reducers (except the currently running) are executed after
     * [Quantum.quit] was called.
     */
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


    /**
     * Ensures that all currently pending reducers are executed after
     * [Quantum.quitSafely] was called
     */
    @Test
    fun quitSafely_executesAllPendingReducers() = repeat(REPETITIONS) {
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

        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(7), listener.states.last())
    }

    @Test
    fun singleAction_receivesLatestState() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)
        quantum.setState { copy(revision = 1) }

        val stateListener = TestListener()
        quantum.withState(stateListener)
        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(1, stateListener.states.size)
        assertEquals(TestState(1), stateListener.states.first())
    }


    /**
     * Test will check if a single action is only called once.
     * This test will enforce that two cycles are performed by the quantum.
     */
    @Test
    fun singleAction_isCalledOnce() = repeat(REPETITIONS) {
        setup()

        /*
        Lock and condition to enable control over precise timing over quantum thread and
        test thread
         */
        val lock = ReentrantLock()
        val firstReducerRunning = lock.newCondition()

        /*
        Whole test is captured by the lock
         */
        lock.withLock {
            quantum.addListener(listener)
            quantum.setState {
                lock.withLock {
                    /*
                    Notify that the first reducer is now running
                     */
                    firstReducerRunning.signalAll()
                    copy(revision = 1)

                }
            }

            val stateListener = TestListener()
            quantum.withState(stateListener)

            /*
            Wait for the first reducer to run
             */
            firstReducerRunning.await()

            /*
            Dispatch multiple reducer afterwards.
            This has to be handled in another cycle, because the first cycle is currently running
             */
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }
            quantum.setState { copy(revision = revision + 1) }


            /*
            Quit all
             */
            quantum.quitSafely().join()
            listenerThread.quitSafely()
            listenerThread.join()

            /*
            Assert that the withState is only called once
            and that withState is called with the correct state
             */
            assertEquals(1, stateListener.states.size)
            assertEquals(TestState(1), stateListener.states.first())
        }
    }

    /**
     * Ensures that a single action receives the initial state of the quantum
     * if no reducer was enqueued yet
     */
    @Test
    fun singleAction_receivesInitialState() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)

        val stateListener = TestListener()
        quantum.withState(stateListener)
        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(1, stateListener.states.size)
        assertEquals(TestState(0), stateListener.states.first())
    }

    /**
     * Ensures that a single action receives the the latest state of the quantum
     * when there are no pending reducers
     */
    @Test
    fun singleAction_receivesLatestState_noPendingReducers() = repeat(REPETITIONS) {
        setup()

        /*
        Lock used to handle timing between the quantum thread, listener thread and test thread
         */
        val lock = ReentrantLock()
        val firstCycle = lock.newCondition()

        /*
        Whole test acquires lock
         */
        lock.withLock {
            quantum.addListener(listener)

            /*
            Set state with revision 1
             */
            quantum.setState { copy(revision = 1) }
            quantum.addListener {
                /*
                Notify that the first cycle was completed!
                 */
                if (it.revision != 1) return@addListener
                lock.withLock {
                    firstCycle.signalAll()
                }
            }

            /*
            Wait for first cycle to complete
             */
            firstCycle.await()

            /*
            Dispatch an action
             */
            val stateListener = TestListener()
            quantum.withState(stateListener)

            /*
            Quit all
             */
            quantum.quitSafely().join()
            listenerThread.quitSafely()
            listenerThread.join()

            /*
            Assert that action got latest state
             */
            assertEquals(1, stateListener.states.size)
            assertEquals(TestState(1), stateListener.states.first())
        }
    }

    /**
     * Ensures that multiple actions will receive the latest state
     */
    @Test
    fun multipleActions_receiveLatestState() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)
        quantum.setState { copy(revision = 1) }

        val stateListener = TestListener()

        repeat(REPETITIONS) {
            quantum.withState(stateListener)
        }

        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(REPETITIONS, stateListener.states.size)
        for (state in stateListener.states) {
            assertEquals(TestState(1), state)
        }
    }


    @Test
    fun addListener_receivesCurrentState() = repeat(REPETITIONS) {
        setup()
        quantum.addListener(listener)
        quantum.quitSafely().join()
        listenerThread.quitSafely()
        listenerThread.join()

        assertEquals(1, listener.states.size)
        assertEquals(TestState(), listener.states.first())

    }
}


