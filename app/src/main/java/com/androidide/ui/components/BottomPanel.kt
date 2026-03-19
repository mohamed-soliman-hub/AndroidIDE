package com.androidide.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.androidide.core.build.GradleManager
import com.androidide.data.models.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.*

@Composable
fun BottomPanel(
    isExpanded: Boolean,
    activeTab: BottomPanelTab,
    buildState: GradleManager.BuildState,
    onTabClick: (BottomPanelTab) -> Unit,
    onToggle: () -> Unit,
    viewModel: MainViewModel
) {
    val panelHeight by animateDpAsState(
        targetValue = if (isExpanded) 240.dp else 36.dp,
        label = "panel_height"
    )

    Surface(
        modifier = Modifier.fillMaxWidth().height(panelHeight),
        color = Surface,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel Tab Bar
            HorizontalDivider(color = Outline, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(SurfaceVariant)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val tabs = listOf(
                    BottomPanelTab.BUILD_LOG to "BUILD",
                    BottomPanelTab.LOGCAT    to "LOGCAT",
                    BottomPanelTab.TERMINAL  to "TERMINAL",
                    BottomPanelTab.PROBLEMS  to "PROBLEMS",
                    BottomPanelTab.GIT_LOG   to "GIT"
                )
                tabs.forEach { (tab, label) ->
                    val isActive = tab == activeTab
                    val hasIndicator = when (tab) {
                        BottomPanelTab.BUILD_LOG -> buildState == GradleManager.BuildState.FAILED
                        else -> false
                    }
                    TextButton(
                        onClick = { onTabClick(tab); if (!isExpanded) onToggle() },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isActive) Primary else OnSurfaceDim,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        )
                        if (hasIndicator) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(LogError))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Panel controls
                IDEIconButton(
                    icon = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    onClick = onToggle,
                    size = 14.dp,
                    tint = OnSurfaceDim
                )
                IDEIconButton(
                    icon = Icons.Default.Close,
                    onClick = { if (isExpanded) onToggle() },
                    size = 14.dp,
                    tint = OnSurfaceDim
                )
            }

            // Panel Content
            if (isExpanded) {
                Box(modifier = Modifier.fillMaxSize().background(EditorBackground)) {
                    when (activeTab) {
                        BottomPanelTab.BUILD_LOG -> BuildLogPanel(viewModel)
                        BottomPanelTab.LOGCAT    -> LogcatPanel(viewModel)
                        BottomPanelTab.TERMINAL  -> TerminalPanel()
                        BottomPanelTab.PROBLEMS  -> ProblemsPanel(viewModel)
                        BottomPanelTab.GIT_LOG   -> GitLogPanel(viewModel)
                    }
                }
            }
        }
    }
}

// ── Build Log Panel ────────────────────────────────────────────────────────────
@Composable
fun BuildLogPanel(viewModel: MainViewModel) {
    val logs by viewModel.buildLogs.collectAsState(initial = emptyList<BuildLog>())
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(logs) { log ->
            val color = when (log.level) {
                BuildLogLevel.ERROR   -> LogError
                BuildLogLevel.WARNING -> LogWarning
                BuildLogLevel.SUCCESS -> LogSuccess
                BuildLogLevel.INFO    -> LogInfo
                BuildLogLevel.DEBUG   -> LogDebug
            }
            Text(
                log.message,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = color
                )
            )
        }
    }
}

// ── Logcat Panel ───────────────────────────────────────────────────────────────
@Composable
fun LogcatPanel(viewModel: MainViewModel) {
    val entries by viewModel.logcatEntries.collectAsState()
    val filter  by viewModel.logcatFilter.collectAsState()
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var filterExpanded by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf(LogcatLevel.VERBOSE) }

    val filteredEntries = remember(entries, tagFilter, levelFilter) {
        entries.filter { entry ->
            entry.level.ordinal >= levelFilter.ordinal &&
            (tagFilter.isEmpty() || entry.tag.contains(tagFilter, ignoreCase = true))
        }.takeLast(500)
    }

    LaunchedEffect(filteredEntries.size) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = tagFilter,
                onValueChange = { tagFilter = it },
                modifier = Modifier.width(120.dp).height(32.dp),
                placeholder = { Text("Tag filter", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground
                )
            )

            // Level filter chips
            val levels = listOf(LogcatLevel.VERBOSE, LogcatLevel.DEBUG, LogcatLevel.INFO, LogcatLevel.WARNING, LogcatLevel.ERROR)
            levels.forEach { level ->
                val levelColor = when(level) {
                    LogcatLevel.ERROR   -> LogError
                    LogcatLevel.WARNING -> LogWarning
                    LogcatLevel.INFO    -> LogInfo
                    LogcatLevel.DEBUG   -> LogDebug
                    else                -> LogVerbose
                }
                FilterChip(
                    selected = levelFilter == level,
                    onClick  = { levelFilter = level },
                    label    = { Text(level.char.toString(), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = levelColor.copy(alpha = 0.2f),
                        selectedLabelColor = levelColor
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            IDEIconButton(
                icon = if (autoScroll) Icons.Default.LockOpen else Icons.Default.Lock,
                onClick = { autoScroll = !autoScroll },
                size = 14.dp,
                tint = if (autoScroll) Primary else OnSurfaceDim
            )
            IDEIconButton(
                icon = Icons.Default.Delete,
                onClick = { /* clear logs */ },
                size = 14.dp,
                tint = OnSurfaceDim
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(filteredEntries) { entry ->
                val levelColor = when (entry.level) {
                    LogcatLevel.ERROR   -> LogError
                    LogcatLevel.WARNING -> LogWarning
                    LogcatLevel.INFO    -> LogInfo
                    LogcatLevel.DEBUG   -> LogDebug
                    LogcatLevel.FATAL   -> LogError
                    else                -> LogVerbose
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        entry.level.char.toString(),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = levelColor, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        entry.timestamp,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = OnSurfaceDim)
                    )
                    Text(
                        entry.tag.take(20).padEnd(20),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = LogDebug)
                    )
                    Text(
                        entry.message,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = levelColor)
                    )
                }
            }
        }
    }
}

// ── Terminal Panel ─────────────────────────────────────────────────────────────
@Composable
fun TerminalPanel() {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Terminal, null, tint = LogSuccess.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
            Text("Terminal", style = MaterialTheme.typography.titleSmall.copy(color = LogSuccess))
            Text("Open full terminal from the toolbar", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
        }
    }
}

// ── Problems Panel ─────────────────────────────────────────────────────────────
@Composable
fun ProblemsPanel(viewModel: MainViewModel) {
    val logs by viewModel.buildLogs.collectAsState(initial = emptyList<BuildLog>())
    val errors = remember(logs) { logs.filter { it.level == BuildLogLevel.ERROR } }

    if (errors.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = LogSuccess)
                Text("No problems", style = MaterialTheme.typography.bodyMedium.copy(color = LogSuccess))
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            items(errors) { log ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = LogError, modifier = Modifier.size(14.dp))
                    Text(log.message, style = MaterialTheme.typography.bodySmall.copy(
                        color = LogError, fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

// ── Git Log Panel ──────────────────────────────────────────────────────────────
@Composable
fun GitLogPanel(viewModel: MainViewModel) {
    val commits by viewModel.gitCommits.collectAsState()
    if (commits.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No commits yet", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
        items(commits) { commit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Primary.copy(alpha = 0.15f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text(commit.shortHash, style = TextStyle(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Primary))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(commit.message, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground))
                    Text("${commit.author}", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                }
            }
        }
    }
}
