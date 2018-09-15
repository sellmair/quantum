package io.sellmair.quantum.internal

import io.sellmair.quantum.Quantum
import io.sellmair.quantum.QuantumConfig

fun Quantum.Companion.configure(configuration: QuantumConfig.() -> Unit) {
    config(configuration)
}