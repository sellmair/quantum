package io.sellmair.quantum.rx

import io.reactivex.Observable
import io.sellmair.quantum.Quantum

val <T> Quantum<T>.rx: Observable<T>
    get() = Observable.create<T> { emitter ->
        this.addStateListener(emitter::onNext)
        this.addQuittedListener(emitter::onComplete)
        emitter.setCancellable {
            this.removeStateListener(emitter::onNext)
        }
    }