package com.androidide.core.project

import android.content.Context
import com.androidide.data.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject

    private val baseDir: File get() = File(context.getExternalFilesDir(null), "projects").also { it.mkdirs() }

    suspend fun createProject(
        name: String,
        type: ProjectType,
        language: Language,
        packageName: String,
        minSdk: Int = 26
    ): Project = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val projectDir = File(baseDir, name).also { it.mkdirs() }
        val project = Project(
            id = id, name = name, path = projectDir.absolutePath,
            type = type, language = language, packageName = packageName, minSdk = minSdk
        )
        TemplateManager.generateTemplate(project, projectDir)
        val updated = _projects.value + project
        _projects.value = updated
        _currentProject.value = project
        project
    }

    suspend fun importProject(path: String): Project? = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return@withContext null
        val settingsFile = dir.listFiles()?.find { it.name == "settings.gradle" || it.name == "settings.gradle.kts" }
        val name = if (settingsFile != null) parseProjectName(settingsFile) else dir.name
        val project = Project(
            id = UUID.randomUUID().toString(), name = name,
            path = dir.absolutePath, type = ProjectType.IMPORTED,
            language = detectLanguage(dir)
        )
        val updated = _projects.value + project
        _projects.value = updated
        _currentProject.value = project
        project
    }

    fun openProject(project: Project) { _currentProject.value = project }

    fun closeProject() { _currentProject.value = null }

    suspend fun getFileTree(project: Project): FileNode = withContext(Dispatchers.IO) {
        buildFileNode(File(project.path), 0)
    }

    private fun buildFileNode(file: File, depth: Int): FileNode {
        val node = FileNode(file = file, depth = depth)
        if (file.isDirectory) {
            val children = file.listFiles()
                ?.filter { !it.name.startsWith(".") || it.name == ".gitignore" }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.map { buildFileNode(it, depth + 1) }
                ?: emptyList()
            node.children.addAll(children)
        }
        return node
    }

    private fun parseProjectName(settingsFile: File): String {
        return settingsFile.readLines()
            .find { it.contains("rootProject.name") }
            ?.substringAfter("=")?.trim()?.replace("\"", "")?.replace("'", "")
            ?: settingsFile.parentFile?.name ?: "Unknown"
    }

    private fun detectLanguage(dir: File): Language {
        var kotlinCount = 0; var javaCount = 0
        dir.walkTopDown().forEach { f ->
            when (f.extension.lowercase()) { "kt" -> kotlinCount++; "java" -> javaCount++ }
        }
        return if (kotlinCount >= javaCount) Language.KOTLIN else Language.JAVA
    }
}
