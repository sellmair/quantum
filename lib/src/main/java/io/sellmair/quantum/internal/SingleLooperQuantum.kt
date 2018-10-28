package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.*
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class SingleLooperQuantum<T>(
    initial: T,
    private val looper: Looper,

    private val stateSubject: StateSubject<T> = StateSubject(
        executor = Executor { it.run() },
        lock = SingleThreadLock(looper.thread)),

    private val quittedSubject: QuitedSubject = QuitedSubject(
        executor = Executor { it.run() },
        lock = SingleThreadLock(looper.thread))) :
    Quantum<T>,
    StateObservable<T> by stateSubject,
    QuitedObservable by quittedSubject {


    /*
    ################################################################################################
    API: Quantum
    ################################################################################################
    */

    override fun setStateFuture(reducer: ItReducer<T>): CycleFuture {
        requireCorrectThread()
        if (quitted) return CycleFuture.rejected(this.config.callbackExecutor)
        applyReducer(reducer)
        return CycleFuture.completed(this.config.callbackExecutor)
    }

    override fun withStateFuture(action: ItAction<T>): CycleFuture {
        requireCorrectThread()
        if (quitted) return CycleFuture.rejected(this.config.callbackExecutor)
        applyAction(action)
        return CycleFuture.completed(this.config.callbackExecutor)
    }

    override val history: MutableHistory<T> = LockedHistory<T>(
        initial = initial,
        lock = SingleThreadLock(looper.thread))

    override val config: InstanceConfig = object : InstanceConfig {
        override val callbackExecutor: Executor = looper.asExecutor()
        override val executor: Executor = looper.asExecutor()
    }

    override fun quit(): Joinable {
        requireCorrectThread()
        performQuit()
        return Joinable.noop()
    }

    /**
     * No difference between quit and quitSafely, because there is now reducer buffer
     * since each operation is performed immediate.
     */
    override fun quitSafely() = quit()

    override fun addQuittedListener(listener: QuittedListener) {
        requireCorrectThread()
        quittedSubject.addQuittedListener(listener)
    }

    override fun removeQuittedListener(listener: QuittedListener) {
        requireCorrectThread()
        quittedSubject.removeQuittedListener(listener)
    }

    override fun addStateListener(listener: StateListener<T>) {
        requireCorrectThread()
        stateSubject.addStateListener(listener)
    }

    override fun removeStateListener(listener: StateListener<T>) {
        requireCorrectThread()
        stateSubject.removeStateListener(listener)
    }

    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: State
    ################################################################################################
    */

    private var state: T = initial
        set(value) {
            requireCorrectThread()
            field = value
        }
        get() {
            requireCorrectThread()
            return field
        }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Apply reducer
    ################################################################################################
    */

    private fun applyReducer(reducer: Reducer<T>) {
        requireCorrectThread()
        this.state = reducer(state)
        history.add(state)
        stateSubject.publish(state)
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: Apply action
    ################################################################################################
    */

    private fun applyAction(action: Action<T>) {
        requireCorrectThread()
        action(state)
    }


    /*
    ################################################################################################
    PRIVATE IMPLEMENTATION: quitting
    ################################################################################################
    */

    private var quitted: Boolean = false


    private fun performQuit() {
        check(!quitted)

        this.quitted = true
        this.quittedSubject.quitted()
    }


    /*
    ################################################################################################
    HELPER
    ################################################################################################
    */

    private fun requireCorrectThread() {
        if (Looper.myLooper() != looper) {
            throw ForbiddenThreadException("Expected $looper. Found: ${Looper.myLooper()}")
        }
    }

}

