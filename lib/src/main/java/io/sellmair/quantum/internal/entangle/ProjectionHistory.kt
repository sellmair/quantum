package io.sellmair.quantum.internal.entangle

import io.sellmair.quantum.History

internal class ProjectionHistory<Outer, Inner>(
    private val history: History<Outer>,
    private val projection: Projection<Outer, Inner>) : History<Inner> {

    override var enabled: Boolean
        get() = history.enabled
        set(value) {
            history.enabled = value
        }

    override var limit: Int?
        get() = history.limit
        set(value) {
            history.limit = value
        }

    override fun clear() {
        history.clear()
    }

    override fun iterator(): Iterator<Inner> {
        return history.iterator().asSequence()
            .map { projection(it) }
            .iterator()
    }

}