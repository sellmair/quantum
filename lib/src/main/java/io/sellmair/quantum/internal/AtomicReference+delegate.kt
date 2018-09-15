package io.sellmair.quantum.internal

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

operator fun <T> AtomicReference<T>.getValue(thisRef: Any?, property: KProperty<*>): T {
    return this.get()
}

operator fun <T> AtomicReference<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.set(value)
}