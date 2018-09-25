package io.sellmair.quantum.internal

import io.sellmair.quantum.CycleFuture
import java.util.concurrent.Executor

/*
################################################################################################
INTERNAL API
################################################################################################
*/

fun CycleFuture.Companion.completed(callbackExecutor: Executor = Executor { it.run() }):
    CycleFuture = CompletableCycleFuture(callbackExecutor).also(CompletableCycleFuture::completed)

fun CycleFuture.Companion.rejected(callbackExecutor: Executor = Executor { it.run() }):
    CycleFuture = CompletableCycleFuture(callbackExecutor).also(CompletableCycleFuture::rejected)