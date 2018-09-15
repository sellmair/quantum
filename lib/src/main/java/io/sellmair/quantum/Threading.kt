package io.sellmair.quantum

import java.util.concurrent.Executor

sealed class Threading {
    class Sync : Threading()
    class Pool : Threading()
    class Thread : Threading()
    class Custom(val executor: Executor) : Threading()
    companion object
}