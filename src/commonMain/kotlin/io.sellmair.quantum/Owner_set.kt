package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl

@QuantumDsl
suspend inline fun <T> Owner<T>.set(reducer: Access<T>.() -> T) {
    state.setWithAccess(reducer)
}