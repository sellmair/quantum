package io.sellmair.quantum

interface Owner<T> {
    fun setState(reducer: Reducer<T>)
    fun setStateIt(reducer: ItReducer<T>)
    fun withState(action: Action<T>)
    fun withStateIt(action: Action<T>)
}

