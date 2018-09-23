package io.sellmair.quantum.internal.entangle

internal interface Connection<Outer, Inner> {
    operator fun invoke(outer: Outer, inner: Inner): Outer
}

