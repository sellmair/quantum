package io.sellmair.quantum.test.common

import io.sellmair.quantum.Joinable
import io.sellmair.quantum.internal.asJoinable
import org.junit.Assert
import java.util.concurrent.TimeUnit

private const val defaultTimeout = 10L
private val defaultUnit = TimeUnit.SECONDS

fun Joinable.assertJoin(timeout: Long = defaultTimeout, unit: TimeUnit = defaultUnit,
                        message: String? = null) {
    val joined = this.join(timeout, unit)
    val formattedMessage = if (message != null) ": $message" else ""
    Assert.assertTrue("Join failed $formattedMessage", joined)
}

fun Thread.assertJoin(timeout: Long = defaultTimeout, unit: TimeUnit = defaultUnit,
                      message: String? = null) {
    this.asJoinable().assertJoin(timeout, unit, message)
}