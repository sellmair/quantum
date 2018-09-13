package io.sellmair.quantum.test.common

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestListener : (BaseQuantumTest.TestState) -> Unit {
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    private val internalStates = mutableListOf<BaseQuantumTest.TestState>()
    val states: List<BaseQuantumTest.TestState>
        get() = lock.withLock {
            mutableListOf(*internalStates.toTypedArray())
        }

    override fun invoke(state: BaseQuantumTest.TestState): Unit = lock.withLock {
        internalStates.add(state)
        condition.signalAll()
    }
}