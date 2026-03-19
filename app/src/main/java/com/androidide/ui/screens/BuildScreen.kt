package com.androidide.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.core.build.KotlinCompilerEngine
import com.androidide.core.build.OnDeviceBuildEngine
import com.androidide.core.build.KotlinCompileResult
import com.androidide.core.build.RealBuildResult
import com.androidide.data.models.BuildLog
import com.androidide.data.models.BuildLogLevel
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val scope        = rememberCoroutineScope()
    val engine         = remember { viewModel.onDeviceBuildEngine }
    val kotlinEngine   = remember { viewModel.kotlinCompilerEngine }
    val project        = viewModel.projectManager.currentProject.collectAsState().value

    var buildStatus    by remember { mutableStateOf(engine.getBuildStatus()) }
    var kotlinStatus   by remember { mutableStateOf(kotlinEngine.getStatus()) }
    var logs           by remember { mutableStateOf<List<BuildLog>>(emptyList()) }
    var buildResult    by remember { mutableStateOf<RealBuildResult?>(null) }
    var isDownloading  by remember { mutableStateOf(false) }
    var isDownloadingKt by remember { mutableStateOf(false) }
    var isBuilding     by remember { mutableStateOf(false) }
    var dlProgress     by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var ktDlProgress   by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val progress       by engine.progress.collectAsState()
    val logState       = rememberLazyListState()

    // Collect build logs
    LaunchedEffect(Unit) {
        engine.logs.collect { log -> logs = logs + log }
    }
    LaunchedEffect(Unit) {
        kotlinEngine.logs.collect { log -> logs = logs + log }
    }
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) logState.animateScrollToItem(logs.size - 1)
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Build, null, tint = Secondary, modifier = Modifier.size(20.dp))
                        Text("Build", color = OnBackground, fontWeight = FontWeight.SemiBold)
                        if (project != null) {
                            Text("· ${project.name}", color = OnSurfaceDim,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = OnSurfaceDim)
                    }
                },
                actions = {
                    if (buildResult?.success == true) {
                        Button(
                            onClick = { buildResult?.apkPath?.let { viewModel.installApk(it) } },
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GitAdded),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Install APK", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Tool Status Cards ──────────────────────────────────────────
            Surface(color = Surface) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Build Tools", style = MaterialTheme.typography.labelMedium.copy(
                        color = OnSurfaceDim, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold))

                    // ── Native tools ───────────────────────────────────────
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolCard("AAPT2",       buildStatus.aapt2Ready,     dlProgress["aapt2"],      Modifier.weight(1f))
                        ToolCard("DX",          buildStatus.dxReady,        dlProgress["dx"],          Modifier.weight(1f))
                        ToolCard("android.jar", buildStatus.androidJarReady,dlProgress["android.jar"], Modifier.weight(1f))
                    }

                    // ── Kotlin compiler ────────────────────────────────────
                    HorizontalDivider(color = Outline.copy(alpha = 0.5f))
                    Text("Kotlin Compiler", style = MaterialTheme.typography.labelMedium.copy(
                        color = OnSurfaceDim, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolCard("kotlinc",       kotlinStatus.kotlincReady,  ktDlProgress["Kotlin Compiler"],        Modifier.weight(1f))
                        ToolCard("Compose Plugin",kotlinStatus.composeReady,  ktDlProgress["Compose Compiler Plugin"],Modifier.weight(1f))
                        ToolCard("stdlib",        kotlinStatus.stdlibReady,   ktDlProgress["Kotlin Stdlib"],          Modifier.weight(1f))
                    }
                    if (!kotlinStatus.canCompileKotlin) {
                        // Download Kotlin compiler button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isDownloadingKt = true
                                    ktDlProgress    = emptyMap()
                                    kotlinEngine.downloadCompiler(includeCompose = true) { name, pct ->
                                        ktDlProgress = ktDlProgress + (name to pct)
                                    }
                                    kotlinStatus    = kotlinEngine.getStatus()
                                    isDownloadingKt = false
                                }
                            },
                            enabled  = !isDownloadingKt && !isBuilding,
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            border   = BorderStroke(1.dp, IconKotlin)
                        ) {
                            if (isDownloadingKt) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IconKotlin)
                                Spacer(Modifier.width(6.dp))
                                val currentDl = ktDlProgress.entries.lastOrNull()
                                Text(
                                    if (currentDl != null) "Downloading ${currentDl.key}: ${currentDl.value}%"
                                    else "Downloading Kotlin compiler...",
                                    style = MaterialTheme.typography.labelSmall.copy(color = IconKotlin)
                                )
                            } else {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp), tint = IconKotlin)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Download Kotlin + Compose Compiler (~75MB)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = IconKotlin)
                                )
                            }
                        }
                        Text(
                            "Required for .kt files and Jetpack Compose",
                            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontSize = 10.sp),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        Surface(shape = RoundedCornerShape(6.dp), color = IconKotlin.copy(0.1f),
                            border = BorderStroke(1.dp, IconKotlin.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = IconKotlin, modifier = Modifier.size(16.dp))
                                Column {
                                    Text("Kotlin Compiler Ready ✓", style = MaterialTheme.typography.labelMedium.copy(color = IconKotlin, fontWeight = FontWeight.SemiBold))
                                    Text(
                                        if (kotlinStatus.composeReady) "Kotlin + Jetpack Compose ✓"
                                        else "Kotlin only (no Compose plugin)",
                                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text("${kotlinEngine.getCacheSizeMb()}MB", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!buildStatus.allReady) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloading = true
                                        dlProgress = emptyMap()
                                        logs = emptyList()
                                        engine.downloadTools { name, pct ->
                                            dlProgress = dlProgress + (name to pct)
                                        }
                                        buildStatus = engine.getBuildStatus()
                                        isDownloading = false
                                    }
                                },
                                enabled = !isDownloading && !isBuilding,
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) {
                                if (isDownloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = OnPrimary)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Downloading...", style = MaterialTheme.typography.labelSmall)
                                } else {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download Tools (~20MB)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (project == null) return@Button
                                scope.launch {
                                    isBuilding   = true
                                    buildResult  = null
                                    logs         = emptyList()
                                    buildResult  = engine.buildApk(project.path)
                                    isBuilding   = false
                                    buildStatus  = engine.getBuildStatus()
                                }
                            },
                            enabled = buildStatus.allReady && !isBuilding && !isDownloading && project != null,
                            modifier = Modifier.height(36.dp),
                            colors   = ButtonDefaults.buttonColors(
                                containerColor = if (buildStatus.allReady) Secondary else SurfaceVariant
                            )
                        ) {
                            if (isBuilding) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                                    color = if (buildStatus.allReady) OnPrimary else OnSurfaceDim)
                                Spacer(Modifier.width(6.dp))
                                Text("Building...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp),
                                    tint = if (buildStatus.allReady) Color(0xFF1C1B1F) else OnSurfaceDim)
                                Spacer(Modifier.width(6.dp))
                                Text("Build APK", style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (buildStatus.allReady) Color(0xFF1C1B1F) else OnSurfaceDim))
                            }
                        }
                    }

                    // Build progress bar
                    if (isBuilding) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color    = Secondary,
                            trackColor = SurfaceVariant
                        )
                        Text("$progress%", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                }
            }

            HorizontalDivider(color = Outline)

            // ── Build Result Banner ────────────────────────────────────────
            buildResult?.let { result ->
                Surface(
                    color = if (result.success) GitAdded.copy(0.1f) else LogError.copy(0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint    = if (result.success) GitAdded else LogError,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (result.success) "Build Successful!" else "Build Failed",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color       = if (result.success) GitAdded else LogError,
                                    fontWeight  = FontWeight.SemiBold
                                )
                            )
                            if (result.success && result.apkPath != null) {
                                Text(
                                    result.apkPath,
                                    style    = MaterialTheme.typography.labelSmall.copy(
                                        color = OnSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text("Built in ${result.durationMs / 1000}s",
                                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                            }
                            result.error?.let { err ->
                                Text(err, style = MaterialTheme.typography.labelSmall.copy(color = LogError))
                            }
                        }
                        if (result.success) {
                            Button(
                                onClick = { result.apkPath?.let { viewModel.installApk(it) } },
                                colors  = ButtonDefaults.buttonColors(containerColor = GitAdded),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Install", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider(color = Outline)
            }

            // ── Build Log ──────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF080B10))) {
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Terminal, null, tint = OnSurfaceDim.copy(0.3f), modifier = Modifier.size(40.dp))
                            Text("Build log will appear here",
                                color = OnSurfaceDim.copy(0.5f), style = MaterialTheme.typography.bodySmall)
                            if (!buildStatus.allReady)
                                Text("Download tools first, then build",
                                    color = OnSurfaceDim.copy(0.35f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    LazyColumn(
                        state            = logState,
                        modifier         = Modifier.fillMaxSize(),
                        contentPadding   = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(logs) { log ->
                            val color = when (log.level) {
                                BuildLogLevel.ERROR   -> LogError
                                BuildLogLevel.WARNING -> LogWarning
                                BuildLogLevel.SUCCESS -> GitAdded
                                BuildLogLevel.DEBUG   -> OnSurfaceDim
                                else                  -> OnSurface
                            }
                            Text(
                                log.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color      = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 11.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tool Status Card ───────────────────────────────────────────────────────────
@Composable
private fun ToolCard(name: String, ready: Boolean, progress: Int?, modifier: Modifier = Modifier) {
    Surface(
        shape  = RoundedCornerShape(8.dp),
        color  = if (ready) GitAdded.copy(0.08f) else SurfaceVariant,
        border = BorderStroke(1.dp, if (ready) GitAdded.copy(0.4f) else Outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                if (ready) Icons.Default.CheckCircle else Icons.Default.Download,
                null,
                tint     = if (ready) GitAdded else OnSurfaceDim,
                modifier = Modifier.size(18.dp)
            )
            Text(
                name,
                style    = MaterialTheme.typography.labelSmall.copy(
                    color      = if (ready) GitAdded else OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 9.sp
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (progress != null && !ready) {
                LinearProgressIndicator(
                    progress  = { progress / 100f },
                    modifier  = Modifier.fillMaxWidth().height(2.dp),
                    color     = Primary,
                    trackColor = Outline
                )
                Text("$progress%", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontSize = 8.sp))
            } else {
                Text(if (ready) "Ready ✓" else "Not downloaded",
                    style = MaterialTheme.typography.labelSmall.copy(color = if (ready) GitAdded else OnSurfaceDim, fontSize = 8.sp))
            }
        }
    }
}
