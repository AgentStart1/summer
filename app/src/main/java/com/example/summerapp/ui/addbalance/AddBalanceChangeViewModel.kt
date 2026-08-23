package com.example.summerapp.ui.addbalance

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.summerapp.data.DataRepository
import com.example.summerapp.data.db.entity.BalanceChange
import com.example.summerapp.data.db.entity.FundSource
import com.example.summerapp.data.llmd.LlmdImageAnalyzer
import com.example.summerapp.data.llmd.LlmdServiceConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddBalanceChangeViewModel(
    application: Application,
    private val repository: DataRepository,
    private val llmdServiceConnection: LlmdServiceConnection,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AddBalanceChangeUiState())
    val uiState: StateFlow<AddBalanceChangeUiState> = _uiState.asStateFlow()

    val fundSources: StateFlow<List<FundSource>> = repository.getAllFundSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFundSource(fundSource: FundSource) {
        _uiState.update { it.copy(selectedFundSource = fundSource) }
    }

    fun updateBalance(balance: String) {
        _uiState.update { it.copy(balance = balance) }
    }

    fun updateNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun extractBalanceFromImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImageAnalyzing = true, errorMessage = null) }

            val analyzer = LlmdImageAnalyzer(llmdServiceConnection)
            val result = analyzer.extractBalanceFromImage(uri, getApplication())

            result.onSuccess { balance ->
                _uiState.update {
                    it.copy(
                        balance = "%.2f".format(balance),
                        isImageAnalyzing = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isImageAnalyzing = false,
                        errorMessage = error.message ?: "Failed to extract balance",
                    )
                }
            }
        }
    }

    fun saveBalanceChange(onSuccess: () -> Unit) {
        val state = _uiState.value
        val fundSource = state.selectedFundSource ?: return
        val balance = state.balance.toDoubleOrNull() ?: return

        viewModelScope.launch {
            val currentBalance = repository.getBalanceChangesByFundSource(fundSource.id)
                .firstOrNull()
                ?.firstOrNull()
                ?.newBalance

            val balanceChange = BalanceChange(
                fundSourceId = fundSource.id,
                newBalance = balance,
                previousBalance = currentBalance,
                note = state.note.ifBlank { null },
            )

            repository.insertBalanceChange(balanceChange)
            onSuccess()
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmdServiceConnection.close()
    }

    class Factory(
        private val application: Application,
        private val repository: DataRepository,
        private val llmdServiceConnection: LlmdServiceConnection,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return AddBalanceChangeViewModel(application, repository, llmdServiceConnection) as T
        }
    }
}

data class AddBalanceChangeUiState(
    val selectedFundSource: FundSource? = null,
    val balance: String = "",
    val note: String = "",
    val isImageAnalyzing: Boolean = false,
    val errorMessage: String? = null,
)
