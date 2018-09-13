package io.sellmair.quantum.livedata

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.os.Looper
import io.sellmair.quantum.StateObservable
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


/**
 * Will get an associated [LiveData] object from a certain state observable.
 */
val <T> StateObservable<T>.live: LiveData<T>
    get() = storeLock.withLock {
        getExistingLiveData() ?: createNewLiveData()
    }


/**
 * Stores the live data objects to the given StateObservable
 * to ensure that one StateObservable only has ONE live-data associated with it.
 *
 * Synchronized using [storeLock]
 */
private val store = WeakHashMap<StateObservable<*>, MutableLiveData<*>>()

/**
 * Lock used to synchronize [store] with.
 */
private val storeLock = ReentrantLock()


/**
 * Get the associated live data (if one exists)
 */
@Suppress("UNCHECKED_CAST")
private fun <T> StateObservable<T>.getExistingLiveData(): LiveData<T>? {
    return store[this] as? LiveData<T>
}

/**
 * Create a new associated live data.
 * This will also wire up the listener to the quantum.
 */
private fun <T> StateObservable<T>.createNewLiveData(): LiveData<T> {
    val liveData = MutableLiveData<T>()
    addListener(liveData::push)
    store[this] = liveData
    return liveData
}


/**
 * Simple helper function that sets the value of the [LiveData] immediately if invoked
 * by the main thread. Uses [MutableLiveData.postValue] otherwise.
 */
private fun <T> MutableLiveData<T>.push(value: T) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        this.value = value
        return
    }

    this.postValue(value)
}