package com.storytellerf.summet.ui.addbalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.storytellerf.summet.data.DataRepository
import com.storytellerf.summet.data.db.entity.FundSource
import com.storytellerf.summet.data.llmd.BalanceImageAnalyzer
import com.storytellerf.summet.data.llmd.LlmdTarget
import com.storytellerf.summet.ui.host.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class AddBalanceChangeViewModel(
    repository: DataRepository,
    private val imageAnalyzer: BalanceImageAnalyzer,
    imageAnalysisTarget: Flow<LlmdTarget> = flowOf(LlmdTarget.Release),
    dispatchers: AppDispatchers = AppDispatchers.Runtime,
) : ViewModel() {
    private val host = AddBalanceChangeHost(
        repository = repository,
        imageAnalyzer = imageAnalyzer,
        scope = viewModelScope,
        dispatchers = dispatchers,
        imageAnalysisTarget = imageAnalysisTarget,
    )
    val uiState: StateFlow<AddBalanceChangeUiState> = host.uiState
    val effects: SharedFlow<AddBalanceChangeEffect> = host.effects

    fun selectFundSource(fundSource: FundSource) {
        host.selectFundSource(fundSource)
    }

    fun updateBalance(balance: String) {
        host.updateBalance(balance)
    }

    fun updateNote(note: String) {
        host.updateNote(note)
    }

    fun extractBalanceFromImage(imageReference: String) {
        host.extractBalanceFromImage(imageReference)
    }

    fun onAuthorizationResult(authorized: Boolean) {
        host.onAuthorizationResult(authorized)
    }

    fun saveBalanceChange() {
        host.saveBalanceChange()
    }

    override fun onCleared() {
        host.close()
        super.onCleared()
    }

    class Factory(
        private val repository: DataRepository,
        private val imageAnalyzer: BalanceImageAnalyzer,
        private val imageAnalysisTarget: Flow<LlmdTarget> = flowOf(LlmdTarget.Release),
        private val dispatchers: AppDispatchers = AppDispatchers.Runtime,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AddBalanceChangeViewModel::class.java))
            return AddBalanceChangeViewModel(
                repository,
                imageAnalyzer,
                imageAnalysisTarget,
                dispatchers,
            ) as T
        }
    }
}
