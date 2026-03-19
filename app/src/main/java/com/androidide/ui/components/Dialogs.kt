package com.androidide.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.androidide.data.models.*
import com.androidide.ui.theme.*

// ── New Project Dialog ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, type: ProjectType, lang: Language, pkg: String, minSdk: Int) -> Unit
) {
    var step         by remember { mutableIntStateOf(0) }
    var projectName  by remember { mutableStateOf("MyApp") }
    var packageName  by remember { mutableStateOf("com.example.myapp") }
    var selectedType by remember { mutableStateOf(ProjectType.COMPOSE_ACTIVITY) }
    var selectedLang by remember { mutableStateOf(Language.KOTLIN) }
    var selectedSdk  by remember { mutableIntStateOf(26) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, null, tint = Primary)
                Text("New Project", color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Project templates
                Text("Template", style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceDim))
                val templates = listOf(
                    ProjectType.COMPOSE_ACTIVITY   to Triple(Icons.Default.Layers,     "Jetpack Compose",     "Modern declarative UI"),
                    ProjectType.EMPTY_ACTIVITY     to Triple(Icons.Default.CropSquare, "Empty Activity",      "Minimal Kotlin setup"),
                    ProjectType.APPCOMPAT_ACTIVITY to Triple(Icons.Default.ViewQuilt,  "AppCompat + XML",     "Traditional XML layouts"),
                    ProjectType.NAVIGATION_DRAWER  to Triple(Icons.Default.Menu,       "Navigation Drawer",   "Side drawer navigation"),
                    ProjectType.BOTTOM_TABS        to Triple(Icons.Default.TableChart, "Bottom Tabs",         "Bottom navigation tabs"),
                    ProjectType.VIEWMODEL_LIVEDATA to Triple(Icons.Default.Analytics,  "ViewModel + LiveData","MVVM architecture"),
                    ProjectType.SERVICE_BROADCAST  to Triple(Icons.Default.Settings,   "Service + Receiver",  "Background processing"),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(110.dp)) {
                    items(templates) { (type, info) ->
                        val (icon, name, desc) = info
                        TemplateCard(
                            icon = icon, name = name, desc = desc,
                            selected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }

                HorizontalDivider(color = Outline)

                // Project Name
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        packageName = "com.example.${it.lowercase().replace(" ", "")}"
                    },
                    label = { Text("Project Name", color = OnSurfaceDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ideTextFieldColors()
                )

                // Package Name
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package Name", color = OnSurfaceDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ideTextFieldColors()
                )

                // Language
                Text("Language", style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceDim))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Language.values().forEach { lang ->
                        FilterChip(
                            selected = selectedLang == lang,
                            onClick  = { selectedLang = lang },
                            label    = { Text(lang.name) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (lang == Language.KOTLIN) IconKotlin.copy(0.2f) else IconJava.copy(0.2f),
                                selectedLabelColor = if (lang == Language.KOTLIN) IconKotlin else IconJava
                            )
                        )
                    }
                }

                // Min SDK
                Text("Minimum SDK", style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceDim))
                val sdkOptions = listOf(21 to "Android 5.0", 24 to "Android 7.0", 26 to "Android 8.0",
                    28 to "Android 9.0", 30 to "Android 11", 33 to "Android 13", 34 to "Android 14")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sdkOptions) { (sdk, name) ->
                        FilterChip(
                            selected = selectedSdk == sdk,
                            onClick  = { selectedSdk = sdk },
                            label    = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("API $sdk", style = MaterialTheme.typography.labelSmall)
                                    Text(name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = OnSurfaceDim))
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(projectName.trim(), selectedType, selectedLang, packageName.trim(), selectedSdk) },
                enabled = projectName.isNotBlank() && packageName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Project")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceDim) } }
    )
}

@Composable
private fun TemplateCard(
    icon: ImageVector, name: String, desc: String,
    selected: Boolean, onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(100.dp).fillMaxHeight(),
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Primary.copy(alpha = 0.15f) else SurfaceVariant),
        border = BorderStroke(1.dp, if (selected) Primary else Outline)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = if (selected) Primary else OnSurfaceDim, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(name, style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) Primary else OnSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(desc, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, color = OnSurfaceDim),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

