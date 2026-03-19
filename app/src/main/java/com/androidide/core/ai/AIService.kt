package com.androidide.core.ai

import com.androidide.data.models.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIService @Inject constructor() {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var config = AIConfig()

    fun updateConfig(newConfig: AIConfig) { config = newConfig }

    // ── Generate code from prompt ──────────────────────────────────────────────
    suspend fun generateCode(
        prompt: String,
        contextCode: String = "",
        language: String = "Kotlin"
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
You are an expert Android developer specializing in Kotlin and Jetpack Compose.
Generate clean, production-ready $language code.
Return ONLY the code without any markdown fences or explanations unless asked.
Current file context:
```
${contextCode.take(2000)}
```
        """.trimIndent()

        callAI(listOf(
            AIMessage("system", systemPrompt),
            AIMessage("user", prompt)
        ))
    }

    // ── Fix error in code ──────────────────────────────────────────────────────
    suspend fun fixError(
        code: String,
        errorMessage: String,
        fileName: String = ""
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
File: $fileName
Error: $errorMessage

Code:
```kotlin
${code.take(3000)}
```

Please fix this error. Return the corrected code only.
        """.trimIndent()

        callAI(listOf(AIMessage("user", prompt)))
    }

    // ── Explain code ───────────────────────────────────────────────────────────
    suspend fun explainCode(code: String): String = withContext(Dispatchers.IO) {
        callAI(listOf(AIMessage("user", "Explain this Android/Kotlin code clearly:\n\n```kotlin\n${code.take(2000)}\n```")))
    }

    // ── Refactor code ──────────────────────────────────────────────────────────
    suspend fun refactorCode(code: String, instruction: String): String = withContext(Dispatchers.IO) {
        callAI(listOf(AIMessage("user",
            "Refactor this code: $instruction\n\n```kotlin\n${code.take(2000)}\n```\n\nReturn only the refactored code."
        )))
    }

    // ── Chat with context ──────────────────────────────────────────────────────
    suspend fun chat(
        messages: List<AIMessage>,
        projectContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        val systemMsg = AIMessage("system", """
You are an expert Android developer assistant embedded in an IDE.
You help with Kotlin, Java, Jetpack Compose, Android SDK, Gradle, Git, and general development.
Be concise and practical. Provide code examples when helpful.
${if (projectContext.isNotEmpty()) "Current project context:\n$projectContext" else ""}
        """.trimIndent())
        callAI(listOf(systemMsg) + messages)
    }

    // ── Core API call ──────────────────────────────────────────────────────────
    private suspend fun callAI(messages: List<AIMessage>): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isEmpty()) return@withContext "⚠ API key not configured. Please add your API key in Settings → AI Configuration."

        return@withContext when (config.provider) {
            AIProvider.OPENAI, AIProvider.CLAUDE, AIProvider.CUSTOM -> callOpenAICompatible(messages)
            AIProvider.GEMINI -> callGemini(messages)
        }
    }

    private fun callOpenAICompatible(messages: List<AIMessage>): String {
        val body = OpenAIRequest(
            model = config.model,
            messages = messages.map { OpenAIMessage(role = it.role, content = it.content) },
            maxTokens = 2000,
            temperature = 0.3
        )
        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "API Error ${response.code}: ${response.message}"
                val json = response.body?.string() ?: return "Empty response"
                val resp = gson.fromJson(json, OpenAIResponse::class.java)
                resp.choices?.firstOrNull()?.message?.content ?: "No response content"
            }
        } catch (e: Exception) { "Network error: ${e.message}" }
    }

    private fun callGemini(messages: List<AIMessage>): String {
        val prompt = messages.filter { it.role != "system" }.joinToString("\n\n") {
            "${it.role.uppercase()}: ${it.content}"
        }
        val systemContent = messages.find { it.role == "system" }?.content ?: ""
        val body = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart("$systemContent\n\n$prompt")))),
            generationConfig = GeminiGenerationConfig(temperature = 0.3f, maxOutputTokens = 2000)
        )
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent?key=${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Gemini Error ${response.code}: ${response.message}"
                val json = response.body?.string() ?: return "Empty response"
                val resp = gson.fromJson(json, GeminiResponse::class.java)
                resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response"
            }
        } catch (e: Exception) { "Network error: ${e.message}" }
    }
}

// ── OpenAI API DTOs ────────────────────────────────────────────────────────────
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerializedName("max_tokens") val maxTokens: Int,
    val temperature: Double
)
data class OpenAIMessage(val role: String, val content: String)
data class OpenAIResponse(val choices: List<OpenAIChoice>?)
data class OpenAIChoice(val message: OpenAIMessage?)

// ── Gemini API DTOs ────────────────────────────────────────────────────────────
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig
)
data class GeminiContent(val parts: List<GeminiPart>)
data class GeminiPart(val text: String)
data class GeminiGenerationConfig(val temperature: Float, @SerializedName("maxOutputTokens") val maxOutputTokens: Int)
data class GeminiResponse(val candidates: List<GeminiCandidate>?)
data class GeminiCandidate(val content: GeminiContent?)
