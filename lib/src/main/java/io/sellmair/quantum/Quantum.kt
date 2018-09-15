package io.sellmair.quantum

import io.sellmair.quantum.internal.*
import java.util.concurrent.Executor

/*
################################################################################################
PUBLIC API
################################################################################################
*/

typealias Reducer<T> = T.() -> T
typealias Action<T> = T.() -> Unit
typealias ItReducer<T> = (T) -> T
typealias ItAction<T> = (T) -> Unit
typealias StateListener<T> = (T) -> Unit
typealias QuittedListener = () -> Unit
/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface Quantum<T> : Quitable, QuitedObservable, StateObservable<T> {


    /**
     * Will enqueue the [reducer].
     * The [reducer] will be run on the internal state thread and should not
     * contain long running / blocking operations.
     * Only one reducer will running at a time.
     *
     *
     * The internal state will be updated according to the reducer.
     * The reducer is not allowed to manipulate the object directly and rather
     * should provide a copy of the object.
     *
     *
     * The reducer, however, is allowed to return the same unmodified instance
     * to signal a NO-OP to the state.
     *
     *
     */
    fun setState(reducer: Reducer<T>)


    /**
     * Same as [setState]
     */
    fun setStateIt(reducer: ItReducer<T>) = setState(reducer)


    /**
     * Will enqueue the [action]
     * The [action] will be executed on the internal state thread and should not
     * contain long running / blocking operations.
     * Only one action will be running at a time.
     *
     * The action will run at the end of the next cycle.
     * All pending reducers will be invoked and evaluated before.
     */
    fun withState(action: Action<T>)


    /**
     * Same as [withState]
     */
    fun withStateIt(action: ItAction<T>) = withState(action)


    /**
     *  History of all states (including intermediate states)
     *  A new state will be created by every reducer.
     *  Each of those states will be pushed to the history.
     *
     *  History will be disabled by default.
     *
     *
     *  ## WARNING
     * This field is for debugging purpose only.
     * DO NOT USE THIS TO CREATE A DIFF ON THE STATE, because
     * views WILL NOT be notified after each reducer.
     * This means, that the history contains states that were not sent to
     * the UI, therefore it is a stupid idea to use this to create a diff.
     * If you want to create a diff: Use live-data or rxJava to diff against
     * the actual last state!
     */
    val history: History<T>


    /**
     * Quits the current Quantum.
     * All currently enqueued reducers and actions will be discarded.
     * Currently running reducers will be executed
     * It is necessary to quit this state store to
     * ensure that the internal thread is stopped and the resources can be garbage collected.
     */
    override fun quit(): Joinable


    /**
     * Quits the current Quantum.
     * All currently enqueued reducers and actions will eb safely executed.
     * It is necessary to quit a quantum to ensure that internal resources can be freed
     */
    override fun quitSafely(): Joinable

    companion object
}

/**
 * @param initial The initial state of the quantum.
 *
 * @param threading The threading option for this quantum.
 * - Default value can be configured using [Quantum.Companion.configure].
 * - Default configuration is [Threading.Pool]
 *
 * @param callbackExecutor The executor used to notify [StateListener]s and [QuittedListener]'s
 * - Default value can be configured using [Quantum.Companion.configure]
 * - Default configuration is Android's main thread
 */
fun <T> Quantum.Companion.create(
    initial: T,
    threading: Threading = config { this.threading.default.mode },
    callbackExecutor: Executor = config { this.threading.default.callbackExecutor }): Quantum<T> {
    val stateSubject = StateSubject<T>(callbackExecutor)
    val quittedSubject = QuitedSubject(callbackExecutor)
    val managedExecutor = managedExecutor(threading)
    val quantum = ExecutorQuantum(initial, stateSubject, quittedSubject, managedExecutor.executor)
    quantum.addQuittedListener { managedExecutor.quitable?.quitSafely() }
    return quantum
}


/**
 * Create a [ManagedExecutor] from a given threading optionn
 */
private fun managedExecutor(threading: Threading): ManagedExecutor {
    return when (threading) {
        is Threading.Sync -> ManagedExecutor.nonQuitable(Executor(Runnable::run))
        is Threading.Pool -> ManagedExecutor.nonQuitable(config { this.threading.pool })
        is Threading.Thread -> ManagedExecutor.quitable(SingleThreadExecutor())
        is Threading.Custom -> ManagedExecutor.nonQuitable(threading.executor)
    }
}

/**
 * Wrapper around [Executor] that indicates whether or not the executor
 * should be quitted by if the quantum quitted.
 *
 * This is especially necessary if a new thread or thread-pool was allocated just
 * for a quantum. This thread or thread pool needs to get quitted if the quantum died.
 */
private data class ManagedExecutor(
    val executor: Executor,
    val quitable: Quitable? = null) {
    companion object {
        fun quitable(executor: QuitableExecutor): ManagedExecutor {
            return ManagedExecutor(executor, executor)
        }

        fun nonQuitable(executor: Executor): ManagedExecutor {
            return ManagedExecutor(executor, null)
        }
    }
}




