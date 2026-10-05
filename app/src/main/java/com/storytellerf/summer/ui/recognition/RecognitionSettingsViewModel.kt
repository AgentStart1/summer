package com.storytellerf.summer.ui.recognition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.storytellerf.summer.data.recognition.RecognitionSettings
import com.storytellerf.summer.ui.host.AppDispatchers

class RecognitionSettingsViewModel(settings: RecognitionSettings) : ViewModel() {
    val host = RecognitionSettingsHost(settings, viewModelScope, AppDispatchers.Runtime)
    override fun onCleared() {
        host.close()
    }

    class Factory(private val settings: RecognitionSettings) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecognitionSettingsViewModel::class.java))
            return RecognitionSettingsViewModel(settings) as T
        }
    }
}
