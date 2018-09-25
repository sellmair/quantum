package io.sellmair.quantum

interface CycleFuture : Joinable {
    fun after(action: () -> Unit): CycleFuture
    fun completed(action: () -> Unit): CycleFuture
    fun rejected(action: () -> Unit): CycleFuture

    companion object
}


