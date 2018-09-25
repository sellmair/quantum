package io.sellmair.quantum.internal.entangle

internal interface Projection<Outer, Inner> {
    operator fun invoke(outer: Outer): Inner
}