// ── Import Project Dialog ──────────────────────────────────────────────────────
@Composable
fun ImportProjectDialog(
    path: String,
    onPathChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FolderOpen, null, tint = Secondary)
                Text("Import Project", color = OnBackground)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the path to the root directory of your Android project (must contain settings.gradle or settings.gradle.kts).",
                    style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim)
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = onPathChange,
                    label = { Text("Project Path", color = OnSurfaceDim) },
                    placeholder = { Text("/storage/emulated/0/Projects/MyApp", color = OnSurfaceDim.copy(0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Folder, null, tint = Secondary) },
                    colors = ideTextFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(path.trim()) },
                enabled = path.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Secondary, contentColor = OnPrimary)
            ) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceDim) } }
    )
}

// ── Name Input Dialog ──────────────────────────────────────────────────────────
@Composable
fun NameInputDialog(
    title: String,
    hint: String = "",
    initialValue: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        title = { Text(title, color = OnBackground) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(hint, color = OnSurfaceDim.copy(0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = ideTextFieldColors()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OnSurfaceDim) } }
    )
}

// ── Search/Replace Bar ─────────────────────────────────────────────────────────
@Composable
fun SearchReplaceBar(
    searchQuery: String,
    replaceQuery: String,
    resultCount: Int,
    onSearchChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onReplaceAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(color = SurfaceVariant, tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, null, tint = OnSurfaceDim, modifier = Modifier.size(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f).height(36.dp),
                    placeholder = { Text("Find", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)) },
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = OnBackground),
                    singleLine = true,
                    colors = ideTextFieldColors()
                )
                if (resultCount > 0) {
                    Text("$resultCount", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                }
                IDEIconButton(icon = Icons.Default.Close, onClick = onDismiss, size = 14.dp)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FindReplace, null, tint = OnSurfaceDim, modifier = Modifier.size(16.dp))
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceChange,
                    modifier = Modifier.weight(1f).height(36.dp),
                    placeholder = { Text("Replace", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)) },
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = OnBackground),
                    singleLine = true,
                    colors = ideTextFieldColors()
                )
                TextButton(
                    onClick = onReplaceAll,
                    modifier = Modifier.height(32.dp),
                    enabled = searchQuery.isNotBlank()
                ) { Text("All", color = Primary, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

// ── Dependency Dialog ──────────────────────────────────────────────────────────
@Composable
fun DependencySearchDialog(
    onDismiss: () -> Unit,
    onAdd: (MavenDependency) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val popular = remember { listOf(
        MavenDependency("androidx.compose.ui", "ui", "latest.release", "Compose UI"),
        MavenDependency("com.google.dagger", "hilt-android", "2.52", "Hilt DI"),
        MavenDependency("com.squareup.retrofit2", "retrofit", "2.11.0", "HTTP client"),
        MavenDependency("io.coil-kt", "coil-compose", "2.7.0", "Image loading"),
        MavenDependency("androidx.room", "room-runtime", "2.6.1", "SQLite ORM"),
        MavenDependency("com.google.code.gson", "gson", "2.11.0", "JSON parser"),
        MavenDependency("com.squareup.okhttp3", "okhttp", "4.12.0", "OkHttp client"),
        MavenDependency("androidx.navigation", "navigation-compose", "2.8.2", "Navigation"),
        MavenDependency("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.8.1", "Coroutines"),
        MavenDependency("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.6", "ViewModel"),
        MavenDependency("io.github.Rosemoe.sora-editor", "editor", "0.23.4", "Code editor"),
        MavenDependency("com.airbnb.android", "lottie-compose", "6.5.0", "Lottie animations"),
    )}
    val filtered = remember(query) {
        if (query.isBlank()) popular
        else popular.filter { it.artifactId.contains(query, true) || it.description.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Add Dependency", color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search Maven Central", color = OnSurfaceDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = OnSurfaceDim) },
                    colors = ideTextFieldColors()
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered) { dep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceVariant)
                                .clickable { onAdd(dep); onDismiss() }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Extension, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${dep.groupId}:${dep.artifactId}",
                                    style = MaterialTheme.typography.labelMedium.copy(color = OnBackground),
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(dep.description, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                            }
                            Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(IconGradle.copy(0.15f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(dep.version, style = MaterialTheme.typography.labelSmall.copy(color = IconGradle))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = OnSurfaceDim) } }
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────────
@Composable
fun ideTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Outline,
    focusedTextColor = OnBackground,
    unfocusedTextColor = OnBackground,
    cursorColor = Primary
)
