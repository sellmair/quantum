package io.sellmair.quantum.internal

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun Looper.asExecutor(): Executor {
    return LooperExecutor(this)
}

/*
################################################################################################
PRIVATE IMPLEMENTATION
################################################################################################
*/

private class LooperExecutor(looper: Looper) : Executor {
    private val handler = Handler(looper)
    override fun execute(command: Runnable) {
        handler.post(command)
    }
}