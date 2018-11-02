package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.ForbiddenThreadException
import io.sellmair.quantum.Threading

/*
################################################################################################
INTERNAL API
################################################################################################
*/
internal operator fun Threading.Single.invoke(block: () -> Unit) {
    return when (this) {
        is Threading.Single.Throw -> {
            if (Looper.myLooper() == this.looper) {
                block()
            } else {
                throw ForbiddenThreadException("" +
                    "Expected looper: ${this.looper} " +
                    "Found: ${Looper.myLooper()} (Thread: ${Thread.currentThread()}")
            }
        }

        is Threading.Single.Post -> {
            if (Looper.myLooper() == this.looper) {
                block()
            } else {
                this.handler.post(block)
                Unit
            }
        }
    }
}