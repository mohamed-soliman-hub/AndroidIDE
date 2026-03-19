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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.androidide.data.models.GitStatus
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel

@Composable
fun GitPanel(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val gitStatus  by viewModel.gitStatus.collectAsState()
    val branches   by viewModel.gitBranches.collectAsState()
    var commitMsg  by remember { mutableStateOf("") }
    var showBranchDialog by remember { mutableStateOf(false) }
    var newBranchName   by remember { mutableStateOf("") }
    var showPushDialog  by remember { mutableStateOf(false) }

    Surface(modifier = modifier, color = SurfaceVariant, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountTree, null, tint = GitModified, modifier = Modifier.size(18.dp))
                    Text("Source Control", style = MaterialTheme.typography.titleSmall.copy(
                        color = OnBackground, fontWeight = FontWeight.SemiBold))
                }
                gitStatus?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CallSplit, null, tint = GitModified, modifier = Modifier.size(12.dp))
                        Text(it.branch, style = MaterialTheme.typography.labelSmall.copy(color = GitModified))
                    }
                }
            }
            HorizontalDivider(color = Outline)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick actions
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GitActionButton(modifier = Modifier.weight(1f),
                            icon = Icons.Default.CloudUpload, label = "Push",
                            color = Primary, onClick = { showPushDialog = true })
                        GitActionButton(modifier = Modifier.weight(1f),
                            icon = Icons.Default.CloudDownload, label = "Pull",
                            color = Secondary, onClick = viewModel::gitPull)
                        GitActionButton(modifier = Modifier.weight(1f),
                            icon = Icons.Default.AddCircle, label = "Branch",
                            color = Tertiary, onClick = { showBranchDialog = true })
                    }
                }

                // Status summary
                gitStatus?.let { status ->
                    if (status.staged.isNotEmpty() || status.modified.isNotEmpty() || status.untracked.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("CHANGES", style = MaterialTheme.typography.labelSmall.copy(
                                    color = OnSurfaceDim, letterSpacing = 1.sp))

                                // Stage All button
                                OutlinedButton(
                                    onClick = viewModel::gitStageAll,
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = BorderStroke(1.dp, Outline)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = OnSurface)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stage All", style = MaterialTheme.typography.labelSmall.copy(color = OnSurface))
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${status.modified.size + status.untracked.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                                }

                                status.staged.forEach { file ->
                                    GitFileRow(file, GitAdded, "A") { viewModel.gitStageFile(file) }
                                }
                                status.modified.forEach { file ->
                                    GitFileRow(file, GitModified, "M") { viewModel.gitStageFile(file) }
                                }
                                status.untracked.forEach { file ->
                                    GitFileRow(file, GitUntracked, "U") { viewModel.gitStageFile(file) }
                                }
                                status.deleted.forEach { file ->
                                    GitFileRow(file, GitDeleted, "D") { viewModel.gitStageFile(file) }
                                }
                            }
                        }
                    }

                    // Ahead/Behind indicator
                    if (status.ahead > 0 || status.behind > 0) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceElevated)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (status.ahead > 0) {
                                    Text("↑ ${status.ahead} to push",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Primary))
                                }
                                if (status.behind > 0) {
                                    Text("↓ ${status.behind} to pull",
                                        style = MaterialTheme.typography.labelSmall.copy(color = LogWarning))
                                }
                            }
                        }
                    }
                }

                // Branches list
                if (branches.isNotEmpty()) {
                    item {
                        Text("BRANCHES", style = MaterialTheme.typography.labelSmall.copy(
                            color = OnSurfaceDim, letterSpacing = 1.sp))
                    }
                    items(branches) { branch ->
                        val isCurrent = branch == gitStatus?.branch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCurrent) Primary.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { viewModel.checkoutBranch(branch) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CallSplit, null,
                                tint = if (isCurrent) Primary else OnSurfaceDim,
                                modifier = Modifier.size(12.dp))
                            Text(branch, style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isCurrent) Primary else OnBackground,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal))
                            if (isCurrent) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            // Commit section
            HorizontalDivider(color = Outline)
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = commitMsg,
                    onValueChange = { commitMsg = it },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    placeholder = { Text("Commit message...", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = OnBackground),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Outline)
                )
                Button(
                    onClick = { viewModel.gitCommit(commitMsg); commitMsg = "" },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    enabled = commitMsg.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Commit", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (showBranchDialog) {
        AlertDialog(
            onDismissRequest = { showBranchDialog = false },
            containerColor = Surface,
            title = { Text("New Branch", color = OnBackground) },
            text = {
                OutlinedTextField(
                    value = newBranchName,
                    onValueChange = { newBranchName = it },
                    label = { Text("Branch name", color = OnSurfaceDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = Outline,
                        focusedTextColor = OnBackground, unfocusedTextColor = OnBackground)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createBranch(newBranchName)
                    newBranchName = ""; showBranchDialog = false
                }, enabled = newBranchName.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showBranchDialog = false }) { Text("Cancel", color = OnSurfaceDim) } }
        )
    }
}

@Composable
private fun GitActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = color)
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = color))
    }
}

@Composable
private fun GitFileRow(
    file: String,
    color: androidx.compose.ui.graphics.Color,
    status: String,
    onStage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onStage)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center) {
            Text(status, style = MaterialTheme.typography.labelSmall.copy(color = color, fontSize = 9.sp))
        }
        Text(
            file.substringAfterLast('/'),
            style = MaterialTheme.typography.labelSmall.copy(color = color),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
