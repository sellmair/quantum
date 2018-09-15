package io.sellmair.quantum.rx

import io.reactivex.Observable
import io.sellmair.quantum.StateObservable

val <T> StateObservable<T>.rx: Observable<T>
    get() = Observable.create<T> { emitter ->
        this.addStateListener(emitter::onNext)
        emitter.setCancellable {
            this.removeStateListener(emitter::onNext)
        }
    }