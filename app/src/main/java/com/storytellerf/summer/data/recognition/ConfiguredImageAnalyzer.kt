package com.storytellerf.summer.data.recognition

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.storytellerf.summer.data.recognition.BalanceImageAnalyzer
import com.storytellerf.summer.data.llmd.LlmdImageAnalyzer
import com.storytellerf.summer.data.llmd.LlmdServiceConnection
import com.storytellerf.summer.data.llmd.LlmdTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Takes a single settings snapshot for each request; changing settings never reroutes an in-flight image. */
class ConfiguredImageAnalyzer(
    private val settings: RecognitionSettings,
    private val llmd: BalanceImageAnalyzer,
    private val readJpeg: suspend (String) -> ByteArray,
    private val remote: RemoteBalanceRecognizer = KoogBalanceRecognizer(),
) : BalanceImageAnalyzer {
    override suspend fun extractBalanceFromImage(imageReference: String, target: LlmdTarget): Result<Double> {
        return try {
            val config = settings.config.first()
            if (config.backend == RecognitionBackend.Llmd) {
                llmd.extractBalanceFromImage(imageReference, target)
            } else {
                val connection = config.connectionFor().validated()
                Result.success(remote.recognize(config.backend, connection, readJpeg(imageReference)))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override fun close() = llmd.close()
}

fun configuredImageAnalyzer(
    context: Context,
    io: CoroutineDispatcher = Dispatchers.IO,
    remote: RemoteBalanceRecognizer = KoogBalanceRecognizer(),
): BalanceImageAnalyzer {
    val appContext = context.applicationContext
    val encoder = BalanceImageEncoder(appContext)
    return ConfiguredImageAnalyzer(
        settings = DataStoreRecognitionSettings(appContext),
        llmd = LlmdImageAnalyzer(appContext, LlmdServiceConnection(appContext), io),
        readJpeg = { reference -> withContext(io) { encoder.encode(reference.toUri()) } },
        remote = RemoteBalanceRecognizer { backend, connection, jpeg ->
            val started = SystemClock.elapsedRealtime()
            try {
                remote.recognize(backend, connection, jpeg).also {
                    Log.i("BalanceRecognition", "operation=recognize provider=${backend.name} outcome=success duration_ms=${SystemClock.elapsedRealtime() - started}")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val connectionError = error as? RecognitionConnectionException
                Log.w("BalanceRecognition", "operation=recognize provider=${backend.name} outcome=failure status=${connectionError?.statusCode} category=${connectionError?.category ?: error.javaClass.simpleName}")
                throw error
            }
        },
    )
}
