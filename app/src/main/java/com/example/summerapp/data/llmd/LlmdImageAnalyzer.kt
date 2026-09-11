package com.example.summerapp.data.llmd

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface BalanceImageAnalyzer : AutoCloseable {
    suspend fun extractBalanceFromImage(
        imageReference: String,
        target: LlmdTarget = LlmdTarget.Release,
    ): Result<Double>

    override fun close() = Unit
}

class LlmdImageAnalyzer(
    context: Context,
    private val serviceConnection: LlmdServiceConnection,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BalanceImageAnalyzer {
    private val appContext = context.applicationContext

    override suspend fun extractBalanceFromImage(
        imageReference: String,
        target: LlmdTarget,
    ): Result<Double> {
        return try {
            val encodedImage = withContext(ioDispatcher) { encodeImage(imageReference.toUri()) }
            val requestJson = buildBalanceExtractionRequest(encodedImage)
            val responseJson = serviceConnection.chatCompletion(target, requestJson)
            parseBalanceFromResponse(responseJson)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override fun close() {
        serviceConnection.close()
    }

    private fun encodeImage(uri: Uri): EncodedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open image URI")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a valid image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Cannot decode selected image")

        var working = decoded.scaledToFit(MAX_IMAGE_DIMENSION)
        if (working !== decoded) decoded.recycle()
        if (working.hasAlpha()) {
            val flattened = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(working, 0f, 0f, null)
            }
            working.recycle()
            working = flattened
        }

        return try {
            var quality = INITIAL_JPEG_QUALITY
            var bytes = working.compressAsJpeg(quality)
            while (bytes.size > MAX_ENCODED_IMAGE_BYTES && quality > MIN_JPEG_QUALITY) {
                quality -= JPEG_QUALITY_STEP
                bytes = working.compressAsJpeg(quality)
            }
            require(bytes.size <= MAX_ENCODED_IMAGE_BYTES) {
                "Image is too detailed to send safely. Please crop it and try again."
            }
            EncodedImage(
                mimeType = JPEG_MIME_TYPE,
                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        } finally {
            working.recycle()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MAX_IMAGE_DIMENSION * 2 ||
            height / sampleSize > MAX_IMAGE_DIMENSION * 2
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.scaledToFit(maxDimension: Int): Bitmap {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= maxDimension) return this
        val scale = maxDimension.toFloat() / largestDimension
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Bitmap.compressAsJpeg(quality: Int): ByteArray {
        return ByteArrayOutputStream().use { output ->
            check(compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Failed to encode image" }
            output.toByteArray()
        }
    }

    companion object {
        private const val MAX_IMAGE_DIMENSION = 1600
        private const val MAX_ENCODED_IMAGE_BYTES = 500_000
        private const val INITIAL_JPEG_QUALITY = 85
        private const val MIN_JPEG_QUALITY = 45
        private const val JPEG_QUALITY_STEP = 10
        private const val JPEG_MIME_TYPE = "image/jpeg"
    }
}

internal data class EncodedImage(
    val mimeType: String,
    val base64Data: String,
)

internal fun buildBalanceExtractionRequest(encodedImage: EncodedImage): String {
    val content = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "text")
            put("text", BALANCE_EXTRACTION_PROMPT)
        })
        put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", buildImageDataUrl(encodedImage))
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

internal fun buildImageDataUrl(encodedImage: EncodedImage): String =
    "data:${encodedImage.mimeType};base64,${encodedImage.base64Data}"

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
            Result.success(parseBalanceFromText(content))
        } else {
            Result.failure(Exception("No balance found in image"))
        }
    } catch (error: Exception) {
        Result.failure(error)
    }
}

internal fun parseBalanceFromText(text: String): Double {
    val normalized = text.replace('\u2212', '-')
    val match = BALANCE_PATTERN.find(normalized)
    return match?.value?.replace(",", "")?.toDoubleOrNull()
        ?: throw IllegalArgumentException("Could not parse balance from response: $text")
}

private const val MODEL_NAME = "gemma-4-E2B-it"
private val BALANCE_PATTERN = Regex("""[-+]?(?:(?:\d{1,3}(?:,\d{3})+)|\d+)(?:\.\d+)?""")

const val BALANCE_EXTRACTION_PROMPT = """
Extract the balance amount from this screenshot.
Return ONLY the numeric value, including a leading minus sign when the balance is negative.
Do not include currency symbols or other text.
For example, if the balance is ¥1,234.56, return: 1234.56
If the balance is -¥45.67, return: -45.67
If you cannot find a balance, return: 0
"""

class LlmdAuthorizationException :
    Exception("Authorization required. Please authorize the app in llmd.")
