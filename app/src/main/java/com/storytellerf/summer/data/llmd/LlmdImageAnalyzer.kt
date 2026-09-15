package com.storytellerf.summer.data.llmd

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

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
        var preparedImage: PreparedImage? = null
        return try {
            preparedImage = withContext(ioDispatcher) { prepareImage(imageReference.toUri()) }
            appContext.grantUriPermission(
                target.packageName,
                preparedImage.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val requestJson = buildBalanceExtractionRequest(preparedImage.uri.toString())
            val responseJson = serviceConnection.chatCompletion(target, requestJson)
            parseBalanceFromResponse(responseJson)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } finally {
            preparedImage?.let { image ->
                appContext.revokeUriPermission(image.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                withContext(ioDispatcher) { image.file.delete() }
            }
        }
    }

    override fun close() {
        serviceConnection.close()
    }

    private fun prepareImage(uri: Uri): PreparedImage {
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
            val imageDirectory = File(appContext.cacheDir, "llmd-images").apply { mkdirs() }
            val file = File.createTempFile("balance-", ".jpg", imageDirectory)
            FileOutputStream(file).use { it.write(bytes) }
            PreparedImage(
                uri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.llmd-images",
                    file,
                ),
                file = file,
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

private data class PreparedImage(
    val uri: Uri,
    val file: File,
)

internal fun buildBalanceExtractionRequest(imageUrl: String): String {
    val content = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "text")
            put("text", BALANCE_EXTRACTION_PROMPT)
        })
        put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", imageUrl)
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
        put("response_format", buildBalanceResponseFormat())
    }.toString()
}

private fun buildBalanceResponseFormat(): JSONObject = JSONObject().apply {
    put("type", "json_schema")
    put("json_schema", JSONObject().apply {
        put("name", "balance_extraction")
        put("strict", true)
        put("schema", JSONObject().apply {
            put("type", "number")
            put("description", "The balance amount shown in the screenshot")
        })
    })
}

internal fun parseBalanceFromResponse(responseJson: String): Result<Double> {
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
            val balance = JSONTokener(content).nextValue()
            require(balance is Number) { "Structured balance response is not a number" }
            Result.success(balance.toDouble())
        } else {
            Result.failure(Exception("No balance found in image"))
        }
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private const val MODEL_NAME = "gemma-4-E2B-it"

const val BALANCE_EXTRACTION_PROMPT = """
Extract the balance amount from this screenshot.
Return the numeric value, including a leading minus sign when the balance is negative.
Do not include currency symbols in the value.
For example, ¥1,234.56 is 1234.56 and -¥45.67 is -45.67.
If you cannot find a balance, return 0.
"""

class LlmdAuthorizationException :
    Exception("Authorization required. Please authorize the app in llmd.")
