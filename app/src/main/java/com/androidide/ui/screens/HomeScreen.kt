package com.androidide.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.data.models.*
import com.androidide.ui.components.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val projects by viewModel.projectManager.projects.collectAsState()
    val uiState  by viewModel.uiState.collectAsState()
    var showNewProject by remember { mutableStateOf(false) }
    var showImport     by remember { mutableStateOf(false) }
    var importPath     by remember { mutableStateOf("") }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Background)) {

        // Decorative gradient header
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2436), Background)))) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = Primary.copy(alpha = 0.06f), radius = 250.dp.toPx(), center = Offset(size.width * 0.8f, 0f))
                drawCircle(color = Secondary.copy(alpha = 0.04f), radius = 180.dp.toPx(), center = Offset(30.dp.toPx(), 100.dp.toPx()))
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── TopBar ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AndroidIDE", style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold, color = OnBackground))
                    Text("Professional Android Development", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IDEIconButton(icon = Icons.Default.Settings, onClick = { viewModel.setScreen(com.androidide.viewmodels.IDEScreen.SETTINGS) })
                    IDEIconButton(icon = Icons.Default.Info, onClick = { viewModel.setScreen(com.androidide.viewmodels.IDEScreen.ABOUT) })
                }
            }

            // ── Quick Actions ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Add,
                    label = "New Project",
                    color = Primary,
                    onClick = { showNewProject = true }
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FolderOpen,
                    label = "Open / Import",
                    color = Secondary,
                    onClick = { showImport = true }
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Terminal,
                    label = "Terminal",
                    color = Tertiary,
                    onClick = { viewModel.setScreen(com.androidide.viewmodels.IDEScreen.TERMINAL) }
                )
            }

            // ── Recent Projects ────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Projects", style = MaterialTheme.typography.titleMedium.copy(
                            color = OnSurface, fontWeight = FontWeight.SemiBold))
                        Text("${projects.size} projects", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                }

                if (projects.isEmpty()) {
                    item { EmptyProjectsPlaceholder(onNew = { showNewProject = true }) }
                } else {
                    items(projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onClick = { viewModel.openProject(project) }
                        )
                    }
                }

                // Feature showcase cards
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Text("Features", style = MaterialTheme.typography.titleMedium.copy(
                        color = OnSurface, fontWeight = FontWeight.SemiBold))
                }
                item { FeatureGrid() }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().background(Background.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Primary)
                    Text("Creating project...", color = OnSurface)
                }
            }
        }
    }

    if (showNewProject) {
        NewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate  = { name, type, lang, pkg, minSdk ->
                showNewProject = false
                viewModel.createProject(name, type, lang, pkg, minSdk)
            }
        )
    }

    if (showImport) {
        ImportProjectDialog(
            path = importPath,
            onPathChange = { importPath = it },
            onDismiss = { showImport = false },
            onImport  = { path ->
                showImport = false
                viewModel.importProject(path)
            }
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(80.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = OnSurface),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Android, contentDescription = null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall.copy(
                    color = OnBackground, fontWeight = FontWeight.SemiBold))
                Text(project.path, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProjectTag(text = project.language.name, color = if (project.language == Language.KOTLIN) IconKotlin else IconJava)
                    ProjectTag(text = "SDK ${project.minSdk}+", color = IconGradle)
                    ProjectTag(text = project.type.name.replace("_", " ").lowercase()
                        .split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.take(18),
                        color = Primary.copy(alpha = 0.8f))
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = OnSurfaceDim)
        }
    }
}

@Composable
private fun ProjectTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(color = color, fontSize = 10.sp))
    }
}

@Composable
private fun EmptyProjectsPlaceholder(onNew: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Outline, RoundedCornerShape(16.dp))
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FolderOff, contentDescription = null,
                tint = OnSurfaceDim, modifier = Modifier.size(48.dp))
            Text("No projects yet", style = MaterialTheme.typography.titleMedium.copy(color = OnSurface))
            Text("Create your first Android project", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
            FilledTonalButton(onClick = onNew,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Primary.copy(alpha = 0.15f))) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Project", color = Primary)
            }
        }
    }
}

@Composable
private fun FeatureGrid() {
    val features = listOf(
        Triple(Icons.Default.Code,          "Smart IntelliSense",   "Context-aware code completion"),
        Triple(Icons.Default.Build,         "Gradle Build",         "One-click build & run"),
        Triple(Icons.Default.AccountTree,   "Git Integration",      "Commit, push, pull, branch"),
        Triple(Icons.Default.AutoFixHigh,   "AI Co-Pilot",          "OpenAI & Gemini support"),
        Triple(Icons.Default.Palette,       "Visual Designer",      "Drag-and-drop UI editor"),
        Triple(Icons.Default.Terminal,      "Terminal",             "Full Linux shell access"),
        Triple(Icons.Default.Storage,       "DB Inspector",         "Real-time database viewer"),
        Triple(Icons.Default.Analytics,     "APK Analyzer",         "Decompile & inspect APKs"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        features.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, title, sub) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        border = BorderStroke(1.dp, Outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.Medium))
                                Text(sub, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
