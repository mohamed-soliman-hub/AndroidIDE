package com.androidide.ui.preview

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.androidide.data.models.EditorTab
import com.androidide.ui.components.CodeEditorPane
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import com.androidide.viewmodels.SearchResult

@Composable
fun SplitPreviewScreen(viewModel: MainViewModel, activeTab: EditorTab?, onClose: () -> Unit) {
    val completions  by viewModel.completions.collectAsState()
    val showComplete by viewModel.showCompletions.collectAsState()
    var splitRatio   by remember { mutableFloatStateOf(0.5f) }
    var previewOnly  by remember { mutableStateOf(false) }
    var codeOnly     by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // Split controls bar
        Surface(color = Surface) {
            Row(modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Preview, null, tint = Tertiary, modifier = Modifier.size(14.dp))
                Text("Split Preview", style = MaterialTheme.typography.labelMedium.copy(color = OnBackground))
                Spacer(Modifier.weight(1f))
                listOf(
                    Triple(Icons.Default.Code,         "Code",    codeOnly),
                    Triple(Icons.Default.ViewSidebar,  "50/50",   !codeOnly && !previewOnly),
                    Triple(Icons.Default.Preview,      "Preview", previewOnly),
                ).forEachIndexed { i, (icon, label, active) ->
                    Surface(
                        onClick = { when(i) { 0 -> { codeOnly = !codeOnly; previewOnly = false }; 1 -> { codeOnly = false; previewOnly = false; splitRatio = 0.5f }; 2 -> { previewOnly = !previewOnly; codeOnly = false } } },
                        shape = RoundedCornerShape(4.dp),
                        color = if (active) Primary.copy(0.2f) else SurfaceVariant,
                        border = BorderStroke(1.dp, if (active) Primary else Outline),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(icon, null, tint = if (active) Primary else OnSurfaceDim, modifier = Modifier.size(11.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = if (active) Primary else OnSurfaceDim, fontSize = 9.sp))
                        }
                    }
                }
                if (!previewOnly && !codeOnly) Slider(value = splitRatio, onValueChange = { splitRatio = it }, valueRange = 0.3f..0.7f,
                    modifier = Modifier.width(80.dp).height(24.dp), colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                }
            }
        }
        HorizontalDivider(color = Outline)

        Row(modifier = Modifier.fillMaxSize()) {
            // Code editor
            AnimatedVisibility(visible = !previewOnly, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
                Box(modifier = Modifier.fillMaxHeight().weight(if (codeOnly) 1f else splitRatio)) {
                    if (activeTab != null) {
                        CodeEditorPane(tab = activeTab, completions = completions, showCompletions = showComplete,
                            searchResults = emptyList<SearchResult>(),
                            onContentChange = { content, cursor -> viewModel.updateActiveTabContent(content, cursor) },
                            onCompletionApply = viewModel::applyCompletion, onDismissCompletion = viewModel::dismissCompletions, onSave = viewModel::saveActiveFile)
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No file open", color = OnSurfaceDim) }
                }
            }
            if (!previewOnly && !codeOnly) VerticalDivider(color = Outline)
            // Preview
            AnimatedVisibility(visible = !codeOnly, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
                Box(modifier = Modifier.fillMaxHeight().weight(if (previewOnly) 1f else (1f - splitRatio))) {
                    PreviewPanel(activeTab = activeTab, modifier = Modifier.fillMaxSize(), onClose = onClose)
                }
            }
        }
    }
}
