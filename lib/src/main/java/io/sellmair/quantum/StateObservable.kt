package io.sellmair.quantum

interface StateObservable<T> {
    /**
     * Add a listener to the observable.
     *
     * The listener is guaranteed to be invoked by the executor
     * specified in [Quantum.Companion.create]
     *
     * The listener will be invoked with the current state as soon as possible
     */
    fun addStateListener(listener: StateListener<T>)

    /**
     * Remove listener from the observable.
     */
    fun removeStateListener(listener: StateListener<T>)
}