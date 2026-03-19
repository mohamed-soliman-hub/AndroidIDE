package com.androidide.data.models

import java.io.File
import java.time.Instant

// ─────────────────────────────────────────────────────────────────────────────
// Project
// ─────────────────────────────────────────────────────────────────────────────
data class Project(
    val id: String,
    val name: String,
    val path: String,
    val type: ProjectType,
    val language: Language,
    val lastOpened: Long = Instant.now().epochSecond,
    val gradleVersion: String = "8.9",
    val compileSdk: Int = 35,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val packageName: String = "com.example.app"
) {
    val rootDir: File get() = File(path)
    val isValid: Boolean get() = rootDir.exists() && rootDir.isDirectory
}

enum class ProjectType { EMPTY_ACTIVITY, APPCOMPAT_ACTIVITY, COMPOSE_ACTIVITY,
    NAVIGATION_DRAWER, BOTTOM_TABS, VIEWMODEL_LIVEDATA, SERVICE_BROADCAST,
    IMPORTED }

enum class Language { KOTLIN, JAVA }

// ─────────────────────────────────────────────────────────────────────────────
// File Tree Node
// ─────────────────────────────────────────────────────────────────────────────
data class FileNode(
    val file: File,
    val depth: Int = 0,
    var isExpanded: Boolean = false,
    val children: MutableList<FileNode> = mutableListOf()
) {
    val name: String get() = file.name
    val isDirectory: Boolean get() = file.isDirectory
    val extension: String get() = file.extension.lowercase()
    val absolutePath: String get() = file.absolutePath

    fun fileType(): FileType = when {
        isDirectory -> FileType.DIRECTORY
        extension == "kt" -> FileType.KOTLIN
        extension == "java" -> FileType.JAVA
        extension == "xml" -> FileType.XML
        extension in listOf("gradle", "kts") -> FileType.GRADLE
        extension == "json" -> FileType.JSON
        extension == "md" -> FileType.MARKDOWN
        extension in listOf("png", "jpg", "jpeg", "webp", "svg") -> FileType.IMAGE
        extension == "pro" -> FileType.PROGUARD
        extension == "toml" -> FileType.TOML
        else -> FileType.TEXT
    }
}

enum class FileType { DIRECTORY, KOTLIN, JAVA, XML, GRADLE, JSON,
    MARKDOWN, IMAGE, PROGUARD, TOML, TEXT }

// ─────────────────────────────────────────────────────────────────────────────
// Editor Tab
// ─────────────────────────────────────────────────────────────────────────────
data class EditorTab(
    val id: String,
    val file: File,
    var content: String = "",
    var isModified: Boolean = false,
    var cursorPosition: Int = 0,
    val language: EditorLanguage = EditorLanguage.fromExtension(file.extension)
) {
    val displayName: String get() = if (isModified) "● ${file.name}" else file.name
}

enum class EditorLanguage {
    KOTLIN, JAVA, XML, GRADLE, JSON, MARKDOWN, PROGUARD, PLAIN;
    companion object {
        fun fromExtension(ext: String) = when (ext.lowercase()) {
            "kt"      -> KOTLIN
            "java"    -> JAVA
            "xml"     -> XML
            "gradle"  -> GRADLE
            "kts"     -> GRADLE
            "json"    -> JSON
            "md"      -> MARKDOWN
            "pro"     -> PROGUARD
            else      -> PLAIN
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Build Logs
// ─────────────────────────────────────────────────────────────────────────────
data class BuildLog(
    val timestamp: Long = Instant.now().epochSecond,
    val message: String,
    val level: BuildLogLevel = BuildLogLevel.INFO
)

enum class BuildLogLevel { DEBUG, INFO, WARNING, ERROR, SUCCESS }

data class BuildResult(
    val success: Boolean,
    val durationMs: Long,
    val apkPath: String? = null,
    val errors: List<BuildError> = emptyList()
)

data class BuildError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Code Completion
// ─────────────────────────────────────────────────────────────────────────────
data class CompletionItem(
    val label: String,
    val detail: String = "",
    val documentation: String = "",
    val kind: CompletionKind,
    val insertText: String = label,
    val sortText: String = label
)

enum class CompletionKind { CLASS, METHOD, PROPERTY, KEYWORD, SNIPPET, INTERFACE, ENUM, VARIABLE }

// ─────────────────────────────────────────────────────────────────────────────
// Git
// ─────────────────────────────────────────────────────────────────────────────
data class GitStatus(
    val branch: String = "main",
    val staged: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val untracked: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val ahead: Int = 0,
    val behind: Int = 0
)

data class GitCommit(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val timestamp: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// Dependency
// ─────────────────────────────────────────────────────────────────────────────
data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val description: String = "",
    val type: DependencyType = DependencyType.IMPLEMENTATION
)

enum class DependencyType { IMPLEMENTATION, DEBUG_IMPLEMENTATION, TEST_IMPLEMENTATION,
    ANDROID_TEST_IMPLEMENTATION, KSP, KAPT }

// ─────────────────────────────────────────────────────────────────────────────
// Logcat
// ─────────────────────────────────────────────────────────────────────────────
data class LogcatEntry(
    val timestamp: String,
    val pid: String,
    val tid: String,
    val level: LogcatLevel,
    val tag: String,
    val message: String
)

enum class LogcatLevel(val char: Char) {
    VERBOSE('V'), DEBUG('D'), INFO('I'), WARNING('W'), ERROR('E'), FATAL('F');
    companion object {
        fun fromChar(c: Char) = values().find { it.char == c } ?: VERBOSE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI
// ─────────────────────────────────────────────────────────────────────────────
data class AIMessage(
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = Instant.now().epochSecond
)

data class AIConfig(
    val provider: AIProvider = AIProvider.OPENAI,
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val baseUrl: String = "https://api.openai.com/v1"
)

enum class AIProvider { OPENAI, GEMINI, CLAUDE, CUSTOM }
