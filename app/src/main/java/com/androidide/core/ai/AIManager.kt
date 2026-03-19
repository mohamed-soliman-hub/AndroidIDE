package com.androidide.core.ai

import com.androidide.data.models.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIManager @Inject constructor() {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _config = MutableStateFlow(AIConfig())
    val config: StateFlow<AIConfig> = _config

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _chatHistory = MutableStateFlow<List<AIMessage>>(emptyList())
    val chatHistory: StateFlow<List<AIMessage>> = _chatHistory

    fun updateConfig(config: AIConfig) { _config.value = config }

    fun clearHistory() { _chatHistory.value = emptyList() }

    // ── Generate code from natural language ────────────────────────────────────
    suspend fun generateCode(
        prompt: String,
        currentFile: String = "",
        language: String = "Kotlin"
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = buildString {
            append("You are an expert Android developer specializing in Kotlin and Jetpack Compose. ")
            append("Generate clean, production-ready $language code. ")
            append("Return ONLY the code, no explanations unless asked. ")
            if (currentFile.isNotEmpty()) {
                append("\n\nContext from current file:\n```\n${currentFile.take(2000)}\n```")
            }
        }
        callAI(systemPrompt, prompt)
    }

    // ── Fix error with AI ──────────────────────────────────────────────────────
    suspend fun fixError(
        errorMessage: String,
        fileContent: String,
        fileName: String
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are an expert Android developer. Analyze the error and provide the corrected code. Return ONLY the fixed code with a brief comment explaining the fix."
        val userPrompt = buildString {
            append("Fix this error in $fileName:\n\n")
            append("ERROR:\n$errorMessage\n\n")
            append("FILE CONTENT:\n```\n${fileContent.take(3000)}\n```\n\n")
            append("Provide the complete corrected file.")
        }
        callAI(systemPrompt, userPrompt)
    }

    // ── Explain code ───────────────────────────────────────────────────────────
    suspend fun explainCode(code: String): String = withContext(Dispatchers.IO) {
        callAI(
            "You are an expert Android developer. Explain code clearly and concisely.",
            "Explain this code:\n```\n$code\n```"
        )
    }

    // ── Chat with context ──────────────────────────────────────────────────────
    suspend fun chat(
        userMessage: String,
        projectContext: ProjectContext? = null
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = buildString {
            append("You are an intelligent Android IDE assistant and expert developer. ")
            append("Help with code, architecture, debugging, and best practices. ")
            projectContext?.let { ctx ->
                append("\n\nProject context:\n")
                append("- Name: ${ctx.projectName}\n")
                append("- Language: ${ctx.language}\n")
                append("- Active file: ${ctx.activeFileName}\n")
                if (ctx.activeFileContent.isNotEmpty()) {
                    append("- File content:\n```\n${ctx.activeFileContent.take(2000)}\n```")
                }
            }
        }

        val userMsg = AIMessage(role = "user", content = userMessage)
        _chatHistory.value = _chatHistory.value + userMsg

        val response = callAI(systemPrompt, userMessage, includeHistory = true)

        val assistantMsg = AIMessage(role = "assistant", content = response)
        _chatHistory.value = _chatHistory.value + assistantMsg
        response
    }

    // ── Refactor code ──────────────────────────────────────────────────────────
    suspend fun refactorCode(code: String, instruction: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are an expert Android developer. Refactor the given code according to the instruction. Return ONLY the refactored code."
        val userPrompt = "Instruction: $instruction\n\nCode:\n```\n$code\n```"
        callAI(systemPrompt, userPrompt)
    }

    // ── Scan file for issues ───────────────────────────────────────────────────
    suspend fun reviewCode(fileContent: String, fileName: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = "You are a senior Android code reviewer. Identify bugs, performance issues, and best-practice violations. Be concise and actionable."
        val userPrompt = "Review this file ($fileName):\n```\n${fileContent.take(3000)}\n```"
        callAI(systemPrompt, userPrompt)
    }

    // ── Scan and create files ──────────────────────────────────────────────────
    suspend fun generateAndCreateFiles(
        instruction: String,
        projectPath: String,
        packageName: String,
        onFileCreated: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = buildString {
            append("You are an expert Android developer. Generate complete Kotlin/Java files based on the instruction. ")
            append("Format your response EXACTLY as JSON array:\n")
            append("""[{"path": "relative/path/FileName.kt", "content": "...full file content..."}]""")
            append("\nPackage: $packageName")
        }
        val raw = callAI(systemPrompt, instruction)
        try {
            val cleanJson = raw.replace(Regex("```json\\s*|```\\s*"), "").trim()
            val files = gson.fromJson(cleanJson, Array<FileCreationItem>::class.java)
            files.forEach { item ->
                val file = File(projectPath, item.path)
                file.parentFile?.mkdirs()
                file.writeText(item.content)
                onFileCreated(item.path)
            }
            "Created ${files.size} file(s): ${files.joinToString { it.path }}"
        } catch (e: Exception) {
            raw // Return raw response if JSON parsing fails
        }
    }

    // ── Core API caller ────────────────────────────────────────────────────────
    private suspend fun callAI(
        systemPrompt: String,
        userMessage: String,
        includeHistory: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            val cfg = _config.value
            if (cfg.apiKey.isEmpty()) return@withContext "⚠ API key not configured. Go to Settings → AI Configuration."

            val messages = mutableListOf<Map<String, String>>()
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
            if (includeHistory) {
                _chatHistory.value.takeLast(10).forEach { msg ->
                    messages.add(mapOf("role" to msg.role, "content" to msg.content))
                }
            }
            messages.add(mapOf("role" to "user", "content" to userMessage))

            val body = when (cfg.provider) {
                AIProvider.GEMINI -> buildGeminiRequest(userMessage, systemPrompt)
                else -> buildOpenAIRequest(cfg.model, messages)
            }

            val url = when (cfg.provider) {
                AIProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                AIProvider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:generateContent?key=${cfg.apiKey}"
                AIProvider.CLAUDE -> "https://api.anthropic.com/v1/messages"
                AIProvider.CUSTOM -> "${cfg.baseUrl}/chat/completions"
            }

            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))

            when (cfg.provider) {
                AIProvider.OPENAI, AIProvider.CUSTOM -> requestBuilder.header("Authorization", "Bearer ${cfg.apiKey}")
                AIProvider.CLAUDE -> {
                    requestBuilder.header("x-api-key", cfg.apiKey)
                    requestBuilder.header("anthropic-version", "2023-06-01")
                }
                AIProvider.GEMINI -> {} // Key in URL
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext "API Error ${response.code}: ${response.body?.string()?.take(200)}"
            }
            val responseBody = response.body?.string() ?: return@withContext "Empty response"
            parseAIResponse(responseBody, cfg.provider)
        } catch (e: Exception) {
            "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    private fun buildOpenAIRequest(model: String, messages: List<Map<String, String>>): String =
        gson.toJson(mapOf("model" to model, "messages" to messages, "max_tokens" to 4096, "temperature" to 0.3))

    private fun buildGeminiRequest(message: String, system: String): String =
        gson.toJson(mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to system))),
            "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to message))))
        ))

    private fun parseAIResponse(responseBody: String, provider: AIProvider): String {
        return try {
            val json = gson.fromJson(responseBody, Map::class.java)
            when (provider) {
                AIProvider.GEMINI -> {
                    val candidates = json["candidates"] as? List<*>
                    val first = candidates?.firstOrNull() as? Map<*, *>
                    val content = first?.get("content") as? Map<*, *>
                    val parts = content?.get("parts") as? List<*>
                    (parts?.firstOrNull() as? Map<*, *>)?.get("text") as? String ?: "No response"
                }
                AIProvider.CLAUDE -> {
                    val content = json["content"] as? List<*>
                    (content?.firstOrNull() as? Map<*, *>)?.get("text") as? String ?: "No response"
                }
                else -> {
                    val choices = json["choices"] as? List<*>
                    val first = choices?.firstOrNull() as? Map<*, *>
                    val message = first?.get("message") as? Map<*, *>
                    message?.get("content") as? String ?: "No response"
                }
            }
        } catch (e: Exception) { "Parse error: ${e.message}\n$responseBody" }
    }

    data class ProjectContext(
        val projectName: String,
        val language: String,
        val activeFileName: String,
        val activeFileContent: String,
        val relatedFiles: List<String> = emptyList()
    )

    private data class FileCreationItem(val path: String, val content: String)
}
