package io.sellmair.quantum


interface History<T> : Iterable<T> {
    /**
     * Indicates whether or not this history is allowed to collect states.
     * Setting this to 'false' will clear the current history
     */
    var enabled: Boolean

    /**
     * Maximum amount of states that are allowed in this history
     *
     * Must be positive, 0 or null.
     * Null indicating that there is no limit
     */
    var limit: Int?


    /**
     * Will clear the history.
     * Won't affect [enabled] state
     */
    fun clear()
}

