package io.sellmair.quantum

import io.sellmair.quantum.internal.config

/*
################################################################################################
PUBLIC API
################################################################################################
*/

fun Quantum.Companion.configure(configuration: QuantumConfig.() -> Unit) {
    config(configuration)
}