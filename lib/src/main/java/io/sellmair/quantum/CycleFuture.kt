package io.sellmair.quantum

interface CycleFuture : Joinable {
    fun after(action: () -> Unit)
    fun completed(action: () -> Unit)
    fun rejected(action: () -> Unit)
}


