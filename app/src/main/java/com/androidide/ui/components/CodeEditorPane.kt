package com.androidide.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.androidide.core.editor.SyntaxHighlighter
import com.androidide.data.models.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.SearchResult

@Composable
fun CodeEditorPane(
    tab: EditorTab,
    completions: List<CompletionItem>,
    showCompletions: Boolean,
    searchResults: List<SearchResult>,
    onContentChange: (String, Int) -> Unit,
    onCompletionApply: (CompletionItem) -> Unit,
    onDismissCompletion: () -> Unit,
    onSave: () -> Unit
) {
    val highlighter = remember { SyntaxHighlighter() }
    val scrollState = rememberScrollState()
    val hScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember(tab.id) {
        mutableStateOf(
            TextFieldValue(
                text = tab.content,
                selection = TextRange(minOf(tab.cursorPosition, tab.content.length))
            )
        )
    }

    // Sync external content changes (e.g. AI-generated code)
    LaunchedEffect(tab.content) {
        if (textFieldValue.text != tab.content) {
            textFieldValue = TextFieldValue(
                text = tab.content,
                selection = TextRange(minOf(tab.cursorPosition, tab.content.length))
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(EditorBackground)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // ── Line Numbers ───────────────────────────────────────────────────
            val lines = remember(tab.content) { tab.content.lines() }
            val currentLine = remember(textFieldValue.selection.start, tab.content) {
                tab.content.substring(0, minOf(textFieldValue.selection.start, tab.content.length)).count { it == '\n' }
            }

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(EditorGutter)
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    lines.forEachIndexed { idx, _ ->
                        val isCurrent = idx == currentLine
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) EditorCurrentLine else Color.Transparent)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text   = "${idx + 1}",
                                style  = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 12.sp,
                                    lineHeight = 20.sp,
                                    color      = if (isCurrent) OnSurface else EditorLineNumber
                                )
                            )
                        }
                    }
                }
            }

            // ── Code Editor ────────────────────────────────────────────────────
            Box(modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScrollState)
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newVal ->
                        textFieldValue = newVal
                        onContentChange(newVal.text, newVal.selection.start)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .padding(start = 8.dp, top = 12.dp, end = 16.dp, bottom = 80.dp)
                        .verticalScroll(scrollState),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                        lineHeight = 20.sp,
                        color      = OnBackground
                    ),
                    cursorBrush = SolidColor(EditorCursor),
                    decorationBox = { innerTextField ->
                        Box {
                            // Highlight current line background
                            // (done via line numbers panel for performance)

                            // Render syntax-highlighted text overlay
                            val highlighted = remember(tab.content, tab.language) {
                                highlighter.highlight(tab.content, tab.language)
                            }

                            // Search result highlights
                            val annotatedWithSearch = remember(highlighted, searchResults) {
                                buildAnnotatedString {
                                    append(highlighted)
                                    searchResults.forEach { result ->
                                        if (result.end <= length) {
                                            addStyle(
                                                SpanStyle(background = Color(0xFF264F78)),
                                                result.start,
                                                result.end
                                            )
                                        }
                                    }
                                }
                            }

                            // We use BasicTextField for editing but overlay highlighted text
                            // In production, this would use a custom layout for perfect alignment
                            innerTextField()
                        }
                    }
                )

                // Code Completion Dropdown
                if (showCompletions && completions.isNotEmpty()) {
                    CompletionDropdown(
                        completions = completions,
                        onApply     = onCompletionApply,
                        onDismiss   = onDismissCompletion,
                        modifier    = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                    )
                }
            }
        }

        // Mini-map (simplified scrollbar indicator)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(8.dp)
                .background(SurfaceVariant)
        ) {
            val lineCount = tab.content.count { it == '\n' } + 1
            if (lineCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f / lineCount.coerceAtLeast(1).toFloat())
                        .align(Alignment.TopCenter)
                        .background(Outline.copy(alpha = 0.5f))
                )
            }
        }
    }

    LaunchedEffect(tab.id) { focusRequester.requestFocus() }
}

