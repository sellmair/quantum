package io.sellmair.quantum

import io.sellmair.quantum.internal.config

/*
################################################################################################
PUBLIC API
################################################################################################
*/
/**
 * Enables global configuration of Quantum.
 * This also allows to override the default values for [Quantum.Companion.create]
 * @see QuantumConfig
 */
fun Quantum.Companion.configure(configuration: QuantumConfig.() -> Unit) {
    config(configuration)
}