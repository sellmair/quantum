package io.sellmair.quantum.internal

import io.sellmair.quantum.Connectable
import io.sellmair.quantum.Owner

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class ProjectionOwnerConnectable<T, Projection>(
    private val owner: Owner<T>,
    private val projection: (T) -> Projection) : Connectable<T, Projection> {

    override fun connect(connection: T.(Projection) -> T): Owner<Projection> {
        return ProjectionOwner(owner, projection, connection)
    }
}



