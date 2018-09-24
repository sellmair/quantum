package io.sellmair.quantum.internal

import io.sellmair.quantum.CycleFuture

/*
################################################################################################
INTERNAL API
################################################################################################
*/

fun CycleFuture.Companion.completed(): CycleFuture = CompletableCycleFuture()
    .also(CompletableCycleFuture::completed)

fun CycleFuture.Companion.rejected(): CycleFuture = CompletableCycleFuture()
    .also(CompletableCycleFuture::rejected)