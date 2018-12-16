package io.sellmair.quantum

import io.sellmair.quantum.internal.ProjectionOwnerConnectable

/*
################################################################################################
PUBLIC API
################################################################################################
*/

fun <T, Projection> Owner<T>.map(projection: (T) -> Projection): Connectable<T, Projection> {
    return ProjectionOwnerConnectable(this, projection)
}


