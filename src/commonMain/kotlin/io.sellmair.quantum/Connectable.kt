package io.sellmair.quantum

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface Connectable<T, Projection> {
    fun connect(connection: T.(Projection) -> T): Owner<Projection>
}