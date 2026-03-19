package com.androidide.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.core.build.DependencyResolver
import com.androidide.core.build.MavenSearchResult
import com.androidide.core.build.ResolveResult
import com.androidide.data.models.BuildLog
import com.androidide.data.models.BuildLogLevel
import com.androidide.data.models.MavenDependency
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyManagerScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    var searchQuery   by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MavenSearchResult>>(emptyList()) }
    var isSearching   by remember { mutableStateOf(false) }
    var downloadLogs  by remember { mutableStateOf<List<BuildLog>>(emptyList()) }
    var isDownloading by remember { mutableStateOf(false) }
    var activeTab     by remember { mutableIntStateOf(0) }
    var selectedDep   by remember { mutableStateOf<MavenSearchResult?>(null) }
    var downloadResult by remember { mutableStateOf<ResolveResult?>(null) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var manualGroup   by remember { mutableStateOf("") }
    var manualArtifact by remember { mutableStateOf("") }
    var manualVersion  by remember { mutableStateOf("") }

    val scope      = rememberCoroutineScope()
    val resolver   = remember { viewModel.dependencyResolver }
    val logState   = rememberLazyListState()

    // Auto scroll logs
    LaunchedEffect(downloadLogs.size) {
        if (downloadLogs.isNotEmpty()) logState.animateScrollToItem(downloadLogs.size - 1)
    }

    // Initial popular list
    LaunchedEffect(Unit) {
        searchResults = resolver.popularLibraries.take(30).map {
            MavenSearchResult(it.groupId, it.artifactId, it.latestVersion, it.description, "")
        }
    }

    // Collect resolver logs
    LaunchedEffect(Unit) {
        resolver.logs.collect { log -> downloadLogs = downloadLogs + log }
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Extension, null, tint = Secondary, modifier = Modifier.size(20.dp))
                        Text("Dependency Manager", color = OnBackground, fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Close", tint = OnSurfaceDim)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Manual add", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Search bar ────────────────────────────────────────────────
            Surface(color = Surface) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value        = searchQuery,
                        onValueChange= { searchQuery = it },
                        modifier     = Modifier.fillMaxWidth(),
                        placeholder  = { Text("Search Maven Central, Google Maven...", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall) },
                        leadingIcon  = { Icon(Icons.Default.Search, null, tint = OnSurfaceDim) },
                        trailingIcon = {
                            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                            else if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, tint = OnSurfaceDim) }
                        },
                        singleLine   = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            scope.launch {
                                isSearching = true
                                searchResults = resolver.search(searchQuery)
                                isSearching = false
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                            cursorColor = Primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Quick filter chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categories = listOf("compose","network","database","di","testing","firebase","image","media")
                        items(categories) { cat ->
                            AssistChip(
                                onClick = {
                                    searchQuery = cat
                                    scope.launch {
                                        isSearching = true
                                        searchResults = resolver.search(cat)
                                        isSearching = false
                                    }
                                },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = SurfaceVariant, labelColor = OnSurface
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Outline)

            // ── Tabs ──────────────────────────────────────────────────────
            TabRow(selectedTabIndex = activeTab, containerColor = Surface, contentColor = Primary) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 },
                    text = { Text("Libraries (${searchResults.size})", style = MaterialTheme.typography.labelMedium) })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Download Log", style = MaterialTheme.typography.labelMedium)
                            if (downloadLogs.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(10.dp), color = Primary.copy(0.2f)) {
                                    Text("${downloadLogs.size}", style = MaterialTheme.typography.labelSmall.copy(color = Primary),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                        }
                    })
            }

            when (activeTab) {
                // ── Search Results ──────────────────────────────────────────
                0 -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchResults.isEmpty() && !isSearching) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text("No results. Try a different search term.", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(searchResults) { result ->
                        DependencyCard(
                            result       = result,
                            isDownloading= isDownloading && selectedDep?.coordinate == result.coordinate,
                            onAdd = { dep ->
                                scope.launch {
                                    // 1. Add to build.gradle
                                    val project = viewModel.projectManager.currentProject.value
                                    if (project != null) {
                                        viewModel.gradleManager.addDependency(project.path, dep.toMavenDependency())
                                    }
                                    // 2. Resolve & download JARs
                                    selectedDep   = dep
                                    isDownloading = true
                                    activeTab     = 1
                                    downloadLogs  = emptyList()
                                    downloadResult = resolver.resolveDependency(dep.toMavenDependency())
                                    isDownloading  = false
                                }
                            }
                        )
                    }
                }

                // ── Download Log ────────────────────────────────────────────
                1 -> Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    if (downloadResult != null) {
                        val res = downloadResult!!
                        Surface(color = if (res.success) GitAdded.copy(0.1f) else LogError.copy(0.1f),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    null, tint = if (res.success) GitAdded else LogError, modifier = Modifier.size(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (res.success) "✓ Resolved ${res.artifacts.size} artifacts" else "✗ ${res.failed.size} failed",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = if (res.success) GitAdded else LogError, fontWeight = FontWeight.SemiBold))
                                    Text("${res.artifacts.size} JARs • ${res.fromCacheCount} cached • ${res.totalSize / 1024}KB",
                                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                                }
                                if (isDownloading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                            }
                        }
                        HorizontalDivider(color = Outline)
                    }

                    // Logs
                    LazyColumn(
                        state = logState,
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (downloadLogs.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Download, null, tint = OnSurfaceDim.copy(0.3f), modifier = Modifier.size(40.dp))
                                        Text("Download log will appear here", color = OnSurfaceDim.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                                        Text("Tap '+ Add' on any library to start", color = OnSurfaceDim.copy(0.35f), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        items(downloadLogs) { log ->
                            val color = when (log.level) {
                                BuildLogLevel.ERROR   -> LogError
                                BuildLogLevel.WARNING -> LogWarning
                                BuildLogLevel.SUCCESS -> GitAdded
                                BuildLogLevel.DEBUG   -> OnSurfaceDim
                                else                  -> OnSurface
                            }
                            Text(log.message, style = MaterialTheme.typography.bodySmall.copy(
                                color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp))
                        }
                    }
                }
            }
        }
    }

    // ── Manual Add Dialog ─────────────────────────────────────────────────
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Add, null, tint = Primary)
                    Text("Add Dependency Manually", color = OnBackground, fontWeight = FontWeight.SemiBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter coordinates from Maven Central:", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                    Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVariant) {
                        Text(
                            "com.squareup.retrofit2:retrofit:2.11.0",
                            style = MaterialTheme.typography.labelSmall.copy(color = Secondary, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    DepTextField("Group ID", manualGroup, "com.squareup.retrofit2") { manualGroup = it }
                    DepTextField("Artifact ID", manualArtifact, "retrofit") { manualArtifact = it }
                    DepTextField("Version", manualVersion, "2.11.0") { manualVersion = it }
                }
            },
            confirmButton = {
                Button(
                    enabled = manualGroup.isNotBlank() && manualArtifact.isNotBlank() && manualVersion.isNotBlank(),
                    onClick = {
                        val dep = MavenDependency(manualGroup.trim(), manualArtifact.trim(), manualVersion.trim())
                        scope.launch {
                            val project = viewModel.projectManager.currentProject.value
                            if (project != null) viewModel.gradleManager.addDependency(project.path, dep)
                            isDownloading = true; activeTab = 1; downloadLogs = emptyList()
                            downloadResult = resolver.resolveDependency(dep)
                            isDownloading  = false
                        }
                        showAddDialog = false
                    }
                ) { Text("Add & Download") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

// ── Dependency Card ────────────────────────────────────────────────────────
@Composable
private fun DependencyCard(
    result: MavenSearchResult,
    isDownloading: Boolean,
    onAdd: (MavenSearchResult) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = SurfaceVariant,
        border = BorderStroke(1.dp, Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Icon
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Extension, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.artifactId, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.groupId, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (result.description.isNotEmpty()) {
                    Text(result.description, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface, fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Version badge
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = IconGradle.copy(0.15f)) {
                        Text(result.latestVersion, style = MaterialTheme.typography.labelSmall.copy(color = IconGradle, fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = Secondary.copy(0.1f)) {
                        Text("implementation", style = MaterialTheme.typography.labelSmall.copy(color = Secondary, fontSize = 9.sp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
            // Add button
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
            } else {
                Button(
                    onClick = { onAdd(result) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
private fun DepTextField(label: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary, unfocusedBorderColor = Outline,
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
            cursorColor = Primary, focusedLabelColor = Primary, unfocusedLabelColor = OnSurfaceDim
        )
    )
}
