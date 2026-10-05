package com.storytellerf.summer.ui.recognition

import com.storytellerf.summer.data.recognition.*
import com.storytellerf.summer.testing.createHostTestEnvironment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecognitionSettingsHostTest {
    @Test fun providerDrafts_surviveSwitchingAndOnlySaveChangesActiveProvider() = runTest {
        val environment = createHostTestEnvironment()
        val settings = RecognitionTestSettings()
        val host = RecognitionSettingsHost(settings, environment.scope, environment.dispatchers)
        advanceUntilIdle()
        host.selectBackend(RecognitionBackend.OpenRouter)
        host.updateConnection { it.copy(model = "test-vision:free", apiKey = "test-key") }
        host.selectBackend(RecognitionBackend.OpenAI)
        host.selectBackend(RecognitionBackend.OpenRouter)
        advanceUntilIdle()
        assertEquals("test-key", host.uiState.value.connection.apiKey)
        assertEquals(RecognitionBackend.Llmd, settings.config.value.backend)
        host.save()
        advanceUntilIdle()
        assertEquals(RecognitionBackend.OpenRouter, settings.config.value.backend)
        assertEquals("test-vision:free", settings.config.value.connectionFor().model)
        assertFalse(host.uiState.value.saving)
        host.close()
        environment.close()
    }

    @Test fun invalidEndpoint_isRejectedWithoutChangingSavedProvider() = runTest {
        val environment = createHostTestEnvironment()
        val settings = RecognitionTestSettings()
        val host = RecognitionSettingsHost(settings, environment.scope, environment.dispatchers)
        advanceUntilIdle()
        host.selectBackend(RecognitionBackend.OpenAI)
        host.updateConnection { it.copy(baseUrl = "https://example.com/v1?key=secret", apiKey = "test-key") }
        host.save()
        advanceUntilIdle()
        assertEquals(RecognitionBackend.Llmd, settings.config.value.backend)
        assertNotNull(host.uiState.value.message)
        assertFalse(host.uiState.value.message!!.contains("secret"))
        host.close()
        environment.close()
    }

    @Test fun removingActiveConnection_erasesKeyAndDraftAndReturnsToLlmd() = runTest {
        val environment = createHostTestEnvironment()
        val settings = RecognitionTestSettings(RecognitionConfig(
            backend = RecognitionBackend.OpenRouter,
            connections = mapOf(RecognitionBackend.OpenRouter to KoogConnection(
                "https://openrouter.ai/api/v1", "test-vision:free", "test-key")),
        ))
        val host = RecognitionSettingsHost(settings, environment.scope, environment.dispatchers)
        advanceUntilIdle()
        host.clearSavedConnection()
        advanceUntilIdle()
        assertEquals(RecognitionBackend.Llmd, host.uiState.value.backend)
        assertFalse(settings.config.value.connections.containsKey(RecognitionBackend.OpenRouter))
        host.selectBackend(RecognitionBackend.OpenRouter)
        advanceUntilIdle()
        assertEquals("", host.uiState.value.connection.apiKey)
        host.close()
        environment.close()
    }
}
