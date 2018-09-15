package io.sellmair.quantum.test.common

import java.util.concurrent.atomic.AtomicInteger

class TestRunnable : Runnable, () -> Unit {


    private val counter = AtomicInteger()

    override fun invoke() {
        run()
    }

    override fun run() {
        counter.incrementAndGet()
    }

    val executions get() = counter.get()
}