package com.androidide.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import com.androidide.data.models.*
import com.androidide.ui.components.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var apiKey       by remember { mutableStateOf("") }
    var provider     by remember { mutableStateOf(AIProvider.OPENAI) }
    var model        by remember { mutableStateOf("gpt-4o") }
    var baseUrl      by remember { mutableStateOf("https://api.openai.com/v1") }
    var showApiKey   by remember { mutableStateOf(false) }
    var fontSize     by remember { mutableFloatStateOf(13f) }
    var tabSize      by remember { mutableIntStateOf(4) }
    var autoSave     by remember { mutableStateOf(true) }
    var showSaved    by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // TopBar
        Surface(color = Surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IDEIconButton(icon = Icons.Default.ArrowBack, onClick = {
                    viewModel.setScreen(com.androidide.viewmodels.IDEScreen.EDITOR)
                })
                Text("Settings", style = MaterialTheme.typography.titleLarge.copy(
                    color = OnBackground, fontWeight = FontWeight.SemiBold))
            }
            HorizontalDivider(color = Outline)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AI Configuration ───────────────────────────────────────────────
            SettingsSection(title = "AI Co-Pilot", icon = Icons.Default.AutoAwesome, iconColor = Tertiary) {
                // Provider selector
                Text("Provider", style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceDim))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AIProvider.values().forEach { p ->
                        val label = when(p) {
                            AIProvider.OPENAI  -> "OpenAI"
                            AIProvider.GEMINI  -> "Gemini"
                            AIProvider.CLAUDE  -> "Claude"
                            AIProvider.CUSTOM  -> "Custom"
                        }
                        FilterChip(
                            selected = provider == p,
                            onClick  = {
                                provider = p
                                model = when(p) {
                                    AIProvider.OPENAI  -> "gpt-4o"
                                    AIProvider.GEMINI  -> "gemini-1.5-pro"
                                    AIProvider.CLAUDE  -> "claude-sonnet-4-6"
                                    AIProvider.CUSTOM  -> model
                                }
                                baseUrl = when(p) {
                                    AIProvider.OPENAI  -> "https://api.openai.com/v1"
                                    AIProvider.GEMINI  -> "https://generativelanguage.googleapis.com"
                                    AIProvider.CLAUDE  -> "https://api.anthropic.com/v1"
                                    AIProvider.CUSTOM  -> baseUrl
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key", color = OnSurfaceDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IDEIconButton(
                            icon = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            onClick = { showApiKey = !showApiKey },
                            size = 18.dp
                        )
                    },
                    colors = ideTextFieldColors()
                )

                // Model
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model", color = OnSurfaceDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = ideTextFieldColors()
                )

                // Base URL (custom only)
                if (provider == AIProvider.CUSTOM) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL", color = OnSurfaceDim) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = ideTextFieldColors()
                    )
                }

                Button(
                    onClick = {
                        viewModel.updateAIConfig(AIConfig(
                            provider = provider, apiKey = apiKey,
                            model = model, baseUrl = baseUrl
                        ))
                        showSaved = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Tertiary.copy(alpha = 0.8f), contentColor = OnPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save AI Config")
                }
            }

            // ── Editor Settings ────────────────────────────────────────────────
            SettingsSection(title = "Editor", icon = Icons.Default.Code, iconColor = Primary) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Font Size", style = MaterialTheme.typography.bodyMedium.copy(color = OnBackground))
                        Text("${fontSize.toInt()}sp", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 10f..20f,
                        steps = 9,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tab Size", style = MaterialTheme.typography.bodyMedium.copy(color = OnBackground))
                        Text("$tabSize spaces", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 4, 8).forEach { size ->
                            FilterChip(
                                selected = tabSize == size,
                                onClick = { tabSize = size },
                                label = { Text("$size") }
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Save", style = MaterialTheme.typography.bodyMedium.copy(color = OnBackground))
                        Text("Save after 1.5s of inactivity", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                    }
                    Switch(
                        checked = autoSave,
                        onCheckedChange = { autoSave = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = Primary.copy(0.3f))
                    )
                }
            }

            // ── Build Settings ─────────────────────────────────────────────────
            SettingsSection(title = "Build", icon = Icons.Default.Build, iconColor = Secondary) {
                SettingItem(title = "Gradle Version", subtitle = "8.9", icon = Icons.Default.Update, onClick = {})
                SettingItem(title = "Java Home", subtitle = System.getProperty("java.home") ?: "System default", icon = Icons.Default.Coffee, onClick = {})
                SettingItem(title = "Build Cache", subtitle = "Clear build cache", icon = Icons.Default.Delete, onClick = {
                    viewModel.cleanProject()
                })
            }

            // ── About ──────────────────────────────────────────────────────────
            SettingsSection(title = "About", icon = Icons.Default.Info, iconColor = OnSurfaceDim) {
                SettingItem(title = "AndroidIDE", subtitle = "Version 1.0.0 · Build 1", icon = Icons.Default.Android, onClick = {})
                SettingItem(title = "Architecture", subtitle = "MVVM · Hilt · Compose · Coroutines", icon = Icons.Default.Architecture, onClick = {})
                SettingItem(title = "Open Source", subtitle = "View on GitHub", icon = Icons.Default.Code, onClick = {})
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showSaved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Snackbar(modifier = Modifier.padding(16.dp), containerColor = LogSuccess) {
                Text("AI configuration saved!", color = OnPrimary)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
                Text(title, style = MaterialTheme.typography.titleSmall.copy(
                    color = OnBackground, fontWeight = FontWeight.SemiBold))
            }
            HorizontalDivider(color = Outline)
            content()
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = OnSurfaceDim, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(color = OnBackground))
            Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
        }
        Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ideTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary, unfocusedBorderColor = Outline,
    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, cursorColor = Primary
)
