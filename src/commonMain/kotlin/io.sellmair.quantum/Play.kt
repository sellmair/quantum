package io.sellmair.quantum


/*
################################################################################################
PLAYGROUND
################################################################################################
*/

data class PlayState(val isLoggingIn: Boolean, val data: String)
class Test(private val quant: Owner<PlayState>) : Owner<PlayState> by quant {

    fun playground() = quant {
        setWithAccess {
            if (state.isLoggingIn) return@quant
            state.copy(isLoggingIn = true)
        }

        val data = runCatching { download() } // this is long running and async

        setWithAccess { state.copy(isLoggingIn = false, data = data.getOrDefault("")) }
    }


    suspend fun download(): String {
        return ""
    }

    fun x() {
        playground().cancel()
    }
}