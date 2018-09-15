package io.sellmair.quantum

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface QuittedObservable {
    /**
     * Add a listener to this observable.
     *
     * This listener is guaranteed to be invoked by the executor
     * specified in [Quantum.Companion.create]
     *
     * The listener will be invoked as as soon as possible
     * if the it was quitted before this call.
     */
    fun addQuittedListener(listener: QuittedListener)


    /**
     * Remove listener from this observable
     */
    fun removeQuittedListener(listener: QuittedListener)
}