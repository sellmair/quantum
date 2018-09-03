package io.sellmair.quantum.rx

import io.reactivex.Observable
import io.sellmair.quantum.StateObservable

val <T> StateObservable<T>.rx: Observable<T>
    get() = Observable.create<T> { emitter ->

        fun onState(state: T) {
            emitter.onNext(state)
        }

        this.addListener(::onState)
        emitter.setCancellable { this.removeListener(::onState) }
    }