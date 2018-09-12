package io.sellmair.quantum

interface Joinable {
    /**
     * Will wait the current thread until the target dies.
     * @see Thread.join
     */
    fun join()
}