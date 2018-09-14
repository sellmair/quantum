package io.sellmair.quantum.internal

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

val threadPool by lazy {
    ThreadPoolExecutor(
        1, // core pool size
        12, // max pool size
        5L,  // keep alive time
        TimeUnit.SECONDS, // keep alive time unit
        ArrayBlockingQueue<Runnable>(10, true) // queue
    )
}