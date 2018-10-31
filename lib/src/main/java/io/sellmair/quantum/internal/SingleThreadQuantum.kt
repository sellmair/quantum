package io.sellmair.quantum.internal

import android.os.Looper
import io.sellmair.quantum.*
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal class SingleThreadQuantum<T>(
    initial: T,
    private val threading: Threading.Single,

    private val stateSubject: StateSubject<T> = StateSubject(
        executor = threading.looper.asExecutor(Handoff.DIRECT)),

    private val quittedSubject: QuitedSubject = QuitedSubject(
        executor = threading.looper.asExecutor(Handoff.DIRECT))) :

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

    override val history: MutableHistory<T> = LockedHistory(
        initial = initial)


    override val config: InstanceConfig = object : InstanceConfig {
        override val callbackExecutor: Executor = threading.looper.asExecutor()
        override val executor: Executor = threading.looper.asExecutor()
    }

    override fun quit(): Joinable = performQuit(Quit.NOW)


    /**
     * No difference between quit and quitSafely, because there is now reducer buffer
     * since each operation is performed immediate.
     */
    override fun quitSafely() = performQuit(Quit.SAFE)


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

    private var quitted by AtomicBoolean(false)


    private fun performQuit(quit: Quit): Joinable {

        /*
        Require the correct thread if threading is supposed to throw
         */
        when (threading) {
            is Threading.Single.Throw -> requireCorrectThread()
            is Threading.Single.Post -> Unit
        }


        if (quitted) {
            debug("performQuit: already quitted")
            return Joinable.noop()
        }

        /*
         Immediately set the quitted flag to ensure all pending operations on the single
         thread will see this now.
         This is allowed to be touched by any thread, since this boolean
         was declared atomic
        */
        if (quit == Quit.NOW) {
            debug("performQuit: quitting now")
            quitted = true
        }

        return threading.joinable {
            this.quitted = true
            this.quittedSubject.quitted()
            debug("performQuit: fully quitted")
        }
    }


    /*
    ################################################################################################
    HELPER
    ################################################################################################
    */

    private fun requireCorrectThread() {
        if (Looper.myLooper() != threading.looper) {
            throw ForbiddenThreadException(
                "Expected $threading.looper. Found: ${Looper.myLooper()}")
        }
    }

    init {
        stateSubject.publish(initial)
    }

}


/*
################################################################################################
PRIVATE API
################################################################################################
*/

enum class Quit {
    SAFE,
    NOW
}
