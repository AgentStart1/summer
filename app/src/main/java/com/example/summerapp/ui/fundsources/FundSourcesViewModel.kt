package com.example.summerapp.ui.fundsources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.FundSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FundSourcesViewModel(private val repository: DataRepository) : ViewModel() {
    val uiState: StateFlow<FundSourcesUiState> = repository.getAllFundSources()
        .map { FundSourcesUiState.Success(it) as FundSourcesUiState }
        .catch { emit(FundSourcesUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FundSourcesUiState.Loading)

    fun addFundSource(name: String) {
        viewModelScope.launch {
            repository.insertFundSource(FundSource(name = name))
        }
    }

    fun updateFundSource(fundSource: FundSource) {
        viewModelScope.launch {
            repository.updateFundSource(fundSource.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteFundSource(fundSource: FundSource) {
        viewModelScope.launch {
            repository.deleteFundSource(fundSource)
        }
    }

    class Factory(private val repository: DataRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FundSourcesViewModel(repository) as T
        }
    }
}

sealed interface FundSourcesUiState {
    data object Loading : FundSourcesUiState
    data class Error(val message: String) : FundSourcesUiState
    data class Success(val fundSources: List<FundSource>) : FundSourcesUiState
}
