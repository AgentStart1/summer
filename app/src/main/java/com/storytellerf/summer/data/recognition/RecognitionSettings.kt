package com.storytellerf.summer.data.recognition

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface RecognitionSettings {
    val config: Flow<RecognitionConfig>
    suspend fun save(backend: RecognitionBackend, connection: KoogConnection)
    suspend fun clearConnection(backend: RecognitionBackend)
}

private object RecognitionStore {
    // One process-wide DataStore per file, including when multiple screens are open.
    val stores = java.util.concurrent.ConcurrentHashMap<String, DataStore<Preferences>>()
    fun get(context: Context): DataStore<Preferences> {
        val file = File(context.noBackupFilesDir, "recognition.preferences_pb")
        return stores.computeIfAbsent(file.absolutePath) {
            PreferenceDataStoreFactory.create { file }
        }
    }
}

class DataStoreRecognitionSettings(context: Context) : RecognitionSettings {
    private val store = RecognitionStore.get(context.applicationContext)
    private val secrets = RecognitionKeyCipher()

    override val config: Flow<RecognitionConfig> = store.data.map { preferences ->
        RecognitionConfig(
            backend = preferences[BACKEND]?.let { name -> RecognitionBackend.entries.find { it.name == name } }
                ?: RecognitionBackend.Llmd,
            connections = RecognitionBackend.entries.filter { it != RecognitionBackend.Llmd }.associateWith { backend ->
                val defaults = RecognitionConfig().connectionFor(backend)
                KoogConnection(
                    baseUrl = preferences[textKey(backend, "url")] ?: defaults.baseUrl,
                    model = preferences[textKey(backend, "model")] ?: defaults.model,
                    apiKey = preferences[textKey(backend, "secret")]?.let(secrets::decrypt).orEmpty(),
                    useResponsesApi = preferences[booleanPreferencesKey("${backend.name}_responses")]
                        ?: defaults.useResponsesApi,
                )
            },
        )
    }

    override suspend fun save(backend: RecognitionBackend, connection: KoogConnection) {
        val validated = if (backend == RecognitionBackend.Llmd) connection else connection.validated()
        store.edit { preferences ->
            if (backend != RecognitionBackend.Llmd) {
                preferences[textKey(backend, "url")] = validated.baseUrl
                preferences[textKey(backend, "model")] = validated.model
                preferences[textKey(backend, "secret")] = secrets.encrypt(validated.apiKey)
                preferences[booleanPreferencesKey("${backend.name}_responses")] = validated.useResponsesApi
            }
            preferences[BACKEND] = backend.name
        }
    }

    override suspend fun clearConnection(backend: RecognitionBackend) {
        store.edit { preferences ->
            listOf("url", "model", "secret").forEach { preferences.remove(textKey(backend, it)) }
            preferences.remove(booleanPreferencesKey("${backend.name}_responses"))
            if (preferences[BACKEND] == backend.name) preferences[BACKEND] = RecognitionBackend.Llmd.name
        }
    }

    private companion object {
        val BACKEND = stringPreferencesKey("backend")
        fun textKey(backend: RecognitionBackend, field: String) = stringPreferencesKey("${backend.name}_$field")
    }
}

private class RecognitionKeyCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return "${encode(cipher.iv)}:${encode(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))}"
    }

    fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Saved API key is invalid. Save the connection again." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        }
        return cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
    }

    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALIAS = "summer-recognition-api-keys"
    }
}
