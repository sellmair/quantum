package io.sellmair.quantum.test.common

import io.sellmair.quantum.Joinable
import io.sellmair.quantum.internal.asJoinable
import org.junit.Assert
import java.util.concurrent.TimeUnit

private const val defaultTimeout = 1L
private val defaultUnit = TimeUnit.MINUTES

fun Joinable.assertJoin(timeout: Long = defaultTimeout, unit: TimeUnit = defaultUnit) {
    val joined = this.join(timeout, unit)
    Assert.assertTrue("Join failed", joined)
}

fun Thread.assertJoin(timeout: Long = defaultTimeout, unit: TimeUnit = defaultUnit) {
    this.asJoinable().assertJoin(timeout, unit)
}