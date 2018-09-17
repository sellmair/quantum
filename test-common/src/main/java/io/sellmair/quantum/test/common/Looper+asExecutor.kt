package io.sellmair.quantum.test.common

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

fun Looper.executor(): Executor {
    val handler = Handler(this)
    return Executor { command: Runnable ->
        handler.post(command)
    }
}