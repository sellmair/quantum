package io.sellmair.quantum.internal

import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun <T> Lock.tryWithLock(action: () -> T): T? {
    this.withLock {
        return if (tryLock()) {
            try {
                action()
            } finally {
                unlock()
            }
        } else null
    }
}