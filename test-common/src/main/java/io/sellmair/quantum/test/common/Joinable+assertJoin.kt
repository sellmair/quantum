package io.sellmair.quantum.test.common

import io.sellmair.quantum.Joinable
import io.sellmair.quantum.internal.asJoinable
import org.junit.Assert
import java.util.concurrent.TimeUnit

fun Joinable.assertJoin(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS) {
    val joined = this.join(timeout, unit)
    Assert.assertTrue("Join failed", joined)
}

fun Thread.assertJoin(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS) {
    this.asJoinable().assertJoin(timeout, unit)
}