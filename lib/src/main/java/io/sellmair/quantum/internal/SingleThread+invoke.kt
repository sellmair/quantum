package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.ForbiddenThreadException
import io.sellmair.quantum.Threading

/*
################################################################################################
INTERNAL API
################################################################################################
*/
internal operator fun Threading.SingleThread.invoke(block: () -> Unit) {
    return when (this) {
        is Threading.SingleThread.Throw -> {
            if (Looper.myLooper() == this.looper) {
                block()
            } else {
                throw ForbiddenThreadException("" +
                    "Expected looper: ${this.looper} " +
                    "Found: ${Looper.myLooper()} (Thread: ${Thread.currentThread()}")
            }
        }

        is Threading.SingleThread.Post -> {
            if (Looper.myLooper() == this.looper) {
                block()
            } else {
                this.handler.post(block)
                Unit
            }
        }
    }
}