package io.sellmair.quantum

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
}