package com.example.summerapp.data.llmd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LlmdImageAnalyzerTest {
    @Test
    fun parseBalance_preservesNegativeSign() {
        assertEquals(-1234.56, parseBalanceFromText("-1,234.56"), 0.0)
    }

    @Test
    fun parseBalance_acceptsUnicodeMinus() {
        assertEquals(-45.67, parseBalanceFromText("−45.67"), 0.0)
    }

    @Test
    fun parseBalance_rejectsResponsesWithoutNumbers() {
        assertThrows(IllegalArgumentException::class.java) {
            parseBalanceFromText("No balance found")
        }
    }

    @Test
    fun request_usesEncodedImageMimeType() {
        assertEquals(
            "data:image/jpeg;base64,YWJj",
            buildImageDataUrl(EncodedImage(mimeType = "image/jpeg", base64Data = "YWJj")),
        )
    }
}
