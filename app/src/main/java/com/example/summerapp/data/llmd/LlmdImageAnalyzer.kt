package com.example.summerapp.data.llmd

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class LlmdImageAnalyzer(private val serviceConnection: LlmdServiceConnection) {

    suspend fun extractBalanceFromImage(
        imageUri: Uri,
        context: Context,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<Double> {
        return try {
            val base64Image = readImageAsBase64(imageUri, context)
            val requestJson = buildBalanceExtractionRequest(base64Image)
            val responseJson = serviceConnection.chatCompletion(target, requestJson)
            parseBalanceFromResponse(responseJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readImageAsBase64(uri: Uri, context: Context): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image URI")
        val bytes = inputStream.readBytes()
        inputStream.close()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun buildBalanceExtractionRequest(base64Image: String): String {
        val content = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "text")
                put("text", BALANCE_EXTRACTION_PROMPT)
            })
            put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64Image")
                })
            })
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            })
        }

        return JSONObject().apply {
            put("model", MODEL_NAME)
            put("messages", messages)
            put("max_tokens", 100)
        }.toString()
    }

    private fun parseBalanceFromResponse(responseJson: String): Result<Double> {
        return try {
            val response = JSONObject(responseJson)
            val error = response.optJSONObject("error")
            if (error != null) {
                val errorType = error.optString("type", "unknown")
                if (errorType == "authorization_required") {
                    return Result.failure(LlmdAuthorizationException())
                }
                return Result.failure(Exception(error.optString("message", "Unknown error")))
            }

            val choices = response.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                val balance = extractBalanceFromText(content)
                Result.success(balance)
            } else {
                Result.failure(Exception("No balance found in image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractBalanceFromText(text: String): Double {
        val balancePattern = Regex("""[\d,]+\.?\d*""")
        val match = balancePattern.find(text)
        return match?.value?.replace(",", "")?.toDoubleOrNull()
            ?: throw IllegalArgumentException("Could not parse balance from response: $text")
    }

    companion object {
        private const val MODEL_NAME = "gemma-4-E2B-it"

        const val BALANCE_EXTRACTION_PROMPT = """
Extract the balance amount from this screenshot.
Return ONLY the numeric value without any currency symbols or text.
For example, if the balance is ¥1,234.56, return: 1234.56
If you cannot find a balance, return: 0
"""
    }
}

class LlmdAuthorizationException :
    Exception("Authorization required. Please authorize the app in llmd.")
