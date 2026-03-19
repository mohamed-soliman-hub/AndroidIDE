package com.androidide.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.data.models.*
import com.androidide.ui.components.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: MainViewModel) {
    val uiState           by viewModel.uiState.collectAsState()
    val tabs              by viewModel.tabs.collectAsState()
    val activeTabIdx      by viewModel.activeTabIndex.collectAsState()
    val activeTab         by viewModel.activeTab.collectAsState()
    val fileTree          by viewModel.fileTree.collectAsState()
    val currentProject    by viewModel.projectManager.currentProject.collectAsState()
    val bottomPanelOpen   by viewModel.bottomPanelExpanded.collectAsState()
    val bottomPanel       by viewModel.bottomPanel.collectAsState()
    val showCompletions   by viewModel.showCompletions.collectAsState()
    val completions       by viewModel.completions.collectAsState()
    val buildState        by viewModel.buildState.collectAsState()
    val gitStatus         by viewModel.gitStatus.collectAsState()
    val showSearch        by remember { derivedStateOf { uiState.showSearchBar } }

    var showFileTree by remember { mutableStateOf(true) }
    var showAIPanel  by remember { mutableStateOf(false) }
    var showGitPanel by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── IDE TopBar ─────────────────────────────────────────────────────
            IDETopBar(
                projectName   = currentProject?.name ?: "No Project",
                buildState    = buildState,
                gitStatus     = gitStatus,
                onHome        = { viewModel.setScreen(IDEScreen.HOME) },
                onBuild       = { viewModel.buildProject() },
                onClean       = { viewModel.cleanProject() },
                onSync        = { viewModel.syncGradle() },
                onGit         = { showGitPanel = !showGitPanel },
                onAI          = { showAIPanel = !showAIPanel },
                onDesigner    = { viewModel.openDesigner() },
                onSearch      = { viewModel._uiState.value.let {
                    viewModel.setScreen(IDEScreen.EDITOR)
                }},
                onSettings    = { viewModel.setScreen(IDEScreen.SETTINGS) },
                onTerminal    = { viewModel.setScreen(IDEScreen.TERMINAL) }
            )

            // ── Tab Bar ────────────────────────────────────────────────────────
            if (tabs.isNotEmpty()) {
                EditorTabBar(
                    tabs = tabs,
                    activeIndex = activeTabIdx,
                    onTabClick = viewModel::switchTab,
                    onTabClose = viewModel::closeTab
                )
            }

            // ── Search Bar ─────────────────────────────────────────────────────
            val searchQuery   by viewModel.searchQuery.collectAsState()
            val replaceQuery  by viewModel.replaceQuery.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            if (uiState.showSearchBar) {
                SearchReplaceBar(
                    searchQuery  = searchQuery,
                    replaceQuery = replaceQuery,
                    resultCount  = searchResults.size,
                    onSearchChange  = viewModel::setSearchQuery,
                    onReplaceChange = viewModel::setReplaceQuery,
                    onReplaceAll    = viewModel::replaceAll,
                    onDismiss = {  }
                )
            }

            // ── Main Layout (File Tree + Editor + AI Panel) ────────────────────
            Row(modifier = Modifier.weight(1f)) {

                // File Tree Sidebar
                AnimatedVisibility(
                    visible = showFileTree,
                    enter   = slideInHorizontally { -it } + fadeIn(),
                    exit    = slideOutHorizontally { -it } + fadeOut()
                ) {
                    FileTreePanel(
                        rootNode      = fileTree,
                        modifier      = Modifier.width(220.dp).fillMaxHeight(),
                        projectName   = currentProject?.name ?: "Project",
                        onFileClick   = { viewModel.openFile(it.file) },
                        onToggle      = { viewModel.toggleFileTreeNode(it) },
                        onNewFile     = { parentPath, name -> viewModel.createNewFile(parentPath, name) },
                        onNewFolder   = { parentPath, name -> viewModel.createNewFolder(parentPath, name) },
                        onDelete      = { viewModel.deleteFile(it) },
                        onRename      = { oldPath, newName -> viewModel.renameFile(oldPath, newName) }
                    )
                }

                // Editor Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (tabs.isEmpty() || activeTab == null) {
                        WelcomeEditor(
                            onNewFile   = { viewModel.createNewFile(
                                currentProject?.path ?: "", "MainActivity.kt") },
                            onOpenProject = { viewModel.setScreen(IDEScreen.HOME) }
                        )
                    } else {
                        CodeEditorPane(
                            tab             = activeTab!!,
                            completions     = completions,
                            showCompletions = showCompletions,
                            searchResults   = viewModel.searchResults.collectAsState().value,
                            onContentChange = { content, cursor -> viewModel.updateActiveTabContent(content, cursor) },
                            onCompletionApply = viewModel::applyCompletion,
                            onDismissCompletion = viewModel::dismissCompletions,
                            onSave          = viewModel::saveActiveFile
                        )
                    }

                    // File Tree Toggle Button
                    FloatingActionButton(
                        onClick = { showFileTree = !showFileTree },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(32.dp),
                        containerColor = SurfaceVariant,
                        contentColor = OnSurface,
                        shape = RoundedCornerShape(8.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            if (showFileTree) Icons.Default.MenuOpen else Icons.Default.Menu,
                            contentDescription = "Toggle file tree",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // AI Panel Sidebar
                AnimatedVisibility(
                    visible = showAIPanel,
                    enter   = slideInHorizontally { it } + fadeIn(),
                    exit    = slideOutHorizontally { it } + fadeOut()
                ) {
                    AIPanel(
                        viewModel = viewModel,
                        modifier  = Modifier.width(280.dp).fillMaxHeight()
                    )
                }

                // Git Panel Sidebar
                AnimatedVisibility(
                    visible = showGitPanel,
                    enter   = slideInHorizontally { it } + fadeIn(),
                    exit    = slideOutHorizontally { it } + fadeOut()
                ) {
                    GitPanel(
                        viewModel = viewModel,
                        modifier  = Modifier.width(260.dp).fillMaxHeight()
                    )
                }
            }

            // ── Bottom Panel ───────────────────────────────────────────────────
            BottomPanel(
                isExpanded     = bottomPanelOpen,
                activeTab      = bottomPanel,
                buildState     = buildState,
                onTabClick     = viewModel::setBottomPanel,
                onToggle       = viewModel::toggleBottomPanel,
                viewModel      = viewModel
            )
        }

        // Error snackbar
        uiState.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action   = { TextButton(onClick = viewModel::clearError) { Text("Dismiss") } },
                containerColor = SurfaceElevated
            ) { Text(msg, color = LogError) }
        }
    }
}

