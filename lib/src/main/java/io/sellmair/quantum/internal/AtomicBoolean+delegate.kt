package io.sellmair.quantum.internal

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KProperty


/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal operator fun AtomicBoolean.getValue(thisRef: Any?, property: KProperty<*>): Boolean {
    return this.get()
}

internal operator fun AtomicBoolean.setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
    this.set(value)
}