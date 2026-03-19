package com.androidide.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidide.core.ai.AIService
import com.androidide.core.build.DependencyResolver
import com.androidide.core.build.GitHubActionsService
import com.androidide.core.build.KotlinCompilerEngine
import com.androidide.core.build.OnDeviceBuildEngine
import com.androidide.core.build.GradleManager
import com.androidide.core.editor.CodeCompletionEngine
import com.androidide.core.git.GitManager
import com.androidide.core.project.ProjectManager
import com.androidide.data.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val projectManager: ProjectManager,
    val gradleManager: GradleManager,
    val gitManager: GitManager,
    val completionEngine: CodeCompletionEngine,
    val aiService: AIService,
    val dependencyResolver: DependencyResolver,
    val onDeviceBuildEngine: OnDeviceBuildEngine,
    val kotlinCompilerEngine: KotlinCompilerEngine,
    val gitHubActionsService: GitHubActionsService,
) : ViewModel() {

    // ── UI State ───────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(IDEUiState())
    val uiState: StateFlow<IDEUiState> = _uiState.asStateFlow()

    // ── Editor Tabs ────────────────────────────────────────────────────────────
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    val activeTab: StateFlow<EditorTab?> = combine(_tabs, _activeTabIndex) { tabs, idx ->
        tabs.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── File Tree ──────────────────────────────────────────────────────────────
    private val _fileTree = MutableStateFlow<FileNode?>(null)
    val fileTree: StateFlow<FileNode?> = _fileTree.asStateFlow()

    // ── Code Completion ────────────────────────────────────────────────────────
    private val _completions = MutableStateFlow<List<CompletionItem>>(emptyList())
    val completions: StateFlow<List<CompletionItem>> = _completions.asStateFlow()

    private val _showCompletions = MutableStateFlow(false)
    val showCompletions: StateFlow<Boolean> = _showCompletions.asStateFlow()

    // ── Build ──────────────────────────────────────────────────────────────────
    val buildLogs   = gradleManager.buildLogs
    val buildState  = gradleManager.buildState

    // ── Git ────────────────────────────────────────────────────────────────────
    val gitStatus  = gitManager.status
    val gitCommits = gitManager.commits
    val gitBranches= gitManager.branches
    val gitLog     = gitManager.log

    // ── AI ─────────────────────────────────────────────────────────────────────
    private val _aiMessages    = MutableStateFlow<List<AIMessage>>(emptyList())
    val aiMessages: StateFlow<List<AIMessage>> = _aiMessages.asStateFlow()

    private val _aiLoading     = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    // ── Logcat ─────────────────────────────────────────────────────────────────
    private val _logcatEntries = MutableStateFlow<List<LogcatEntry>>(emptyList())
    val logcatEntries: StateFlow<List<LogcatEntry>> = _logcatEntries.asStateFlow()

    private val _logcatFilter  = MutableStateFlow(LogcatFilter())
    val logcatFilter: StateFlow<LogcatFilter> = _logcatFilter.asStateFlow()

    // ── Search / Replace ───────────────────────────────────────────────────────
    private val _searchQuery   = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery  = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    // ── Debounce jobs ──────────────────────────────────────────────────────────
    private var completionJob: Job? = null
    private var saveJob: Job? = null
    private var searchJob: Job? = null
    private var logcatJob: Job? = null

    // ── Bottom Panel ───────────────────────────────────────────────────────────
    private val _bottomPanel = MutableStateFlow(BottomPanelTab.BUILD_LOG)
    val bottomPanel: StateFlow<BottomPanelTab> = _bottomPanel.asStateFlow()

    private val _bottomPanelExpanded = MutableStateFlow(false)
    val bottomPanelExpanded: StateFlow<Boolean> = _bottomPanelExpanded.asStateFlow()

    init {
        startLogcatReader()
    }

    // ── Project Operations ─────────────────────────────────────────────────────
    fun createProject(name: String, type: ProjectType, language: Language,
                      packageName: String, minSdk: Int = 26) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val project = projectManager.createProject(name, type, language, packageName, minSdk)
            refreshFileTree(project)
            gitManager.initRepository(project.path)
            _uiState.value = _uiState.value.copy(isLoading = false, currentScreen = IDEScreen.EDITOR)
        }
    }

    fun importProject(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val project = projectManager.importProject(path)
            if (project != null) {
                refreshFileTree(project)
                gitManager.openRepository(project.path)
                _uiState.value = _uiState.value.copy(isLoading = false, currentScreen = IDEScreen.EDITOR)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false,
                    errorMessage = "Failed to import project from: $path")
            }
        }
    }

    fun openProject(project: Project) {
        viewModelScope.launch {
            projectManager.openProject(project)
            refreshFileTree(project)
            gitManager.openRepository(project.path)
            _uiState.value = _uiState.value.copy(currentScreen = IDEScreen.EDITOR)
        }
    }

    private suspend fun refreshFileTree(project: Project) {
        _fileTree.value = projectManager.getFileTree(project)
    }

    fun refreshFileTree() {
        viewModelScope.launch {
            val project = projectManager.currentProject.value ?: return@launch
            refreshFileTree(project)
        }
    }

    // ── File Operations ────────────────────────────────────────────────────────
    fun openFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _tabs.value.indexOfFirst { it.file.absolutePath == file.absolutePath }
            if (existing >= 0) {
                _activeTabIndex.value = existing
                return@launch
            }
            if (!file.canRead()) return@launch
            val content = file.readText()
            val tab = EditorTab(
                id = UUID.randomUUID().toString(),
                file = file,
                content = content
            )
            withContext(Dispatchers.Main) {
                _tabs.value = _tabs.value + tab
                _activeTabIndex.value = _tabs.value.size - 1
            }
        }
    }

    fun closeTab(tabId: String) {
        val tabs = _tabs.value.toMutableList()
        val idx = tabs.indexOfFirst { it.id == tabId }
        if (idx < 0) return
        tabs.removeAt(idx)
        _tabs.value = tabs
        if (_activeTabIndex.value >= tabs.size) {
            _activeTabIndex.value = maxOf(0, tabs.size - 1)
        }
    }

    fun switchTab(index: Int) {
        if (index >= 0 && index < _tabs.value.size) _activeTabIndex.value = index
    }

    fun updateActiveTabContent(content: String, cursorPos: Int = 0) {
        val idx = _activeTabIndex.value
        val tabs = _tabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return
        tabs[idx] = tab.copy(content = content, isModified = true, cursorPosition = cursorPos)
        _tabs.value = tabs

        // Trigger auto-save with debounce
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1500)
            saveActiveFile()
        }

        // Trigger completion with debounce
        completionJob?.cancel()
        completionJob = viewModelScope.launch {
            delay(300)
            requestCompletions(content, cursorPos, tab.language)
        }
    }

    private suspend fun requestCompletions(code: String, cursor: Int, language: EditorLanguage) {
        val items = completionEngine.getCompletions(code, cursor, language)
        _completions.value = items
        _showCompletions.value = items.isNotEmpty()
    }

    fun dismissCompletions() { _showCompletions.value = false }

    fun applyCompletion(item: CompletionItem) {
        val idx = _activeTabIndex.value
        val tabs = _tabs.value.toMutableList()
        val tab = tabs.getOrNull(idx) ?: return
        val cursor = tab.cursorPosition
        val code = tab.content

        // Replace the prefix before cursor with the completion
        var prefixStart = cursor - 1
        while (prefixStart > 0 && (code[prefixStart - 1].isLetterOrDigit() || code[prefixStart - 1] == '_')) prefixStart--

        val newContent = code.substring(0, prefixStart) + item.insertText + code.substring(cursor)
        val newCursor = prefixStart + item.insertText.length

        tabs[idx] = tab.copy(content = newContent, cursorPosition = newCursor, isModified = true)
        _tabs.value = tabs
        _showCompletions.value = false
    }

    fun saveActiveFile() {
        val tab = activeTab.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            tab.file.writeText(tab.content)
            val idx = _activeTabIndex.value
            val tabs = _tabs.value.toMutableList()
            tabs[idx] = tab.copy(isModified = false)
            withContext(Dispatchers.Main) { _tabs.value = tabs }
        }
    }

    fun createNewFile(parentPath: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(parentPath, name)
            file.createNewFile()
            withContext(Dispatchers.Main) { openFile(file) }
            refreshFileTree()
        }
    }

    fun createNewFolder(parentPath: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(parentPath, name).mkdirs()
            refreshFileTree()
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
            // Close tab if open
            val tabIdx = _tabs.value.indexOfFirst { it.file.absolutePath == path }
            if (tabIdx >= 0) withContext(Dispatchers.Main) { closeTab(_tabs.value[tabIdx].id) }
            refreshFileTree()
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldFile = File(oldPath)
            val newFile = File(oldFile.parent, newName)
            oldFile.renameTo(newFile)
            // Update open tab if needed
            val tabIdx = _tabs.value.indexOfFirst { it.file.absolutePath == oldPath }
            if (tabIdx >= 0) {
                val tabs = _tabs.value.toMutableList()
                tabs[tabIdx] = tabs[tabIdx].copy(file = newFile)
                withContext(Dispatchers.Main) { _tabs.value = tabs }
            }
            refreshFileTree()
        }
    }

    // ── Build Operations ───────────────────────────────────────────────────────
    fun buildProject() {
        val project = projectManager.currentProject.value ?: return
        viewModelScope.launch {
            _bottomPanelExpanded.value = true
            _bottomPanel.value = BottomPanelTab.BUILD_LOG
            gradleManager.buildProject(project.path)
        }
    }

    fun cleanProject() {
        val project = projectManager.currentProject.value ?: return
        viewModelScope.launch { gradleManager.cleanProject(project.path) }
    }

    fun syncGradle() {
        val project = projectManager.currentProject.value ?: return
        viewModelScope.launch { gradleManager.syncGradle(project.path) }
    }

    fun addDependency(dependency: MavenDependency) {
        val project = projectManager.currentProject.value ?: return
        viewModelScope.launch { gradleManager.addDependency(project.path, dependency) }
    }

    // ── Git Operations ─────────────────────────────────────────────────────────
    fun gitCommit(message: String) = viewModelScope.launch { gitManager.commit(message) }
    fun gitPush(username: String = "", password: String = "") =
        viewModelScope.launch { gitManager.push(username = username, password = password) }
    fun gitPull() = viewModelScope.launch { gitManager.pull() }
    fun gitStageAll() = viewModelScope.launch { gitManager.stageAll() }
    fun createBranch(name: String) = viewModelScope.launch { gitManager.createBranch(name) }
    fun checkoutBranch(name: String) = viewModelScope.launch { gitManager.checkoutBranch(name) }
    fun gitStageFile(path: String) = viewModelScope.launch { gitManager.stageFile(path) }

    // ── AI Operations ──────────────────────────────────────────────────────────
    fun sendAIMessage(userMessage: String) {
        viewModelScope.launch {
            val newMsg = AIMessage("user", userMessage)
            _aiMessages.value = _aiMessages.value + newMsg
            _aiLoading.value = true
            val projectContext = activeTab.value?.content?.take(1000) ?: ""
            val response = aiService.chat(_aiMessages.value, projectContext)
            _aiMessages.value = _aiMessages.value + AIMessage("assistant", response)
            _aiLoading.value = false
        }
    }

    fun generateCodeAI(prompt: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            val context = activeTab.value?.content ?: ""
            val code = aiService.generateCode(prompt, context)
            val tab = activeTab.value
            if (tab != null) {
                updateActiveTabContent(tab.content + "\n\n" + code, tab.content.length + code.length + 2)
            }
            _aiLoading.value = false
        }
    }

    fun fixErrorAI() {
        viewModelScope.launch {
            _aiLoading.value = true
            val tab = activeTab.value ?: return@launch
            val lastError = buildState.value.name
            val fixed = aiService.fixError(tab.content, lastError, tab.file.name)
            updateActiveTabContent(fixed)
            _aiLoading.value = false
        }
    }

    fun clearAIChat() { _aiMessages.value = emptyList() }

    fun updateAIConfig(config: AIConfig) { aiService.updateConfig(config) }

    // ── Search ─────────────────────────────────────────────────────────────────
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isEmpty()) { _searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(300)
            val tab = activeTab.value ?: return@launch
            val results = mutableListOf<SearchResult>()
            var idx = 0
            while (true) {
                val found = tab.content.indexOf(query, idx, ignoreCase = true)
                if (found < 0) break
                results.add(SearchResult(found, found + query.length))
                idx = found + 1
            }
            _searchResults.value = results
        }
    }

    fun replaceAll() {
        val tab = activeTab.value ?: return
        val newContent = tab.content.replace(_searchQuery.value, _replaceQuery.value, ignoreCase = true)
        updateActiveTabContent(newContent)
        _searchResults.value = emptyList()
    }

    fun setReplaceQuery(q: String) { _replaceQuery.value = q }

    // ── UI State Helpers ───────────────────────────────────────────────────────
    fun setScreen(screen: IDEScreen) { _uiState.value = _uiState.value.copy(currentScreen = screen) }
    fun openDesigner() { setScreen(IDEScreen.DESIGNER) }
    fun onDesignerCodeChanged(code: String) {
        // Insert generated code into a new/existing tab named after the screen
        val existingTab = _tabs.value.find { it.file.name.contains("DesignerScreen") }
        if (existingTab != null) {
            _tabs.value = _tabs.value.map {
                if (it.id == existingTab.id) it.copy(content = code, isModified = true) else it
            }
        }
        // If no tab, just hold in memory - user can explicitly save
    }
    fun setBottomPanel(tab: BottomPanelTab) { _bottomPanel.value = tab }
    fun toggleBottomPanel() { _bottomPanelExpanded.value = !_bottomPanelExpanded.value }
    fun setLogcatFilter(filter: LogcatFilter) { _logcatFilter.value = filter }
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    fun toggleFileTreeNode(node: FileNode) {
        node.isExpanded = !node.isExpanded
        // Force recomposition
        _fileTree.value = _fileTree.value?.copy()
    }

    // ── Logcat ─────────────────────────────────────────────────────────────────
    private fun startLogcatReader() {
        logcatJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                Runtime.getRuntime().exec("logcat -c").waitFor()
                val process = Runtime.getRuntime().exec("logcat -v threadtime")
                val reader = process.inputStream.bufferedReader()
                reader.lineSequence().forEach { line ->
                    parseLogcatLine(line)?.let { entry ->
                        val current = _logcatEntries.value
                        _logcatEntries.value = if (current.size > 5000) current.drop(100) + entry else current + entry
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseLogcatLine(line: String): LogcatEntry? {
        val regex = Regex("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEF])\\s+(.+?):\\s+(.+)$")
        return regex.find(line)?.let { m ->
            LogcatEntry(
                timestamp = m.groupValues[1],
                pid       = m.groupValues[2],
                tid       = m.groupValues[3],
                level     = LogcatLevel.fromChar(m.groupValues[4].firstOrNull() ?: 'V'),
                tag       = m.groupValues[5].trim(),
                message   = m.groupValues[6]
            )
        }
    }

    fun installApk(apkPath: String) {
        onDeviceBuildEngine  // engine has installApk via context
        android.content.Intent(android.content.Intent.ACTION_VIEW).also { intent ->
            val file = java.io.File(apkPath)
            val uri  = androidx.core.content.FileProvider.getUriForFile(
                context, "\${context.packageName}.provider", file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.startActivity(intent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gitManager.close()
        logcatJob?.cancel()
    }
}

// ── Supporting data classes ────────────────────────────────────────────────────
data class IDEUiState(
    val currentScreen: IDEScreen = IDEScreen.HOME,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showNewProjectDialog: Boolean = false,
    val showDependencyDialog: Boolean = false,
    val showGitDialog: Boolean = false,
    val showAIPanel: Boolean = false,
    val showSettings: Boolean = false,
    val showSearchBar: Boolean = false,
)

enum class IDEScreen { HOME, EDITOR, SETTINGS, TERMINAL, GIT, ABOUT, DESIGNER, PREVIEW_SPLIT, DEPENDENCIES, BUILD, CLOUD_BUILD }
enum class BottomPanelTab { BUILD_LOG, LOGCAT, TERMINAL, PROBLEMS, GIT_LOG }

data class LogcatFilter(
    val level: LogcatLevel = LogcatLevel.VERBOSE,
    val tag: String = "",
    val pid: String = "",
    val query: String = ""
)

data class SearchResult(val start: Int, val end: Int)

private fun FileNode.copy() = FileNode(file = file, depth = depth, isExpanded = isExpanded,
    children = children)
