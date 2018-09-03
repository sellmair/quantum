package io.sellmair.quantum

interface StateObservable<T> {
    /**
     * Add a listener to the observable.
     * The listener is guaranteed to be invoked by the main thread
     * The listener will be invoked with the current state as soon as possible
     */
    fun addListener(listener: StateListener<T>)

    /**
     * Removea listener from the observable.
     */
    fun removeListener(listener: StateListener<T>)
}