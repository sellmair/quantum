package io.sellmair.quantum

import android.support.test.runner.AndroidJUnit4
import io.sellmair.quantum.internal.SingleThreadExecutor
import io.sellmair.quantum.test.common.TestRunnable
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@RunWith(AndroidJUnit4::class)
class SingleThreadExecutorTest {
    @Test
    fun immediately_quit() = repeat(100) {
        val executor = SingleThreadExecutor()
        executor.quit().join()
    }

    @Test
    fun immediately_quitSafely() = repeat(100) {
        val executor = SingleThreadExecutor()
        executor.quitSafely().join()
    }

    @Test
    fun executeRunnable() = repeat(100) {
        val executor = SingleThreadExecutor()
        val runnable = TestRunnable()
        executor.execute(runnable)
        executor.quitSafely().join()
        Assert.assertEquals(1, runnable.executions)
    }

    @Test
    fun executeRunnableMultipleTimes() = repeat(100) {
        val executor = SingleThreadExecutor()
        val runnable = TestRunnable()
        repeat(12) { executor.execute(runnable) }
        executor.quitSafely().join()
        Assert.assertEquals(12, runnable.executions)
    }

    @Test
    fun quit_doesNotExecutePendingRunnables() = repeat(100) {
        val executor = SingleThreadExecutor()

        /*
        Lock and condition are used to perfectly time the .quit call
         */
        val lock = ReentrantLock()
        val condition = lock.newCondition()

        val beforeQuitRunnable = TestRunnable()
        val afterQuitRunnable = TestRunnable()


        val joinable = lock.withLock {

            /*
            Dispatch all runnables before .quit is called
             */
            repeat(100) { executor.execute(beforeQuitRunnable) }

            /*
            Runnable that is used to perfectly time the .quit call
             */
            executor.execute {
                /*
                Enter the lock (this can only happen when the test thread is waiting
                 */
                lock.withLock {
                    /*
                    Wake up the test thread and wait for .quit to be called
                     */
                    condition.signalAll()
                    condition.await()
                }
            }
            /*
            Enqueue all runnables that are expected to NOT be executed
             */
            repeat(100) { executor.execute(afterQuitRunnable) }

            /*
            Wait for the signal of the 101'st runnable
             */
            condition.await()

            /*
            Quit the executor while runnable 101 is waiting
             */
            val joinable = executor.quit()

            /*
            Tell runnable 101 to wake up again
             */
            condition.signalAll()
            joinable
        }

        joinable.join()
        Assert.assertEquals(100, beforeQuitRunnable.executions)
        Assert.assertEquals(0, afterQuitRunnable.executions)
    }

}

