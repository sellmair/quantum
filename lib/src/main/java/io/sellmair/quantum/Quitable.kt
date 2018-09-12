package io.sellmair.quantum

interface Quitable {
    fun quit(): Joinable
    fun quitSafely(): Joinable
}