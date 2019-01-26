package io.sellmair.quantum

import io.sellmair.quantum.internal.OwnerImpl
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.EmptyCoroutineContext

internal fun<T> createTestOwner(initial: T) = OwnerImpl(
    initial = initial,
    history = History(),
    coroutineContext = EmptyCoroutineContext,
    mutex = Mutex())