package io.sellmair.quantum

interface Chronological<T> {
    val history: History<T>
}