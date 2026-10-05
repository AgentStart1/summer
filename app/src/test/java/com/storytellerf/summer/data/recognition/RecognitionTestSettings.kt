package com.storytellerf.summer.data.recognition

import kotlinx.coroutines.flow.MutableStateFlow

class RecognitionTestSettings(initial: RecognitionConfig = RecognitionConfig()) : RecognitionSettings {
    override val config = MutableStateFlow(initial)
    override suspend fun save(backend: RecognitionBackend, connection: KoogConnection) {
        config.value = config.value.copy(backend = backend, connections = config.value.connections + (backend to connection))
    }
    override suspend fun clearConnection(backend: RecognitionBackend) {
        config.value = config.value.copy(
            backend = if (config.value.backend == backend) RecognitionBackend.Llmd else config.value.backend,
            connections = config.value.connections - backend,
        )
    }
}
