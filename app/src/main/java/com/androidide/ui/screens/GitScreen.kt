package com.androidide.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.data.models.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.IDEScreen
import com.androidide.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(viewModel: MainViewModel) {
    val gitStatus  by viewModel.gitStatus.collectAsState()
    val commits    by viewModel.gitCommits.collectAsState()
    val branches   by viewModel.gitBranches.collectAsState()
    val gitLog     by viewModel.gitLog.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var commitMsg  by remember { mutableStateOf("") }
    var showNewBranch by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var showPushDialog by remember { mutableStateOf(false) }
    var pushUser by remember { mutableStateOf("") }
    var pushPass by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountTree, null, tint = GitModified, modifier = Modifier.size(20.dp))
                        Text("Git", color = OnBackground, fontWeight = FontWeight.SemiBold)
                        gitStatus?.let { status ->
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = GitModified.copy(alpha = 0.15f)) {
                                Text(status.branch,
                                    style = MaterialTheme.typography.labelSmall.copy(color = GitModified),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setScreen(IDEScreen.EDITOR) }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = OnSurfaceDim)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.gitPull() }) {
                        Icon(Icons.Default.Download, "Pull", tint = OnSurface)
                    }
                    IconButton(onClick = { showPushDialog = true }) {
                        Icon(Icons.Default.Upload, "Push", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab row
            val tabs = listOf("Changes", "History", "Branches", "Log")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Surface,
                contentColor = Primary,
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
            HorizontalDivider(color = Outline)

            when (selectedTab) {
                0 -> ChangesTab(gitStatus, commitMsg, { commitMsg = it },
                    onStageAll = viewModel::gitStageAll,
                    onCommit = { if (commitMsg.isNotBlank()) { viewModel.gitCommit(commitMsg); commitMsg = "" } })
                1 -> HistoryTab(commits)
                2 -> BranchesTab(branches, gitStatus?.branch ?: "main",
                    onCheckout = { viewModel.checkoutBranch(it) },
                    onNewBranch = { showNewBranch = true })
                3 -> LogTab(gitLog)
            }
        }
    }

    // New branch dialog
    if (showNewBranch) {
        AlertDialog(
            onDismissRequest = { showNewBranch = false },
            containerColor = Surface,
            title = { Text("New Branch", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text("Branch name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                        cursorColor = Primary, focusedLabelColor = Primary,
                        unfocusedLabelColor = OnSurfaceDim
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newBranchName.isNotBlank()) {
                        viewModel.createBranch(newBranchName)
                        showNewBranch = false; newBranchName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewBranch = false }) { Text("Cancel") } }
        )
    }

    // Push dialog
    if (showPushDialog) {
        AlertDialog(
            onDismissRequest = { showPushDialog = false },
            containerColor = Surface,
            title = { Text("Push to Remote", color = OnBackground, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IDETextField(value = pushUser, onValueChange = { pushUser = it }, label = "Username (optional)")
                    IDETextField(value = pushPass, onValueChange = { pushPass = it }, label = "Token / Password", isPassword = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.gitPush(pushUser, pushPass)
                    showPushDialog = false
                }) { Text("Push") }
            },
            dismissButton = { TextButton(onClick = { showPushDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ChangesTab(
    status: GitStatus?,
    commitMsg: String,
    onCommitMsgChange: (String) -> Unit,
    onStageAll: () -> Unit,
    onCommit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Commit box
        item {
            Card(shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                border = BorderStroke(1.dp, Outline)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Commit Message", style = MaterialTheme.typography.labelMedium.copy(color = OnSurfaceDim))
                    OutlinedTextField(
                        value = commitMsg,
                        onValueChange = onCommitMsgChange,
                        modifier = Modifier.fillMaxWidth().height(90.dp),
                        placeholder = { Text("Describe your changes...", color = OnSurfaceDim) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground, cursorColor = Primary,
                            unfocusedLabelColor = OnSurfaceDim
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onStageAll, modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Outline)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = OnSurface)
                            Spacer(Modifier.width(4.dp))
                            Text("Stage All", color = OnSurface, style = MaterialTheme.typography.labelMedium)
                        }
                        Button(onClick = onCommit, modifier = Modifier.weight(1f),
                            enabled = commitMsg.isNotBlank()) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Commit", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        status?.let { s ->
            // Staged
            if (s.staged.isNotEmpty()) {
                item { GitSectionHeader("Staged Changes (${s.staged.size})", GitAdded) }
                items(s.staged) { file -> GitFileRow(file, GitAdded, "M") }
            }
            // Modified
            if (s.modified.isNotEmpty()) {
                item { GitSectionHeader("Modified (${s.modified.size})", GitModified) }
                items(s.modified) { file -> GitFileRow(file, GitModified, "M") }
            }
            // Untracked
            if (s.untracked.isNotEmpty()) {
                item { GitSectionHeader("Untracked (${s.untracked.size})", GitUntracked) }
                items(s.untracked) { file -> GitFileRow(file, GitUntracked, "U") }
            }
            // Deleted
            if (s.deleted.isNotEmpty()) {
                item { GitSectionHeader("Deleted (${s.deleted.size})", GitDeleted) }
                items(s.deleted) { file -> GitFileRow(file, GitDeleted, "D") }
            }
            if (s.staged.isEmpty() && s.modified.isEmpty() && s.untracked.isEmpty() && s.deleted.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = GitAdded, modifier = Modifier.size(40.dp))
                            Text("Working tree clean", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(commits: List<GitCommit>) {
    if (commits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No commits yet", color = OnSurfaceDim)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items(commits) { commit ->
            Surface(color = Surface, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Text(commit.author.take(1).uppercase(), style = MaterialTheme.typography.labelMedium.copy(color = Primary, fontWeight = FontWeight.Bold))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(commit.message, style = MaterialTheme.typography.bodySmall.copy(color = OnBackground, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(commit.author, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                        }
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = SurfaceElevated) {
                        Text(commit.shortHash, style = MaterialTheme.typography.labelSmall.copy(color = Secondary, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            HorizontalDivider(color = Outline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun BranchesTab(branches: List<String>, currentBranch: String, onCheckout: (String) -> Unit, onNewBranch: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Button(onClick = onNewBranch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Branch")
            }
        }
        items(branches) { branch ->
            val isCurrent = branch == currentBranch
            Surface(
                onClick = { if (!isCurrent) onCheckout(branch) },
                shape = RoundedCornerShape(8.dp),
                color = if (isCurrent) Primary.copy(alpha = 0.1f) else SurfaceVariant,
                border = BorderStroke(1.dp, if (isCurrent) Primary.copy(alpha = 0.4f) else Outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(if (isCurrent) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        null, tint = if (isCurrent) Primary else OnSurfaceDim, modifier = Modifier.size(16.dp))
                    Text(branch, style = MaterialTheme.typography.bodySmall.copy(color = if (isCurrent) Primary else OnBackground, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal))
                    if (isCurrent) {
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(alpha = 0.2f)) {
                            Text("current", style = MaterialTheme.typography.labelSmall.copy(color = Primary), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTab(logs: List<String>) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(EditorBackground), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(logs) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp))
        }
        if (logs.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text("No git activity yet", color = OnSurfaceDim) } }
        }
    }
}

@Composable
private fun GitSectionHeader(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun GitFileRow(file: String, color: Color, badge: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(3.dp), color = color.copy(alpha = 0.15f)) {
            Text(badge, style = MaterialTheme.typography.labelSmall.copy(color = color, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Text(file, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IDETextField(value: String, onValueChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary, unfocusedBorderColor = Outline,
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
            cursorColor = Primary, focusedLabelColor = Primary, unfocusedLabelColor = OnSurfaceDim
        )
    )
}
