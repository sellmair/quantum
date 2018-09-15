package io.sellmair.quantum.internal

import android.util.Log
import io.sellmair.quantum.LogLevel

/*
################################################################################################
INTERNAL API
################################################################################################
*/

internal fun info(message: String) {
    when (level) {
        LogLevel.NONE -> noop()
        LogLevel.INFO,
        LogLevel.VERBOSE,
        LogLevel.DEBUG -> Log.i(tag, message)
    }
}

internal fun debug(message: String) {
    when (level) {
        LogLevel.NONE -> noop()
        LogLevel.INFO -> noop()
        LogLevel.VERBOSE -> Log.d(tag, message)
        LogLevel.DEBUG -> Log.d(tag, message)
    }
}

internal fun verbose(message: String) {
    when (level) {
        LogLevel.NONE -> noop()
        LogLevel.INFO -> noop()
        LogLevel.VERBOSE -> Log.v(tag, message)
        LogLevel.DEBUG -> noop()
    }
}

internal fun warn(message: String) {
    when (level) {
        LogLevel.NONE -> noop()
        LogLevel.INFO -> Log.w(tag, message)
        LogLevel.VERBOSE -> Log.w(tag, message)
        LogLevel.DEBUG -> Log.w(tag, message)
    }
}

private val tag get() = config { logging.tag }

private val level get() = config { logging.level }

private fun noop() = Unit