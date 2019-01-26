package io.sellmair.quantum

import io.sellmair.quantum.internal.QuantumDsl

/*
################################################################################################
PUBLIC API
################################################################################################
*/

@QuantumDsl
interface Access<T> {
    val state: T
}