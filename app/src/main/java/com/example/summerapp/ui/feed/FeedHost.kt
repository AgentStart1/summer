package com.example.summerapp.ui.feed

import com.example.summerapp.data.DataRepository
import com.example.summerapp.ui.host.AppDispatchers
import com.example.summerapp.ui.host.withDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class FeedHost(
    repository: DataRepository,
    scope: CoroutineScope,
    dispatchers: AppDispatchers,
) {
    val uiState: StateFlow<FeedUiState> = combine(
        repository.getAllBalanceChanges(),
        repository.getAllFundSources(),
    ) { balanceChanges, fundSources ->
        FeedUiState.Success(buildBalanceTimeline(balanceChanges, fundSources)) as FeedUiState
    }
        .flowOn(dispatchers.default)
        .catch { emit(FeedUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(
            scope = scope.withDispatcher(dispatchers.default),
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FeedUiState.Loading,
        )
}

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Success(val snapshots: List<BalanceSnapshot>) : FeedUiState
}