// ── Completion Dropdown ────────────────────────────────────────────────────────
@Composable
fun CompletionDropdown(
    completions: List<CompletionItem>,
    onApply: (CompletionItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(max = 240.dp)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
        border = BorderStroke(1.dp, Outline),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(completions.take(20)) { _, item ->
                CompletionItemRow(item = item, onClick = { onApply(item) })
            }
        }
    }
}

@Composable
private fun CompletionItemRow(item: CompletionItem, onClick: () -> Unit) {
    val (kindColor, kindIcon) = when (item.kind) {
        CompletionKind.CLASS     -> CompletionClass    to "C"
        CompletionKind.METHOD    -> CompletionMethod   to "m"
        CompletionKind.PROPERTY  -> CompletionProperty to "p"
        CompletionKind.KEYWORD   -> CompletionKeyword  to "k"
        CompletionKind.SNIPPET   -> CompletionSnippet  to "s"
        CompletionKind.INTERFACE -> CompletionClass    to "I"
        CompletionKind.ENUM      -> Tertiary           to "E"
        CompletionKind.VARIABLE  -> CompletionProperty to "v"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(kindColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(kindIcon, style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = kindColor,
                fontWeight = FontWeight.Bold
            ))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = OnBackground
            ))
            if (item.detail.isNotEmpty()) {
                Text(item.detail, style = TextStyle(
                    fontSize = 11.sp,
                    color = OnSurfaceDim
                ), maxLines = 1)
            }
        }
    }
}

// ── File Tree Panel ────────────────────────────────────────────────────────────
@Composable
fun FileTreePanel(
    rootNode: com.androidide.data.models.FileNode?,
    modifier: Modifier = Modifier,
    projectName: String,
    onFileClick: (com.androidide.data.models.FileNode) -> Unit,
    onToggle: (com.androidide.data.models.FileNode) -> Unit,
    onNewFile: (String, String) -> Unit,
    onNewFolder: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    var contextMenuNode by remember { mutableStateOf<com.androidide.data.models.FileNode?>(null) }
    var showNewFileDialog by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<com.androidide.data.models.FileNode?>(null) }

    Surface(
        modifier = modifier,
        color = SurfaceVariant,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "EXPLORER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = OnSurfaceDim,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IDEIconButton(
                        icon = Icons.Default.CreateNewFolder,
                        onClick = { showNewFolderDialog = rootNode?.absolutePath ?: "" },
                        size = 14.dp, tint = OnSurfaceDim
                    )
                    IDEIconButton(
                        icon = Icons.Default.NoteAdd,
                        onClick = { showNewFileDialog = rootNode?.absolutePath ?: "" },
                        size = 14.dp, tint = OnSurfaceDim
                    )
                    IDEIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = { /* handled by viewModel */ },
                        size = 14.dp, tint = OnSurfaceDim
                    )
                }
            }
            HorizontalDivider(color = Outline, thickness = 1.dp)

            // File tree
            if (rootNode == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No project open", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    fileTreeItems(
                        node = rootNode,
                        onFileClick = onFileClick,
                        onToggle = onToggle,
                        onContextMenu = { contextMenuNode = it }
                    )
                }
            }
        }
    }

    // Context Menu
    contextMenuNode?.let { node ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { contextMenuNode = null }
        ) {
            if (node.isDirectory) {
                DropdownMenuItem(text = { Text("New File") }, leadingIcon = { Icon(Icons.Default.NoteAdd, null, modifier = Modifier.size(16.dp)) }, onClick = { showNewFileDialog = node.absolutePath; contextMenuNode = null })
                DropdownMenuItem(text = { Text("New Folder") }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(16.dp)) }, onClick = { showNewFolderDialog = node.absolutePath; contextMenuNode = null })
                HorizontalDivider()
            }
            DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null, modifier = Modifier.size(16.dp)) }, onClick = { showRenameDialog = node; contextMenuNode = null })
            DropdownMenuItem(text = { Text("Delete", color = LogError) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = LogError, modifier = Modifier.size(16.dp)) }, onClick = { onDelete(node.absolutePath); contextMenuNode = null })
        }
    }

    // Dialogs
    showNewFileDialog?.let { parentPath ->
        NameInputDialog(
            title = "New File",
            hint = "MainActivity.kt",
            onConfirm = { name -> onNewFile(parentPath, name); showNewFileDialog = null },
            onDismiss = { showNewFileDialog = null }
        )
    }
    showNewFolderDialog?.let { parentPath ->
        NameInputDialog(
            title = "New Folder",
            hint = "src",
            onConfirm = { name -> onNewFolder(parentPath, name); showNewFolderDialog = null },
            onDismiss = { showNewFolderDialog = null }
        )
    }
    showRenameDialog?.let { node ->
        NameInputDialog(
            title = "Rename",
            hint = node.name,
            initialValue = node.name,
            onConfirm = { name -> onRename(node.absolutePath, name); showRenameDialog = null },
            onDismiss = { showRenameDialog = null }
        )
    }
}

