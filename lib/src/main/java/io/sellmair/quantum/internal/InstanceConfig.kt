package io.sellmair.quantum.internal

import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

interface InstanceConfig {
    val callbackExecutor: Executor
    val executor: Executor
}