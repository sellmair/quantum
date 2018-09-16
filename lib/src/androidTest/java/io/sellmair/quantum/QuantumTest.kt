package io.sellmair.quantum

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.internal.*
import io.sellmair.quantum.test.common.BaseQuantumTest
import io.sellmair.quantum.test.common.TestListener
import io.sellmair.quantum.test.common.TestRunnable
import io.sellmair.quantum.test.common.assertJoin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@RunWith(AndroidJUnit4::class)
abstract class QuantumTest : BaseQuantumTest() {

    private lateinit var executor: Executor

    final override fun createQuantum(looper: Looper): Quantum<TestState> {
        executor = createExecutor()
        return ExecutorQuantum(
            initial = TestState(),
            stateSubject = StateSubject(looper.asExecutor()),
            quittedSubject = QuitedSubject(looper.asExecutor()),
            executor = executor)
    }

    override fun cleanup() {
        super.cleanup()
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


    abstract fun createExecutor(): Executor


    /**
     * Will test the the behaviour of the quantum for a single reducer.
     * It will assert that the listener is called exactly twice (initial state + reduced)
     * Also the initial and reduced state are asserted for their integrity.
     */
    @Test
    fun singleReducer() = test {
        quantum.addStateListener(listener)

        quantum.setState { copy(revision = 1) }
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(2, listener.states.size)
        assertEquals(TestState(), listener.states[0])
        assertEquals(TestState(1), listener.states[1])
    }


    /**
     * Will enqueue multiple reducers and assert the integrity
     * of the initial and end state.
     */
    @Test
    fun multipleReducers() = test {
        quantum.addStateListener(listener)

        quantum.setState { copy(revision = 1) }
        quantum.setState { copy(revision = 2) }
        quantum.setState { copy(revision = 3) }
        quantum.setState { copy(revision = 4) }
        quantum.setState { copy(revision = 5) }
        quantum.setState { copy(revision = 6) }
        quantum.setState { copy(revision = 7) }

        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(7), listener.states.last())
    }

    /**
     * Will enqueue many reducers from many threads.
     * The integrity of the first as well as the last state are asserted.
     * The order of published states is asserted.
     */
    @Test
    fun multipleReducers_fromRandomThreads() = test(repetitions = REPETITIONS / 50) {


        /*
        Defines how many threads are created at once
         */
        val nThreads = 15

        /*
        Defines how many increments one thread should perform
         */
        val nIncrementsPerThread = 10000

        quantum.addStateListener(listener)


        /*
        Hold a reference to all created threads to assertJoin them later
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
        Now assertJoin on all of those threads to
        wait for all reducers to be enqueued
         */
        for (thread in threads) {
            thread.assertJoin()
        }


        /*
        Wait for shutdown
         */
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()


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
    open fun quit_doesNotExecutePendingReducers() = test {

        /*
        Lock and condition used to halt the first reducer.
        This is necessary to have pending reducers stacking up.
         */
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        /* SETUP */
        quantum.addStateListener(listener)


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
        Finally assertJoin the quantum to die
        */.assertJoin()

        listenerThread.quitSafely()
        listenerThread.assertJoin()

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
    fun quitSafely_executesAllPendingReducers() = test {
        /* SETUP */
        quantum.addStateListener(listener)

        quantum.setState { copy(revision = revision + 1) } // 1
        quantum.setState { copy(revision = revision + 1) } // 2
        quantum.setState { copy(revision = revision + 1) } // 3
        quantum.setState { copy(revision = revision + 1) } // 4
        quantum.setState { copy(revision = revision + 1) } // 5
        quantum.setState { copy(revision = revision + 1) } // 6
        quantum.setState { copy(revision = revision + 1) } // 7


        val assertJoinable = quantum.quitSafely()

        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }
        quantum.setState { copy(revision = revision + 1) }

        assertJoinable.assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(7), listener.states.last())
    }