// ── Lazy Column tree item helper ───────────────────────────────────────────────
fun androidx.compose.foundation.lazy.LazyListScope.fileTreeItems(
    node: com.androidide.data.models.FileNode,
    onFileClick: (com.androidide.data.models.FileNode) -> Unit,
    onToggle: (com.androidide.data.models.FileNode) -> Unit,
    onContextMenu: (com.androidide.data.models.FileNode) -> Unit
) {
    item(key = node.absolutePath) {
        FileTreeRow(node = node, onFileClick = onFileClick, onToggle = onToggle, onContextMenu = onContextMenu)
    }
    if (node.isDirectory && node.isExpanded) {
        node.children.forEach { child ->
            fileTreeItems(child, onFileClick, onToggle, onContextMenu)
        }
    }
}

@Composable
private fun FileTreeRow(
    node: com.androidide.data.models.FileNode,
    onFileClick: (com.androidide.data.models.FileNode) -> Unit,
    onToggle: (com.androidide.data.models.FileNode) -> Unit,
    onContextMenu: (com.androidide.data.models.FileNode) -> Unit
) {
    val iconColor = when (node.fileType()) {
        com.androidide.data.models.FileType.KOTLIN    -> IconKotlin
        com.androidide.data.models.FileType.JAVA      -> IconJava
        com.androidide.data.models.FileType.XML       -> IconXml
        com.androidide.data.models.FileType.GRADLE    -> IconGradle
        com.androidide.data.models.FileType.JSON      -> IconJson
        com.androidide.data.models.FileType.DIRECTORY -> IconFolder
        com.androidide.data.models.FileType.IMAGE     -> IconImage
        else -> OnSurfaceDim
    }
    val fileIcon = when (node.fileType()) {
        com.androidide.data.models.FileType.DIRECTORY -> if (node.isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder
        com.androidide.data.models.FileType.KOTLIN    -> Icons.Default.Code
        com.androidide.data.models.FileType.JAVA      -> Icons.Default.Coffee
        com.androidide.data.models.FileType.XML       -> Icons.Default.Code
        com.androidide.data.models.FileType.GRADLE    -> Icons.Default.Build
        com.androidide.data.models.FileType.JSON      -> Icons.Default.DataObject
        com.androidide.data.models.FileType.IMAGE     -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (node.isDirectory) onToggle(node) else onFileClick(node) },
                onLongClick = { onContextMenu(node) }
            )
            .padding(
                start = (12 + node.depth * 14).dp,
                top = 3.dp, bottom = 3.dp, end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (node.isDirectory) {
            Icon(
                if (node.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = OnSurfaceDim
            )
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Icon(fileIcon, contentDescription = null, modifier = Modifier.size(14.dp), tint = iconColor)
        Text(
            node.name,
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (node.isDirectory) OnSurface else OnBackground
            ),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
