package io.sellmair.quantum.internal

import io.sellmair.quantum.Quitable
import java.util.concurrent.Executor

interface QuitableExecutor : Executor, Quitable

