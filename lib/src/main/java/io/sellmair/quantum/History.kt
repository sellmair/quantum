package io.sellmair.quantum

/*
################################################################################################
PUBLIC API
################################################################################################
*/

interface HistorySnapshot<T> : Iterable<T> {
    /**
     * Indicates whether or not this history is allowed to collect states.
     * Setting this to 'false' will clear the current history
     */
    val enabled: Boolean

    /**
     * Maximum amount of states that are allowed in this history
     *
     * Must be positive (>=1) or null.
     * Null indicating that there is no limit
     *
     * Note: The history will always start with the initial state
     */
    val limit: Int?
}

/**
 * History of states.
 * If enabled: Will always start with the initial state even if exceeded the limit.
 */
interface History<T> : HistorySnapshot<T> {
    /**
     * Indicates whether or not this history is allowed to collect states.
     * Setting this to 'false' will clear the current history
     */
    override var enabled: Boolean

    /**
     * Maximum amount of states that are allowed in this history
     *
     * Must be positive (>=1) or null.
     * Null indicating that there is no limit
     *
     * Note: The history will always start with the initial state
     */
    override var limit: Int?


    /**
     * Will clear the history.
     * Won't affect [enabled] state
     */
    fun clear()
}

