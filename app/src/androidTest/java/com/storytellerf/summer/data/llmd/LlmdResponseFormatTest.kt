package com.storytellerf.summer.data.llmd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmdResponseFormatTest {
    @Test fun multiBalanceRequestUsesSelectedAccountsAndVariantImageUri() {
        val uri = "content://com.storytellerf.summer.debug.llmd-images/image.jpg"
        val request = JSONObject(buildBalancesExtractionRequest(uri, listOf(
            com.storytellerf.summer.data.recognition.BalanceReadTarget(7, "Wallet", "Available balance", "USD"))))
        val content = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(uri, content.getJSONObject(1).getJSONObject("image_url").getString("url"))
        assertTrue(content.getJSONObject(0).getString("text").contains("Available balance"))
        assertTrue(content.getJSONObject(0).getString("text").contains("USD"))
        assertTrue(request.getJSONObject("response_format").getJSONObject("json_schema")
            .getJSONObject("schema").getJSONObject("properties").has("balances"))
    }

    @Test fun serviceErrorsRemainActionable_andAuthorizationStillRequestsConsent() {
        val error = runCatching { extractResponseContent(
            """{"error":{"type":"llmd_error","message":"LiteRT-LM engine is not ready"}}""") }.exceptionOrNull()
        assertTrue(error is LlmdRecognitionException)
        assertTrue(error!!.message!!.contains("engine is unavailable"))
        assertTrue(runCatching { extractResponseContent(
            """{"error":{"type":"authorization_required"}}""") }.exceptionOrNull() is LlmdAuthorizationException)
    }

    @Test
    fun transactionRequestRequiresOriginalTransactionId_andStrictSchema() {
        val request = JSONObject(buildTransactionExtractionRequest("content://test/image.jpg", "EUR"))
        assertTrue(request.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            .getJSONObject(0).getString("text").contains("selected account currency is EUR"))
        val schema = request.getJSONObject("response_format").getJSONObject("json_schema")
        assertTrue(schema.getBoolean("strict"))
        val row = schema.getJSONObject("schema").getJSONObject("properties")
            .getJSONObject("transactions").getJSONObject("items")
        assertTrue(row.getJSONArray("required").toString().contains("transactionId"))
        assertTrue(row.getJSONObject("properties").has("transactionId"))
    }

}
