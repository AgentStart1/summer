package com.storytellerf.summer.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.storytellerf.summer.data.DataRepository
import com.storytellerf.summer.ui.host.AppDispatchers
import kotlinx.coroutines.flow.StateFlow

class FeedViewModel(
    repository: DataRepository,
    dispatchers: AppDispatchers = AppDispatchers.Runtime,
) : ViewModel() {
    private val host = FeedHost(repository, viewModelScope, dispatchers)
    val uiState: StateFlow<FeedUiState> = host.uiState

    class Factory(private val repository: DataRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FeedViewModel(repository) as T
        }
    }
}
