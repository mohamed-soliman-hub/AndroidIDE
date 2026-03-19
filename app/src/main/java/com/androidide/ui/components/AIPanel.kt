package com.androidide.ui.components

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.androidide.data.models.AIMessage
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPanel(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val messages by viewModel.aiMessages.collectAsState()
    val isLoading by viewModel.aiLoading.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Surface(modifier = modifier, color = SurfaceVariant, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = Tertiary, modifier = Modifier.size(18.dp))
                    Text("AI Co-Pilot", style = MaterialTheme.typography.titleSmall.copy(
                        color = OnBackground, fontWeight = FontWeight.SemiBold))
                }
                IDEIconButton(icon = Icons.Default.Delete, onClick = viewModel::clearAIChat,
                    size = 14.dp, tint = OnSurfaceDim)
            }
            HorizontalDivider(color = Outline)

            // Quick Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickActions = listOf(
                    "Generate Code" to Icons.Default.Code,
                    "Fix Error"     to Icons.Default.BugReport,
                    "Explain"       to Icons.Default.Help,
                    "Refactor"      to Icons.Default.AutoFixHigh
                )
                quickActions.forEach { (label, icon) ->
                    FilterChip(
                        selected  = false,
                        onClick   = {
                            when (label) {
                                "Fix Error" -> viewModel.fixErrorAI()
                                else -> { inputText = "$label: "; }
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(icon, null, modifier = Modifier.size(12.dp)) },
                        modifier = Modifier.height(28.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SurfaceElevated,
                            labelColor = OnSurface,
                            iconColor = Tertiary
                        )
                    )
                }
            }

            HorizontalDivider(color = Outline)

            // Messages
            if (messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Tertiary.copy(alpha = 0.4f), modifier = Modifier.size(40.dp))
                        Text("How can I help?", style = MaterialTheme.typography.titleSmall.copy(color = OnSurface))
                        Text(
                            "Ask me to generate code, fix errors, explain concepts, or help with your Android project.",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        AIChatBubble(message = message)
                    }
                    if (isLoading) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start) {
                                Box(modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceElevated)
                                    .padding(12.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp),
                                            color = Tertiary, strokeWidth = 2.dp)
                                        Text("Thinking...", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Outline)

            // Input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Ask AI anything...",
                            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = OnBackground),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            viewModel.sendAIMessage(inputText.trim())
                            inputText = ""
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Tertiary.copy(alpha = 0.6f),
                        unfocusedBorderColor = Outline,
                        focusedTextColor = OnBackground,
                        unfocusedTextColor = OnBackground
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            viewModel.sendAIMessage(inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(40.dp),
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) Tertiary else OnSurfaceDim)
                }
            }
        }
    }
}

@Composable
private fun AIChatBubble(message: AIMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Tertiary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AutoAwesome, null, tint = Tertiary, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 12.dp
                ))
                .background(if (isUser) Primary.copy(alpha = 0.2f) else SurfaceElevated)
                .padding(10.dp)
        ) {
            // Detect code blocks
            if (message.content.contains("```")) {
                CodeFormattedMessage(message.content)
            } else {
                Text(message.content,
                    style = MaterialTheme.typography.bodySmall.copy(color = OnBackground))
            }
        }
        if (isUser) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Primary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun CodeFormattedMessage(content: String) {
    val parts = content.split(Regex("```(?:kotlin|java|xml|gradle|)?"))
    parts.forEachIndexed { index, part ->
        if (index % 2 == 0) {
            if (part.isNotBlank()) Text(part.trim(),
                style = MaterialTheme.typography.bodySmall.copy(color = OnBackground))
        } else {
            Box(modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(EditorBackground)
                .padding(8.dp)) {
                Text(part.trim(), style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = SyntaxString
                ))
            }
        }
    }
}
