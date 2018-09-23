package io.sellmair.quantum.internal

import io.sellmair.quantum.Quantum
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

interface InternalQuantum<T> : Quantum<T> {
    val callbackExecutor: Executor
}