package com.example.summerapp.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FeedViewModel(private val repository: DataRepository) : ViewModel() {
    val uiState: StateFlow<FeedUiState> = combine(
        repository.getAllBalanceChanges(),
        repository.getAllFundSources(),
    ) { balanceChanges, fundSources ->
        val fundSourceMap = fundSources.associateBy { it.id }
        FeedUiState.Success(balanceChanges, fundSourceMap) as FeedUiState
    }.catch { emit(FeedUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedUiState.Loading)

    class Factory(private val repository: DataRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedViewModel(repository) as T
        }
    }
}

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Success(
        val balanceChanges: List<BalanceChange>,
        val fundSources: Map<Long, FundSource>,
    ) : FeedUiState
}
