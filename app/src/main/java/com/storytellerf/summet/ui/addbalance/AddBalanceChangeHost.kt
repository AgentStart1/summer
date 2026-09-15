package com.storytellerf.summet.ui.addbalance

import com.storytellerf.summet.data.DataRepository
import com.storytellerf.summet.data.db.entity.BalanceChange
import com.storytellerf.summet.data.db.entity.FundSource
import com.storytellerf.summet.data.llmd.BalanceImageAnalyzer
import com.storytellerf.summet.data.llmd.LlmdAuthorizationException
import com.storytellerf.summet.data.llmd.LlmdTarget
import com.storytellerf.summet.ui.host.AppDispatchers
import com.storytellerf.summet.ui.host.withDispatcher
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddBalanceChangeHost(
    private val repository: DataRepository,
    private val imageAnalyzer: BalanceImageAnalyzer,
    private val scope: CoroutineScope,
    private val dispatchers: AppDispatchers,
    private val imageAnalysisTarget: Flow<LlmdTarget> = flowOf(LlmdTarget.Release),
) : AutoCloseable {
    private val formState = MutableStateFlow(AddBalanceChangeUiState())
    private val pendingImageReference = MutableStateFlow<String?>(null)
    private val mutableEffects = MutableSharedFlow<AddBalanceChangeEffect>()
    private var imageAnalysisJob: Job? = null

    val effects: SharedFlow<AddBalanceChangeEffect> = mutableEffects.asSharedFlow()

    val uiState: StateFlow<AddBalanceChangeUiState> = combine(
        formState,
        repository.getAllFundSources(),
    ) { state, fundSources ->
        state.copy(fundSources = fundSources.toList())
    }
        .flowOn(dispatchers.default)
        .catch { error ->
            emit(
                formState.value.copy(
                    errorMessage = error.message ?: "Failed to load fund sources",
                )
            )
        }
        .stateIn(
            scope = scope.withDispatcher(dispatchers.default),
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AddBalanceChangeUiState(),
        )

    fun selectFundSource(fundSource: FundSource) {
        formState.update { it.copy(selectedFundSource = fundSource, errorMessage = null) }
    }

    fun updateBalance(balance: String) {
        formState.update { it.copy(balance = balance, errorMessage = null) }
    }

    fun updateNote(note: String) {
        formState.update { it.copy(note = note) }
    }

    fun extractBalanceFromImage(imageReference: String) {
        pendingImageReference.value = imageReference
        analyzeImage(imageReference)
    }

    private fun analyzeImage(imageReference: String) {
        imageAnalysisJob?.cancel()
        imageAnalysisJob = scope.launch(dispatchers.io) {
            formState.update {
                it.copy(
                    isImageAnalyzing = true,
                    errorMessage = null,
                )
            }

            val target = imageAnalysisTarget.first()
            val result = imageAnalyzer.extractBalanceFromImage(imageReference, target)
            result.onSuccess { balance ->
                pendingImageReference.value = null
                formState.update {
                    it.copy(
                        balance = formatBalanceForInput(balance),
                        isImageAnalyzing = false,
                    )
                }
            }.onFailure { error ->
                if (error is LlmdAuthorizationException) {
                    formState.update { it.copy(isImageAnalyzing = false) }
                    mutableEffects.emit(AddBalanceChangeEffect.RequestAuthorization(target))
                } else {
                    pendingImageReference.value = null
                    formState.update {
                        it.copy(
                            isImageAnalyzing = false,
                            errorMessage = error.message ?: "Failed to extract balance",
                        )
                    }
                }
            }
        }
    }

    fun onAuthorizationResult(authorized: Boolean) {
        val imageReference = pendingImageReference.value
        if (authorized && imageReference != null) {
            analyzeImage(imageReference)
        } else {
            pendingImageReference.value = null
            formState.update {
                it.copy(errorMessage = "llmd authorization was not granted")
            }
        }
    }

    fun saveBalanceChange() {
        val state = formState.value
        val fundSource = state.selectedFundSource
        if (fundSource == null) {
            formState.update { it.copy(errorMessage = "Select a fund source") }
            return
        }
        val balance = parseBalanceInput(state.balance)
        if (balance == null) {
            formState.update { it.copy(errorMessage = "Enter a valid balance") }
            return
        }
        if (state.isSaving) return

        formState.update { it.copy(isSaving = true, errorMessage = null) }
        scope.launch(dispatchers.io) {
            try {
                val currentBalance = repository.getBalanceChangesByFundSource(fundSource.id)
                    .firstOrNull()
                    ?.firstOrNull()
                    ?.newBalance

                repository.insertBalanceChange(
                    BalanceChange(
                        fundSourceId = fundSource.id,
                        newBalance = balance,
                        previousBalance = currentBalance,
                        note = state.note.ifBlank { null },
                    )
                )
                pendingImageReference.value = null
                formState.value = AddBalanceChangeUiState()
                mutableEffects.emit(AddBalanceChangeEffect.Saved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                formState.update {
                    it.copy(errorMessage = error.message ?: "Failed to save balance")
                }
            } finally {
                formState.update { it.copy(isSaving = false) }
            }
        }
    }

    override fun close() {
        imageAnalysisJob?.cancel()
        imageAnalyzer.close()
    }
}

sealed interface AddBalanceChangeEffect {
    data object Saved : AddBalanceChangeEffect
    data class RequestAuthorization(val target: LlmdTarget) : AddBalanceChangeEffect
}

data class AddBalanceChangeUiState(
    val fundSources: List<FundSource> = emptyList(),
    val selectedFundSource: FundSource? = null,
    val balance: String = "",
    val note: String = "",
    val isImageAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

internal fun formatBalanceForInput(balance: Double): String =
    String.format(Locale.ROOT, "%.2f", balance)

internal fun parseBalanceInput(value: String, locale: Locale = Locale.getDefault()): Double? {
    val text = value.trim()
    if (text.isEmpty()) return null
    text.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { return it }

    val position = ParsePosition(0)
    val parsed = NumberFormat.getNumberInstance(locale).parse(text, position)?.toDouble()
    return parsed?.takeIf { position.index == text.length && it.isFinite() }
}
