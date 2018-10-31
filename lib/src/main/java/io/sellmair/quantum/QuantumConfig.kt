package io.sellmair.quantum

import android.os.Looper
import io.sellmair.quantum.internal.asExecutor
import io.sellmair.quantum.internal.createDefaultPool

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
        val default: Threading = Threading.Multi.Pool(
            callbackExecutor = Looper.getMainLooper().asExecutor())

        var pool = Threading.createDefaultPool()
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
