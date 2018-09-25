package io.sellmair.quantum

import android.os.Looper
import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.internal.ExecutorQuantum
import io.sellmair.quantum.internal.SingleThreadExecutor
import io.sellmair.quantum.internal.asAwait
import io.sellmair.quantum.internal.createDefaultPool
import io.sellmair.quantum.test.common.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

@RunWith(AndroidJUnit4::class)
abstract class QuantumTest : BaseQuantumTest() {

    protected open lateinit var executor: Executor

    override fun createQuantum(looper: Looper): Quantum<TestState> {
        executor = createExecutor()
        return ExecutorQuantum(
            initial = TestState(),
            callbackExecutor = looper.executor(),
            executor = executor)
    }

    @After
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
    @Repeat(REPETITIONS)
    @Test
    fun singleReducer() {
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
    @Repeat(REPETITIONS)
    @Test
    fun multipleReducers() {
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
    open fun multipleReducers_fromRandomThreads() {

        val lock = ReentrantLock()
        val enqueueFinished = lock.newCondition()

        /*
        Defines how many threads are created at once
         */
        val nThreads = 12

        /*
        Defines how many increments one thread should perform
         */
        val nIncrementsPerThread = 800

        quantum.addStateListener(listener)


        val threadsFinished = AtomicInteger(0)


        /*
        Dispatch all those reducers at once
         */
        lock.withLock {
            repeat(nThreads) { _ ->
                thread {
                    repeat(nIncrementsPerThread) { _ ->
                        quantum.setState { copy(revision = revision + 1) }
                    }

                    if (threadsFinished.incrementAndGet() == nThreads) {
                        lock.withLock { enqueueFinished.signalAll() }
                    }
                }


            }

            if (!enqueueFinished.asAwait(1L, TimeUnit.MINUTES).await()) {
                fail("Failed to wait for enqueuing reducers. Finished: ${threadsFinished.get()}")
            }
        }


        /*
        Wait for shutdown
         */
        quantum.quitSafely().assertJoin(5L, TimeUnit.MINUTES, message = "quantum")
        listenerThread.quitSafely()
        listenerThread.assertJoin(message = "listener thread")


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
    @Repeat(REPETITIONS)
    open fun quit_doesNotExecutePendingReducers() {

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
    @Repeat(REPETITIONS)
    fun quitSafely_executesAllPendingReducers() {
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
    @Repeat(REPETITIONS)
    fun singleAction_receivesLatestState() {
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
    @Repeat(REPETITIONS)
    open fun singleAction_isCalledOnce() {

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
    fun singleAction_receivesInitialState() {
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
    @Repeat(REPETITIONS)
    open fun singleAction_receivesLatestState_noPendingReducers() {
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
    @Repeat(REPETITIONS)
    fun multipleActions_receiveLatestState() {
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
    @Repeat(REPETITIONS)
    fun addListener_receivesCurrentState() {
        quantum.addStateListener(listener)
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, listener.states.size)
        assertEquals(TestState(), listener.states.first())
    }


    @Test
    @Repeat(REPETITIONS)
    fun history_containsAllStates() {
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
    @Repeat(REPETITIONS)
    fun history_isEmptyWhenDisabled() {
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
    @Repeat(REPETITIONS)
    fun history_withLimit() {
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
    @Repeat(REPETITIONS)
    fun quit_withoutReducers_releasesJoin() {
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()
    }

    @Test
    @Repeat(REPETITIONS)
    open fun quit_withBlockedReducer_joinWithTimeout_returnsFalse() {
        val lock = ReentrantLock()

        lock.withLock {
            quantum.setState {
                lock.withLock {
                    copy(revision = 1)
                }
            }


            val joined = quantum.quitSafely().join(5L, TimeUnit.MILLISECONDS)
            assertFalse(joined)
        }
    }

    @Test
    @Repeat(REPETITIONS)
    fun quit_joinWithTimeout_returnsTrue() {
        quantum.setState { copy(revision = 1) }
        val joined = quantum.quitSafely().join(100L, TimeUnit.MILLISECONDS)
        assertTrue(joined)
    }


    @Test
    @Repeat(REPETITIONS)
    fun quittedObservable_isCalledWhenAlreadyQuitted() {
        val runnable = TestRunnable()
        quantum.quitSafely().assertJoin()
        quantum.addQuittedListener(runnable)
        listenerThread.quitSafely()
        listenerThread.assertJoin()

        assertEquals(1, runnable.executions)
    }

    @Test
    @Repeat(REPETITIONS)
    fun quittedObservable_isCalledWhenQuitted() {
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

    @Test
    override fun multipleReducers_fromRandomThreads() {
        super.multipleReducers_fromRandomThreads()
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
    override fun quit_doesNotExecutePendingReducers() {
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
    override fun singleAction_isCalledOnce() {
        quantum.setState { copy(revision = 1) }
        quantum.withState(listener)
        quantum.setState { copy(revision = 2) }
        quantum.quitSafely().assertJoin()
        listenerThread.quitSafely()
        listenerThread.assertJoin()
        assertEquals(1, listener.states.size)
        assertEquals(TestState(1), listener.states.first())
    }

    /**
     * Disable test for sync version because of deadlocking
     */
    @Test
    override fun quit_withBlockedReducer_joinWithTimeout_returnsFalse() = Unit

}


@RunWith(AndroidJUnit4::class)
class CustomSingleThreadExecutorQuantumTest : QuantumTest() {
    override fun createExecutor(): Executor {
        return SingleThreadExecutor()
    }
}

@RunWith(AndroidJUnit4::class)
class DefaultThreadPoolExecutorQuantumTest : QuantumTest() {

    private lateinit var executorService: ExecutorService

    override fun createExecutor(): Executor {
        return executorService
    }

    override fun setup() {
        executorService = Threading.createDefaultPool()
        super.setup()
    }

    override fun cleanup() {
        super.cleanup()
        executorService.shutdownNow()
        executorService.awaitTermination(1L, TimeUnit.SECONDS)
    }

}


@RunWith(AndroidJUnit4::class)
class EntangledQuantumTest : QuantumTest() {

    data class ParentState(
        val parentRevision: Int = 0,
        val child: TestState = TestState())


    private lateinit var parentQuantum: Quantum<ParentState>

    override fun createExecutor(): Executor {
        return Executors.newCachedThreadPool()
    }

    override fun createQuantum(looper: Looper): Quantum<TestState> {
        executor = createExecutor()

        parentQuantum = ExecutorQuantum(
            initial = ParentState(),
            callbackExecutor = looper.executor(),
            executor = executor)

        return parentQuantum
            .map(ParentState::child)
            .connect { parent, child -> parent.copy(child = child) }

    }

    @Before
    override fun setup() {
        super.setup()
        Quantum.configure { logging.level = LogLevel.DEBUG }
    }

    @After
    fun cleanupParent() {
        parentQuantum.quitSafely().assertJoin()
    }
}