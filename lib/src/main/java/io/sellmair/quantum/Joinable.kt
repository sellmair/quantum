package io.sellmair.quantum

import java.util.concurrent.TimeUnit

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface Joinable {
    /**
     * Will wait the current thread until the target dies.
     * @see Thread.join
     */
    fun join()

    /**
     * Will wait until the target dies or timeout was reached.
     * @see join
     * @return true if the join was successful, false if the timeout was reached
     */
    fun join(timeout: Long, unit: TimeUnit): Boolean
}