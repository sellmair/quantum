package io.sellmair.quantum.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

suspend inline operator fun <T> Mutex.invoke(action: () -> T) = withLock(action = action)