// ── IDE TopBar ─────────────────────────────────────────────────────────────────
@Composable
fun IDETopBar(
    projectName: String,
    buildState: GradleManager.BuildState,
    gitStatus: GitStatus?,
    onHome: () -> Unit, onBuild: () -> Unit, onClean: () -> Unit, onSync: () -> Unit,
    onGit: () -> Unit, onAI: () -> Unit, onSearch: () -> Unit, onDesigner: () -> Unit,
    onSettings: () -> Unit, onTerminal: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        color = Surface,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Back to home
                IDEIconButton(icon = Icons.Default.ArrowBack, onClick = onHome,
                    tint = OnSurfaceDim, size = 18.dp)

                // Project name + git branch
                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(projectName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = OnBackground, fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    gitStatus?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.AccountTree, contentDescription = null,
                                modifier = Modifier.size(10.dp), tint = GitModified)
                            Text(it.branch, style = MaterialTheme.typography.labelSmall.copy(color = GitModified))
                            if (it.modified.isNotEmpty() || it.untracked.isNotEmpty()) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(GitModified))
                            }
                        }
                    }
                }

                // Build state indicator
                val (buildColor, buildIcon) = when (buildState) {
                    GradleManager.BuildState.RUNNING -> Primary to Icons.Default.RotateRight
                    GradleManager.BuildState.SUCCESS -> LogSuccess to Icons.Default.CheckCircle
                    GradleManager.BuildState.FAILED  -> LogError to Icons.Default.Error
                    else -> OnSurfaceDim to Icons.Default.RadioButtonUnchecked
                }
                Icon(buildIcon, contentDescription = "Build state",
                    tint = buildColor, modifier = Modifier.size(14.dp))

                Spacer(modifier = Modifier.width(4.dp))

                // Action buttons
                IDEIconButton(icon = Icons.Default.Search,    onClick = onSearch,   size = 18.dp)
                IDEIconButton(icon = Icons.Default.DesignServices, onClick = onDesigner, size = 18.dp, tint = Tertiary)
                IDEIconButton(icon = Icons.Default.AutoAwesome,onClick = onAI,      size = 18.dp, tint = Tertiary)
                IDEIconButton(icon = Icons.Default.AccountTree,onClick = onGit,     size = 18.dp, tint = GitModified)
                IDEIconButton(icon = Icons.Default.Terminal,  onClick = onTerminal, size = 18.dp)
                IDEIconButton(icon = Icons.Default.Settings,  onClick = onSettings, size = 18.dp)

                Spacer(modifier = Modifier.width(4.dp))

                // Build button
                Button(
                    onClick = onBuild,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (buildState == GradleManager.BuildState.RUNNING) Primary.copy(alpha = 0.5f) else Primary,
                        contentColor = OnPrimary
                    )
                ) {
                    if (buildState == GradleManager.BuildState.RUNNING) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Build", modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Build", style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(color = Outline, thickness = 1.dp)
        }
    }
}

