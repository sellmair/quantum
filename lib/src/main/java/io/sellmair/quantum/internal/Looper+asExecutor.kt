package io.sellmair.quantum.internal

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun Looper.asExecutor(handoff: Handoff = Handoff.POST): Executor {
    return LooperExecutor(this, handoff)
}

internal enum class Handoff {
    POST,
    DIRECT
}

/*
################################################################################################
PRIVATE IMPLEMENTATION
################################################################################################
*/

private class LooperExecutor(
    private val looper: Looper,
    private val handoff: Handoff) : Executor {
    private val handler = Handler(looper)
    override fun execute(command: Runnable) {
        when {
            handoff == Handoff.DIRECT && Looper.myLooper() == looper -> command.run()
            else -> handler.post(command)

        }
    }
}



