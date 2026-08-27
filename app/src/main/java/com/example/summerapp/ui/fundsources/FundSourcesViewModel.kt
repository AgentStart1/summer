package com.example.summerapp.ui.fundsources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.FundSource
import com.example.summerapp.data.llmd.LlmdTarget
import com.example.summerapp.data.llmd.LlmdTargetSettings
import com.example.summerapp.ui.host.AppDispatchers
import kotlinx.coroutines.flow.StateFlow

class FundSourcesViewModel(
    repository: DataRepository,
    settings: LlmdTargetSettings,
    dispatchers: AppDispatchers = AppDispatchers.Runtime,
) : ViewModel() {
    private val host = FundSourcesHost(repository, settings, viewModelScope, dispatchers)
    val uiState: StateFlow<FundSourcesUiState> = host.uiState

    fun addFundSource(name: String) {
        host.addFundSource(name)
    }

    fun updateFundSource(fundSource: FundSource) {
        host.updateFundSource(fundSource)
    }

    fun deleteFundSource(fundSource: FundSource) {
        host.deleteFundSource(fundSource)
    }

    fun selectLlmdTarget(target: LlmdTarget) {
        host.selectLlmdTarget(target)
    }

    class Factory(
        private val repository: DataRepository,
        private val settings: LlmdTargetSettings,
        private val dispatchers: AppDispatchers = AppDispatchers.Runtime,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FundSourcesViewModel(repository, settings, dispatchers) as T
        }
    }
}
