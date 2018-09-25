package io.sellmair.quantum

import io.sellmair.quantum.internal.entangle.Connection
import io.sellmair.quantum.internal.entangle.Entanglement
import io.sellmair.quantum.internal.entangle.Projection

/*
################################################################################################
PUBLIC API
################################################################################################
*/

/**
 * Create a version of this Quantum which's state is mapped to a particular subset of the state.
 * This call has to be followed by [QuantumEntangleConnectionBuilder.connect] to fully
 * create a mapped / entangled quantum.
 *
 * All changes to the parent quantum will be received by the mapped quantum as well
 * (through the  [mapper] function
 *
 * All changes to this quantum are forwarded to the parent quantum using the
 * [QuantumEntangleConnectionBuilder.connect] function.
 *
 * ### Example
 *
 * ```
 * data class ChildState(val name: String, val age: Int)
 *
 * data class ParentState(val name: String, val age: Int, val children: List<ChildState>)
 *
 * // Get the quantum instance of the parent state
 * val parentQuantum: Quantum<ParentState> =  /* ... */
 *
 * // Create the child state
 * val childQuantum = parentQuantum
 *     .map { parentState ->  parentState.children }
 *     .connect { parentState, children -> parentState.copy(children = children) }
 *
 * // Increase the age of all children
 * childQuantum.setState { children ->
 *      children.map { child -> child.copy(age=child.age++) }
 * }
 *
 * ```
 */
fun <T, M> Quantum<T>.map(mapper: (T) -> M): QuantumEntangleConnectionBuilder<T, M> {
    return QuantumEntangleConnectionBuilder(this, ProjectionImpl(mapper))
}


class QuantumEntangleConnectionBuilder<T, P> internal constructor(
    private val quantum: Quantum<T>,
    private val projection: Projection<T, P>) {


    /**
     * The connect functions specifies how changes / reducers to the child quantum
     * should be forwarded to the parent
     *
     * @param connector: Function that receives the parent state and the new version of the child
     * state. The function then should return the new version of the parent state which is
     * reflecting the new child state
     */
    fun connect(connector: (parent: T, child: P) -> T): Quantum<P> =
        Entanglement(
            parent = quantum,
            projection = projection,
            connection = ConnectionImpl(connector))


}


/*
################################################################################################
PRIVATE IMPLEMENNTATION
################################################################################################
*/

private class ProjectionImpl<T, P>(private val projection: (T) -> P) : Projection<T, P> {
    override fun invoke(outer: T): P = projection(outer)
}

private class ConnectionImpl<Outer, Inner>(
    private val connection: (Outer, Inner) -> Outer) : Connection<Outer, Inner> {
    override fun invoke(outer: Outer, inner: Inner): Outer = connection(outer, inner)
}