    @Test
    fun singleAction_receivesLatestState() = test {
        quantum.addStateListener(listener)
        quantum.setState { copy(revision = 1) }

        val stateListener = TestListener()
        quantum.withState(stateListener)
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, stateListener.states.size)
        assertEquals(TestState(1), stateListener.states.first())
    }


    /**
     * Test will check if a single action is only called once.
     * This test will enforce that two cycles are performed by the quantum.
     */
    @Test
    open fun singleAction_isCalledOnce() = test {

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
            quantum.addStateListener(listener)
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
            quantum.quitSafely().assertJoin()
            listenerThread.quitSafely()
            listenerThread.assertJoin()

            /*
            Assert that the withState is only called once
             */
            assertEquals(1, stateListener.states.size)
        }
    }

    /**
     * Ensures that a single action receives the initial state of the quantum
     * if no reducer was enqueued yet
     */
    @Test
    fun singleAction_receivesInitialState() = test {
        quantum.addStateListener(listener)

        val stateListener = TestListener()
        quantum.withState(stateListener)
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, stateListener.states.size)
        assertEquals(TestState(0), stateListener.states.first())
    }

    /**
     * Ensures that a single action receives the the latest state of the quantum
     * when there are no pending reducers
     */
    @Test
    open fun singleAction_receivesLatestState_noPendingReducers() = test {
        /*
        Lock used to handle timing between the quantum thread, listener thread and test thread
         */
        val lock = ReentrantLock()
        val firstCycle = lock.newCondition()

        /*
        Whole test acquires lock
         */
        lock.withLock {
            quantum.addStateListener(listener)

            /*
            Set state with revision 1
             */
            quantum.setState { copy(revision = 1) }
            quantum.addStateListener {
                /*
                Notify that the first cycle was completed!
                 */
                if (it.revision != 1) return@addStateListener
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
            quantum.quitSafely().assertJoin()
            listenerThread.quitSafely()
            listenerThread.assertJoin()

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
    fun multipleActions_receiveLatestState() = test {
        quantum.addStateListener(listener)
        quantum.setState { copy(revision = 1) }

        val stateListener = TestListener()

        repeat(REPETITIONS) {
            quantum.withState(stateListener)
        }

        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(REPETITIONS, stateListener.states.size)
        for (state in stateListener.states) {
            assertEquals(TestState(1), state)
        }
    }


    @Test
    fun addListener_receivesCurrentState() = test {
        quantum.addStateListener(listener)
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, listener.states.size)
        assertEquals(TestState(), listener.states.first())
    }


    @Test
    fun history_containsAllStates() = test {
        quantum.history.enabled = true

        repeat(REPETITIONS) {
            quantum.setState { copy(revision = revision + 1) }
        }

        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()


        assertEquals(REPETITIONS + 1, quantum.history.count())
        assertEquals(TestState(0), quantum.history.first())
        assertEquals(TestState(REPETITIONS), quantum.history.last())

        quantum.history.zipWithNext { first, second ->
            assertEquals(first.revision + 1, second.revision)
        }
    }

    @Test
    fun history_isEmptyWhenDisabled() = test {
        quantum.history.enabled = false

        repeat(REPETITIONS) {
            quantum.setState { copy(revision = revision + 1) }
        }

        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()


        assertEquals(0, quantum.history.count())
    }

    @Test
    fun history_withLimit() = test {
        val limit = REPETITIONS / 2
        quantum.history.enabled = true
        quantum.history.limit = limit


        repeat(REPETITIONS) {
            quantum.setState { copy(revision = revision + 1) }
        }

        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()


        assertEquals(limit, quantum.history.count())
        assertEquals(TestState(), quantum.history.first())
        assertEquals(TestState(REPETITIONS), quantum.history.last())

        quantum.history.asSequence()
            .drop(1)
            .zipWithNext { first, second ->
                assertEquals(first.revision + 1, second.revision)
            }
    }


    @Test
    fun quit_withoutReducers_releasesassertJoin() = test {
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()
    }


    @Test
    fun quittedObservable_isCalledWhenAlreadyQuitted() = test {
        val runnable = TestRunnable()
        quantum.quitSafely().assertJoin()
        quantum.addQuittedListener(runnable)
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, runnable.executions)
    }

    @Test
    fun quittedObservable_isCalledWhenQuitted() = test {
        val runnable = TestRunnable()
        quantum.addQuittedListener(runnable)
        quantum.setState { copy(revision = 1) }
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()
        assertEquals(1, runnable.executions)
    }

}


@RunWith(AndroidJUnit4::class)
class SingleThreadExecutorQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return Executors.newSingleThreadExecutor()
    }

    @Test
    override fun singleAction_receivesLatestState_noPendingReducers() {
        super.singleAction_receivesLatestState_noPendingReducers()
    }
}

@RunWith(AndroidJUnit4::class)
class FixedThreadPoolQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return Executors.newFixedThreadPool(12)
    }
}

@RunWith(AndroidJUnit4::class)
class CachedThreadPoolQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return Executors.newCachedThreadPool()
    }
}

@RunWith(AndroidJUnit4::class)
class WorkStealingPoolQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return Executors.newWorkStealingPool()
    }
}

@RunWith(AndroidJUnit4::class)
class SyncQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return Executor(Runnable::run)
    }

    @Test
    override fun quit_doesNotExecutePendingReducers() = test {
        quantum.addStateListener(listener)
        quantum.setState { copy(revision = 1) }
        quantum.quit()
        quantum.setState { copy(revision = 2) }

        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(2, listener.states.size)
        assertEquals(TestState(), listener.states.first())
        assertEquals(TestState(1), listener.states.last())
    }

    @Test
    override fun singleAction_isCalledOnce() = test {
        quantum.setState { copy(revision = 1) }
        quantum.withState(listener)
        quantum.setState { copy(revision = 2) }
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()
        assertEquals(1, listener.states.size)
        assertEquals(TestState(1), listener.states.first())
    }
}


@RunWith(AndroidJUnit4::class)
class CustomSingleThreadExecutorQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return SingleThreadExecutor()
    }
}

@RunWith(AndroidJUnit4::class)
class DefaultThreadPoolExecutorQuantumTest : QuantumTest() {

    private lateinit var executor: ExecutorService

    override fun createExecutor(): Executor {
        return executor
    }

    override fun setup() {
        executor = Threading.createDefaultPool()
        super.setup()
    }

    override fun cleanup() {
        super.cleanup()
        executor.shutdownNow()
        executor.awaitTermination(1L, TimeUnit.SECONDS)
    }

}