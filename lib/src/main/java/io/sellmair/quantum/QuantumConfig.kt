package io.sellmair.quantum

import android.os.Looper
import io.sellmair.quantum.internal.asExecutor
import io.sellmair.quantum.internal.createDefaultPool
import java.util.concurrent.Executor

/*
################################################################################################
PUBLIC API
################################################################################################
*/

class QuantumConfig {

    /*
    ################################################################################################
    THREADING
    ################################################################################################
    */

    val threading = ThreadingConfig()

    class ThreadingConfig {
        class Default {
            var mode: Threading = Threading.Pool()
            var callback: Executor = Looper.getMainLooper().asExecutor()
        }

        val default = Default()
        val pool = Threading.createDefaultPool()
    }


    /*
    ################################################################################################
    LOGGING
    ################################################################################################
    */

    val logging = LoggingConfig()

    class LoggingConfig {
        var tag = "Quantum"
        var level = LogLevel.NONE
    }


    /*
    ################################################################################################
    HISTORY
    ################################################################################################
    */

    val history = HistoryConfig()
    
    class HistoryConfig {
        class Default {
            var enabled: Boolean = false
            var limit: Int? = null
        }

        val default = Default()
    }

}

fun test() {
}