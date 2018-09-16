package io.sellmair.quantum.internal

/*
################################################################################################
INTERNAL API
################################################################################################
*/

/**
 * Creates a copy of the current list and clears the current list afterwards
 */
internal fun <T> MutableList<T>.poll(): List<T> {
    val copy = ArrayList<T>(this.size)
    copy.addAll(this)
    this.clear()
    return copy
}