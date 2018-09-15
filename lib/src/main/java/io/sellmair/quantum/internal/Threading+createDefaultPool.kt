package io.sellmair.quantum.internal

import io.sellmair.quantum.Threading
import java.util.concurrent.ExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun Threading.Companion.createDefaultPool(): ExecutorService {
    return ThreadPoolExecutor(
        1, // core size
        Integer.MAX_VALUE, // max size
        5L, TimeUnit.SECONDS, // keep-alive
        SynchronousQueue())
}