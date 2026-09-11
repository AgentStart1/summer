package com.example.summerapp.ui.host

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

data class AppDispatchers(
    val default: CoroutineDispatcher,
    val io: CoroutineDispatcher,
) {
    companion object {
        val Runtime = AppDispatchers(
            default = Dispatchers.Default,
            io = Dispatchers.IO,
        )
    }
}

fun CoroutineScope.withDispatcher(dispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(coroutineContext + dispatcher)
