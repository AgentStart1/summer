package com.storytellerf.summer.data.recognition

import com.storytellerf.summer.data.recognition.BalanceImageAnalyzer
import com.storytellerf.summer.data.llmd.LlmdTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfiguredImageAnalyzerTest {
    @Test fun switchingBackend_routesImageToSelectedConnectionAndPreservesLlmdTarget() = runTest {
        val settings = RecognitionTestSettings()
        val llmd = object : BalanceImageAnalyzer {
            var selected: LlmdTarget? = null
            override suspend fun extractBalanceFromImage(imageReference: String, target: LlmdTarget): Result<Double> {
                selected = target
                return Result.success(12.0)
            }
        }
        var uploads = 0
        val connection = KoogConnection("https://openrouter.ai/api/v1", "test-vision:free", "test-key")
        val analyzer = ConfiguredImageAnalyzer(settings, llmd, { byteArrayOf(1, 2) },
            RemoteBalanceRecognizer { backend, actual, jpeg ->
                uploads++
                assertEquals(RecognitionBackend.OpenRouter, backend)
                assertEquals(connection, actual)
                assertArrayEquals(byteArrayOf(1, 2), jpeg)
                -245.70
            })
        assertEquals(12.0, analyzer.extractBalanceFromImage("test-image", LlmdTarget.Alpha).getOrThrow(), 0.0)
        assertEquals(LlmdTarget.Alpha, llmd.selected)
        assertEquals(0, uploads)
        settings.save(RecognitionBackend.OpenRouter, connection)
        assertEquals(-245.70, analyzer.extractBalanceFromImage("test-image", LlmdTarget.Debug).getOrThrow(), 0.0)
        assertEquals(1, uploads)
        settings.save(RecognitionBackend.Llmd, KoogConnection())
        assertEquals(12.0, analyzer.extractBalanceFromImage("test-image", LlmdTarget.Debug).getOrThrow(), 0.0)
        assertEquals(LlmdTarget.Debug, llmd.selected)
        assertEquals(connection, settings.config.value.connectionFor(RecognitionBackend.OpenRouter))
    }

    @Test fun invalidConfiguration_doesNotReadOrUploadImage() = runTest {
        val settings = RecognitionTestSettings(RecognitionConfig(RecognitionBackend.OpenRouter))
        val analyzer = ConfiguredImageAnalyzer(settings, unusedLlmd(), { error("Image must not be read") },
            RemoteBalanceRecognizer { _, _, _ -> error("Image must not be sent") })
        assertTrue(analyzer.extractBalanceFromImage("test", LlmdTarget.Release).exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun remoteCancellation_isPropagated() = runTest {
        val settings = RecognitionTestSettings()
        settings.save(RecognitionBackend.OpenRouter, KoogConnection("https://example.com/v1", "vision", "test-key"))
        val analyzer = ConfiguredImageAnalyzer(settings, unusedLlmd(), { byteArrayOf(1) },
            RemoteBalanceRecognizer { _, _, _ -> throw CancellationException("cancelled") })
        try {
            analyzer.extractBalanceFromImage("test", LlmdTarget.Release)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    private fun unusedLlmd() = object : BalanceImageAnalyzer {
        override suspend fun extractBalanceFromImage(imageReference: String, target: LlmdTarget): Result<Double> =
            error("LLMD must not be used")
    }
}
