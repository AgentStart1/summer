package com.storytellerf.summer.data.recognition

import java.net.URI

enum class RecognitionBackend(val displayName: String, val defaultBaseUrl: String, val defaultModel: String) {
    Llmd("LLMD", "", ""),
    OpenAI("OpenAI", "https://api.openai.com/v1", "gpt-4.1-mini"),
    Anthropic("Anthropic (Claude)", "https://api.anthropic.com/v1", "claude-haiku-4-5"),
    OpenRouter("OpenRouter", "https://openrouter.ai/api/v1", "qwen/qwen3.8-27b:free"),
    OpenAICompatible("OpenAI-compatible", "", ""),
}

data class KoogConnection(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val useResponsesApi: Boolean = false,
) {
    override fun toString(): String =
        "KoogConnection(baseUrl=$baseUrl, model=$model, apiKey=<redacted>, useResponsesApi=$useResponsesApi)"

    fun validated(): KoogConnection {
        val normalized = copy(baseUrl = baseUrl.trim().trimEnd('/'), model = model.trim(), apiKey = apiKey.trim())
        val uri = runCatching { URI(normalized.baseUrl) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Enter an HTTPS API base URL without credentials, query parameters, or fragments"
        }
        require(normalized.model.isNotBlank()) { "Enter a model that supports image input" }
        require(normalized.apiKey.isNotBlank()) { "Enter an API key" }
        require(normalized.apiKey.none { it.isWhitespace() || it.isISOControl() }) { "API keys cannot contain whitespace" }
        return normalized
    }
}

data class RecognitionConfig(
    val backend: RecognitionBackend = RecognitionBackend.Llmd,
    val connections: Map<RecognitionBackend, KoogConnection> = emptyMap(),
) {
    fun connectionFor(backend: RecognitionBackend = this.backend): KoogConnection =
        connections[backend] ?: KoogConnection(
            baseUrl = backend.defaultBaseUrl,
            model = backend.defaultModel,
            useResponsesApi = backend == RecognitionBackend.OpenAI,
        )
}
