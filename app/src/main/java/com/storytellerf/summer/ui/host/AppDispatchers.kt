package com.storytellerf.summer.ui.host

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val default: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val coordination: CoroutineDispatcher = default,
) {
    companion object {
        val Runtime = AppDispatchers(
            default = Dispatchers.Default,
            io = Dispatchers.IO,
            coordination = Dispatchers.Default.limitedParallelism(1),
        )
    }
}

fun CoroutineScope.withDispatcher(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(coroutineContext + dispatcher)
