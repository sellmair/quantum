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

            class Multi {
                var mode: Threading.Multi = Threading.Multi.Pool
                var callbackExecutor: Executor = Looper.getMainLooper().asExecutor()
            }

            class Single {
                var mode: Threading.Single = Threading.Single.Post(Looper.getMainLooper())
            }

            val multi = Multi()
            val single = Single()
        }

        val default = Default()
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
