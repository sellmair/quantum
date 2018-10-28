package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.*
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class SingleThreadQuantum<T>(
    initial: T,
    private val threading: Threading.SingleThread,

    private val stateSubject: StateSubject<T> = StateSubject(
        executor = Executor { it.run() },
        lock = SingleThreadLock(threading.looper.thread)),

    private val quittedSubject: QuitedSubject = QuitedSubject(
        executor = Executor { it.run() },
        lock = SingleThreadLock(threading.looper.thread))) :
    Quantum<T>,
    StateObservable<T> by stateSubject,
    QuitedObservable by quittedSubject {


    /*
    ################################################################################################
    API: Quantum
    ################################################################################################
    */

    override fun setStateFuture(reducer: ItReducer<T>): CycleFuture {
        val future = CompletableCycleFuture(Executor(Runnable::run))

        threading {
            if (quitted) {
                future.rejected()
                return@threading
            }

            applyReducer(reducer)
            future.completed()
        }


        return future
    }

    override fun withStateFuture(action: ItAction<T>): CycleFuture {
        val future = CompletableCycleFuture(Executor(Runnable::run))

        threading {
            if (quitted) {
                future.rejected()
                return@threading
            }

            applyAction(action)
            future.completed()
        }

        return future
    }

    override val history: MutableHistory<T> = LockedHistory<T>(
        initial = initial,
        lock = SingleThreadLock(threading.looper.thread))

    override val config: InstanceConfig = object : InstanceConfig {
        override val callbackExecutor: Executor = threading.looper.asExecutor()
        override val executor: Executor = threading.looper.asExecutor()
    }

    override fun quit(): Joinable {
        return threading.joinable {
            this.performQuit()
        }
    }

    /**
     * No difference between quit and quitSafely, because there is now reducer buffer
     * since each operation is performed immediate.
     */
    override fun quitSafely() = quit()

    override fun addQuittedListener(listener: QuittedListener) = threading {
        quittedSubject.addQuittedListener(listener)
    }

    override fun removeQuittedListener(listener: QuittedListener) = threading {
        quittedSubject.removeQuittedListener(listener)
    }

    override fun addStateListener(listener: StateListener<T>) = threading {
        stateSubject.addStateListener(listener)
    }

    override fun removeStateListener(listener: StateListener<T>) = threading {
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
        requireCorrectThread()

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
        if (Looper.myLooper() != threading.looper) {
            throw ForbiddenThreadException("Expected $threading.looper. Found: ${Looper.myLooper()}")
        }
    }

}

