package io.sellmair.quantum

import io.sellmair.quantum.internal.entangle.Connection
import io.sellmair.quantum.internal.entangle.Entanglement
import io.sellmair.quantum.internal.entangle.Projection
import java.util.concurrent.Executor

fun <T> Quantum<T>.entangle(): QuantumEntangleProjectionBuilder<T> {
    return QuantumEntangleProjectionBuilder(this)
}

class QuantumEntangleProjectionBuilder<T>(private val quantum: Quantum<T>) {
    fun <P> project(projection: (T) -> P) =
        QuantumEntangleConnectionBuilder(quantum, ProjectionImpl(projection))


    class ProjectionImpl<T, P>(private val projection: (T) -> P) : Projection<T, P> {
        override fun invoke(outer: T): P = projection(outer)
    }
}

class QuantumEntangleConnectionBuilder<T, P> internal constructor(
    private val quantum: Quantum<T>,
    private val projection: Projection<T, P>) {

    fun connect(connector: (outer: T, inner: P) -> T) =
        QuantumEntangleConfigurationBuilder(quantum, projection, ConnectionImpl(connector))

    class ConnectionImpl<Outer, Inner>(
        private val connection: (Outer, Inner) -> Outer) : Connection<Outer, Inner> {
        override fun invoke(outer: Outer, inner: Inner): Outer = connection(outer, inner)
    }
}

class QuantumEntangleConfigurationBuilder<T, P> internal constructor(
    private val quantum: Quantum<T>,
    private val projection: Projection<T, P>,
    private val connection: Connection<T, P>) {

    private var callbackExecutor = quantum.config.callbackExecutor

    fun setCallbackExecutor(executor: Executor) = apply {
        callbackExecutor = executor
    }


    fun build(): Quantum<P> = Entanglement(quantum, callbackExecutor, connection, projection)
}


