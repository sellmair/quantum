package io.sellmair.quantum

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/*
################################################################################################
PUBLIC API
################################################################################################
*/

sealed class Threading {
    /**
     * All reducers and actions will be called synchronous inside a lock.
     * This option might almost never be the desired one, since it has the least
     * throughput of all threading options and it is more error prone when working with locks.
     */
    object Sync : Threading()

    /**
     * A shared thread pool is used for multiple [Quantum] instances.
     * This is the recommended option for almost all cases, because it provides a good throughput
     * as well as good memory footprint and responsiveness.
     *
     * The pool that is used can be configured using [Quantum.Companion.configure]
     */
    object Pool : Threading()

    /**
     * Each [Quantum] instance will have one allocated thread that lives until the [Quantum] quits.
     * This most likely might be the most responsive and option with the highest throughput,
     * but having many threads allocated could lead to higher memory footprints.
     */
    object Thread : Threading()

    /**
     * Custom [executor] is used to execute reducers and actions.
     * This [executor] can be shared for multiple [Quantum] instances.
     *
     * Be aware: Executors are not allowed to drop tasks!
     */
    data class Custom(val executor: Executor) : Threading()


    sealed class SingleThread : Threading() {
        internal abstract val looper: Looper

        class Throw(override val looper: Looper) : SingleThread()
        class Post(override val looper: Looper,
                   internal val handler: Handler = Handler(looper)) : SingleThread()
    }

    companion object
}