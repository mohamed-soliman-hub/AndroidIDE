package com.androidide.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.core.build.ActionsBuildResult
import com.androidide.core.build.GitHubActionsService
import com.androidide.data.models.BuildLog
import com.androidide.data.models.BuildLogLevel
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBuildScreen(viewModel: MainViewModel, onClose: () -> Unit) {
    val scope     = rememberCoroutineScope()
    val service   = remember { viewModel.gitHubActionsService }
    val project   = viewModel.projectManager.currentProject.collectAsState().value
    val uriHandler = LocalUriHandler.current

    // Config state
    var token        by remember { mutableStateOf(service.getConfig().token) }
    var repoOwner    by remember { mutableStateOf(service.getConfig().repoOwner) }
    var repoName     by remember { mutableStateOf(service.getConfig().repoName) }
    var showToken    by remember { mutableStateOf(false) }
    var configSaved  by remember { mutableStateOf(service.getConfig().isValid) }

    // Build state
    val buildPhase   by service.state.collectAsState()
    val buildProgress by service.progress.collectAsState()
    var buildResult  by remember { mutableStateOf<ActionsBuildResult?>(null) }
    var isBuilding   by remember { mutableStateOf(false) }
    var isValidating by remember { mutableStateOf(false) }
    var validationMsg by remember { mutableStateOf<String?>(null) }
    var logs         by remember { mutableStateOf<List<BuildLog>>(emptyList()) }
    val logState     = rememberLazyListState()

    // Collect logs
    LaunchedEffect(Unit) {
        service.logs.collect { log -> logs = logs + log }
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
                        Icon(Icons.Default.Cloud, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text("Cloud Build", color = OnBackground, fontWeight = FontWeight.SemiBold)
                        Text("via GitHub Actions", color = OnSurfaceDim, style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = OnSurfaceDim) } },
                actions = {
                    if (buildResult?.success == true) {
                        Button(
                            onClick = { buildResult?.apkPath?.let { service.installApk(it) } },
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = GitAdded),
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

            // ── How it works banner ───────────────────────────────────────
            if (!configSaved) {
                Surface(color = Primary.copy(0.08f), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How Cloud Build works", style = MaterialTheme.typography.labelMedium.copy(color = Primary, fontWeight = FontWeight.SemiBold))
                        val steps = listOf(
                            "1" to "You enter a GitHub Token once (free, takes 1 min)",
                            "2" to "Press 'Build' — your project uploads automatically",
                            "3" to "GitHub's servers build it (Ubuntu + JDK17 + Android SDK)",
                            "4" to "APK downloads to your phone and installs",
                            "⏱" to "Total time: ~4 minutes • Cost: Free (2000 min/month)"
                        )
                        steps.forEach { (icon, text) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(icon, style = MaterialTheme.typography.labelSmall.copy(color = Primary, fontFamily = FontFamily.Monospace))
                                Text(text, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                            }
                        }
                    }
                }
                HorizontalDivider(color = Outline)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                // ── SETUP SECTION ──────────────────────────────────────────
                item {
                    Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            // Section header
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (configSaved) Icons.Default.CheckCircle else Icons.Default.Settings,
                                    null,
                                    tint = if (configSaved) GitAdded else Primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    if (configSaved) "GitHub Connected ✓" else "Step 1: Connect GitHub",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (configSaved) GitAdded else OnBackground,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                if (configSaved) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { configSaved = false }) {
                                        Text("Edit", style = MaterialTheme.typography.labelSmall.copy(color = Primary))
                                    }
                                }
                            }

                            AnimatedVisibility(visible = !configSaved) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    // Step 1: Create Token
                                    StepCard(
                                        number = "1",
                                        title  = "Create GitHub Personal Access Token",
                                        content = {
                                            Text("Go to: GitHub → Settings → Developer Settings → Personal Access Tokens → Tokens (classic)",
                                                style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                                            Text("Select scopes: ✅ repo  ✅ workflow",
                                                style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                                            OutlinedButton(
                                                onClick = { uriHandler.openUri("https://github.com/settings/tokens/new?scopes=repo,workflow&description=AndroidIDE") },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Open GitHub Token Page", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )

                                    // Step 2: Create Repo
                                    StepCard(
                                        number = "2",
                                        title  = "Create a GitHub Repository (private is fine)",
                                        content = {
                                            Text("The workflow file will be added automatically.",
                                                style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                                            OutlinedButton(
                                                onClick = { uriHandler.openUri("https://github.com/new") },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Create Repository on GitHub", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    )

                                    // Step 3: Enter details
                                    StepCard(
                                        number = "3",
                                        title  = "Enter your details",
                                        content = {
                                            // Token
                                            OutlinedTextField(
                                                value         = token,
                                                onValueChange = { token = it },
                                                label         = { Text("GitHub Token (ghp_...)") },
                                                modifier      = Modifier.fillMaxWidth(),
                                                singleLine    = true,
                                                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                trailingIcon  = {
                                                    IconButton(onClick = { showToken = !showToken }) {
                                                        Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = OnSurfaceDim)
                                                    }
                                                },
                                                colors = ideInputColors()
                                            )
                                            // Repo owner
                                            OutlinedTextField(
                                                value         = repoOwner,
                                                onValueChange = { repoOwner = it.lowercase().trim() },
                                                label         = { Text("GitHub Username") },
                                                placeholder   = { Text("yourusername", color = OnSurfaceDim) },
                                                modifier      = Modifier.fillMaxWidth(),
                                                singleLine    = true,
                                                leadingIcon   = { Icon(Icons.Default.Person, null, tint = OnSurfaceDim) },
                                                colors        = ideInputColors()
                                            )
                                            // Repo name
                                            OutlinedTextField(
                                                value         = repoName,
                                                onValueChange = { repoName = it.trim() },
                                                label         = { Text("Repository Name") },
                                                placeholder   = { Text("my-android-builds", color = OnSurfaceDim) },
                                                modifier      = Modifier.fillMaxWidth(),
                                                singleLine    = true,
                                                leadingIcon   = { Icon(Icons.Default.Code, null, tint = OnSurfaceDim) },
                                                colors        = ideInputColors()
                                            )

                                            // Validation message
                                            validationMsg?.let { msg ->
                                                val isOk = msg.startsWith("✓")
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isOk) GitAdded.copy(0.1f) else LogError.copy(0.1f)
                                                ) {
                                                    Text(msg,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = if (isOk) GitAdded else LogError),
                                                        modifier = Modifier.padding(10.dp))
                                                }
                                            }

                                            // Connect button
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        isValidating = true
                                                        validationMsg = null
                                                        service.setConfig(GitHubActionsService.Config(
                                                            token     = token.trim(),
                                                            repoOwner = repoOwner.trim(),
                                                            repoName  = repoName.trim()
                                                        ))
                                                        val result = service.validateConfig()
                                                        validationMsg = result.message
                                                        if (result.success) {
                                                            configSaved = true
                                                        }
                                                        isValidating = false
                                                    }
                                                },
                                                enabled  = token.isNotBlank() && repoOwner.isNotBlank() && repoName.isNotBlank() && !isValidating,
                                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                                colors   = ButtonDefaults.buttonColors(containerColor = Primary)
                                            ) {
                                                if (isValidating) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Validating...")
                                                } else {
                                                    Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("Connect GitHub", fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            // When configured: show summary
                            AnimatedVisibility(visible = configSaved) {
                                Surface(shape = RoundedCornerShape(10.dp), color = SurfaceVariant, border = BorderStroke(1.dp, Outline)) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.GitHub, null, tint = OnBackground, modifier = Modifier.size(24.dp))
                                        Column {
                                            Text("@${service.getConfig().repoOwner} / ${service.getConfig().repoName}",
                                                style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
                                            Text("Free plan: 2000 build-minutes/month",
                                                style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                                        }
                                        Spacer(Modifier.weight(1f))
                                        Surface(shape = RoundedCornerShape(6.dp), color = GitAdded.copy(0.15f)) {
                                            Text("Connected", style = MaterialTheme.typography.labelSmall.copy(color = GitAdded),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── BUILD SECTION ──────────────────────────────────────────
                item {
                    HorizontalDivider(color = Outline)
                    Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            Text("Step 2: Build", style = MaterialTheme.typography.titleSmall.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))

                            // Project info
                            if (project != null) {
                                Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVariant, border = BorderStroke(1.dp, Outline)) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Android, null, tint = GitAdded, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text(project.name, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground))
                                            Text(project.path, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(8.dp), color = LogWarning.copy(0.1f), border = BorderStroke(1.dp, LogWarning.copy(0.3f))) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.Warning, null, tint = LogWarning, modifier = Modifier.size(16.dp))
                                        Text("No project open. Open a project first.", style = MaterialTheme.typography.bodySmall.copy(color = LogWarning))
                                    }
                                }
                            }

                            // Build button
                            Button(
                                onClick = {
                                    if (project == null) return@Button
                                    scope.launch {
                                        isBuilding  = true
                                        buildResult = null
                                        logs        = emptyList()
                                        buildResult = service.buildProject(project.path)
                                        isBuilding  = false
                                    }
                                },
                                enabled  = configSaved && project != null && !isBuilding,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor = if (configSaved && project != null) Primary else SurfaceVariant
                                )
                            ) {
                                if (isBuilding) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OnPrimary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(buildPhase.label, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Build with GitHub Actions", fontWeight = FontWeight.SemiBold,
                                        color = if (configSaved && project != null) OnPrimary else OnSurfaceDim)
                                }
                            }

                            // Progress
                            AnimatedVisibility(visible = isBuilding) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { buildProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color    = Primary,
                                        trackColor = SurfaceVariant
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(buildPhase.label, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                                        Text("$buildProgress%", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontFamily = FontFamily.Monospace))
                                    }
                                }
                            }

                            // Build result
                            buildResult?.let { result ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (result.success) GitAdded.copy(0.1f) else LogError.copy(0.1f),
                                    border = BorderStroke(1.dp, if (result.success) GitAdded.copy(0.4f) else LogError.copy(0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(
                                                if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                                null,
                                                tint = if (result.success) GitAdded else LogError,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                if (result.success) "Build Successful! 🎉" else "Build Failed",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = if (result.success) GitAdded else LogError,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                        }
                                        if (result.success) {
                                            Text("Built in ${result.durationMs / 1000}s • APK ready",
                                                style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                                            result.runId?.let { rid ->
                                                TextButton(
                                                    onClick = { uriHandler.openUri("https://github.com/${service.getConfig().repoFull}/actions/runs/$rid") },
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(12.dp), tint = Primary)
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("View on GitHub", style = MaterialTheme.typography.labelSmall.copy(color = Primary))
                                                }
                                            }
                                            Button(
                                                onClick = { result.apkPath?.let { service.installApk(it) } },
                                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = GitAdded)
                                            ) {
                                                Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Install APK on this device", fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF1C1B1F))
                                            }
                                        } else {
                                            Text(result.error ?: "Unknown error",
                                                style = MaterialTheme.typography.bodySmall.copy(color = LogError))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── BUILD LOG ──────────────────────────────────────────────
                item {
                    HorizontalDivider(color = Outline)
                    if (logs.isNotEmpty()) {
                        Surface(color = Color(0xFF080B10), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 300.dp)) {
                            LazyColumn(
                                state = logState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(10.dp),
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
                                    Text(log.message, style = MaterialTheme.typography.bodySmall.copy(
                                        color = color, fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp, lineHeight = 16.sp))
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

// ── Helper Composables ─────────────────────────────────────────────────────────
@Composable
private fun StepCard(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceVariant, border = BorderStroke(1.dp, Outline)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(0.2f)) {
                    Text(number, style = MaterialTheme.typography.labelMedium.copy(color = Primary, fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Text(title, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
            }
            content()
        }
    }
}

@Composable
private fun ideInputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary, unfocusedBorderColor = Outline,
    focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
    cursorColor          = Primary, focusedLabelColor = Primary,
    unfocusedLabelColor  = OnSurfaceDim
)
