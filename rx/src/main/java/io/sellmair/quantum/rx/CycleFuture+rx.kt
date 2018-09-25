package io.sellmair.quantum.rx

import io.reactivex.Completable
import io.sellmair.quantum.CycleFuture

val CycleFuture.rx: Completable
    get() = Completable.create { emitter -> this.after(emitter::onComplete) }