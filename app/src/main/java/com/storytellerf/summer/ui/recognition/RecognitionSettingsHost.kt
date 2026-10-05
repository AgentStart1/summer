package com.storytellerf.summer.ui.recognition

import com.storytellerf.summer.data.recognition.KoogConnection
import com.storytellerf.summer.data.recognition.RecognitionBackend
import com.storytellerf.summer.data.recognition.RecognitionConfig
import com.storytellerf.summer.data.recognition.RecognitionSettings
import com.storytellerf.summer.ui.host.AppDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecognitionSettingsState(
    val backend: RecognitionBackend = RecognitionBackend.Llmd,
    val connection: KoogConnection = KoogConnection(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
)

class RecognitionSettingsHost(
    private val settings: RecognitionSettings,
    parentScope: CoroutineScope,
    private val dispatchers: AppDispatchers,
) : AutoCloseable {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[kotlinx.coroutines.Job]) +
        dispatchers.coordination)
    private val state = MutableStateFlow(RecognitionSettingsState())
    val uiState = state.asStateFlow()
    private var saved = RecognitionConfig()
    private var drafts = emptyMap<RecognitionBackend, KoogConnection>()

    init {
        scope.launch {
            try {
                saved = withContext(dispatchers.io) { settings.config.first() }
                state.value = RecognitionSettingsState(saved.backend, saved.connectionFor(), loading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.value = RecognitionSettingsState(loading = false, message = "Could not load recognition settings")
            }
        }
    }

    fun selectBackend(backend: RecognitionBackend) = scope.launch {
        if (state.value.loading || state.value.saving) return@launch
        drafts = drafts + (state.value.backend to state.value.connection)
        state.value = state.value.copy(backend = backend, connection = drafts[backend] ?: saved.connectionFor(backend), message = null)
    }

    fun updateConnection(transform: (KoogConnection) -> KoogConnection) = scope.launch {
        if (!state.value.loading && !state.value.saving) {
            state.value = state.value.copy(connection = transform(state.value.connection), message = null)
        }
    }

    fun save() = scope.launch {
        val snapshot = state.value
        if (snapshot.loading || snapshot.saving) return@launch
        val validated = try {
            if (snapshot.backend == RecognitionBackend.Llmd) snapshot.connection else snapshot.connection.validated()
        } catch (error: IllegalArgumentException) {
            state.value = snapshot.copy(message = error.message)
            return@launch
        }
        state.value = snapshot.copy(saving = true, message = null)
        try {
            withContext(dispatchers.io) { settings.save(snapshot.backend, validated) }
            saved = saved.copy(backend = snapshot.backend, connections = saved.connections + (snapshot.backend to validated))
            drafts = drafts + (snapshot.backend to validated)
            state.value = snapshot.copy(connection = validated, saving = false, message = "Recognition settings saved")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.value = snapshot.copy(saving = false, message = "Could not save recognition settings")
        }
    }

    fun clearSavedConnection() = scope.launch {
        val snapshot = state.value
        if (snapshot.loading || snapshot.saving || snapshot.backend == RecognitionBackend.Llmd) return@launch
        state.value = snapshot.copy(saving = true, message = null)
        try {
            withContext(dispatchers.io) { settings.clearConnection(snapshot.backend) }
            saved = saved.copy(
                backend = if (saved.backend == snapshot.backend) RecognitionBackend.Llmd else saved.backend,
                connections = saved.connections - snapshot.backend,
            )
            drafts = drafts - snapshot.backend
            state.value = RecognitionSettingsState(saved.backend, saved.connectionFor(), loading = false,
                message = "Saved connection removed")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            state.value = snapshot.copy(saving = false, message = "Could not remove the saved connection")
        }
    }

    override fun close() = scope.cancel()
}