// ── Editor Tab Bar ─────────────────────────────────────────────────────────────
@Composable
fun EditorTabBar(
    tabs: List<EditorTab>,
    activeIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (String) -> Unit
) {
    Surface(color = TabInactive) {
        Column {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(tabs) { index, tab ->
                    val isActive = index == activeIndex
                    Column {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(if (isActive) TabActive else TabInactive)
                                .clickable { onTabClick(index) }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FileTypeIcon(tab.language, size = 12.dp)
                            Text(
                                tab.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isActive) OnBackground else OnSurfaceDim,
                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tab.isModified) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Primary))
                            }
                            IconButton(
                                onClick = { onTabClose(tab.id) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close tab",
                                    tint = OnSurfaceDim, modifier = Modifier.size(12.dp))
                            }
                        }
                        // Active tab indicator
                        if (isActive) {
                            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(TabActiveIndicator))
                        }
                    }
                }
            }
            HorizontalDivider(color = Outline, thickness = 1.dp)
        }
    }
}

// ── Welcome Screen ─────────────────────────────────────────────────────────────
@Composable
fun WelcomeEditor(onNewFile: () -> Unit, onOpenProject: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(EditorBackground), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Code, contentDescription = null,
                tint = Primary.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
            Text("AndroidIDE", style = MaterialTheme.typography.headlineMedium.copy(
                color = OnSurfaceDim, fontWeight = FontWeight.Light))
            Text("Open a file from the file tree or create a new one",
                style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenProject) { Text("Open Project", color = Primary) }
                FilledTonalButton(onClick = onNewFile,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary.copy(alpha = 0.15f))) {
                    Text("New File", color = Primary)
                }
            }

            // Keyboard shortcuts cheat sheet
            Spacer(modifier = Modifier.height(16.dp))
            ShortcutsGrid()
        }
    }
}

@Composable
private fun ShortcutsGrid() {
    val shortcuts = listOf(
        "Ctrl+S" to "Save file",
        "Ctrl+Z" to "Undo",
        "Ctrl+/" to "Toggle comment",
        "Ctrl+D" to "Duplicate line",
        "Ctrl+F" to "Find",
        "Ctrl+B" to "Build project"
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shortcuts.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(key, style = MaterialTheme.typography.labelSmall.copy(color = Primary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                        }
                        Text(desc, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                }
            }
        }
    }
}
