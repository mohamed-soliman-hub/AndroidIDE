package com.androidide.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import com.androidide.ui.components.IDEIconButton
import com.androidide.ui.theme.*
import com.androidide.viewmodels.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

// ── Terminal Screen ────────────────────────────────────────────────────────────
@Composable
fun TerminalScreen(viewModel: MainViewModel) {
    val output = remember { mutableStateListOf<TerminalLine>() }
    var input  by remember { mutableStateOf("") }
    var cwd    by remember { mutableStateOf(viewModel.projectManager.currentProject.value?.path ?: "/") }
    val scope  = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        output.add(TerminalLine("AndroidIDE Terminal v1.0", LineType.SYSTEM))
        output.add(TerminalLine("Type commands below. Use 'help' for available commands.", LineType.SYSTEM))
        output.add(TerminalLine("cwd: $cwd", LineType.SYSTEM))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TopBar
            Surface(color = Surface) {
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IDEIconButton(icon = Icons.Default.ArrowBack,
                        onClick = { viewModel.setScreen(com.androidide.viewmodels.IDEScreen.EDITOR) })
                    Icon(Icons.Default.Terminal, null, tint = LogSuccess, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Terminal", style = MaterialTheme.typography.titleSmall.copy(color = OnBackground))
                    Spacer(modifier = Modifier.weight(1f))
                    IDEIconButton(icon = Icons.Default.Delete, onClick = { output.clear() }, tint = OnSurfaceDim)
                }
                HorizontalDivider(color = Outline)
            }

            // Output
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(output) { line ->
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = when (line.type) {
                                LineType.SYSTEM  -> LogInfo
                                LineType.INPUT   -> LogSuccess
                                LineType.OUTPUT  -> OnBackground
                                LineType.ERROR   -> LogError
                            }
                        )
                    )
                }
            }

            LaunchedEffect(output.size) {
                if (output.isNotEmpty()) listState.animateScrollToItem(output.size - 1)
            }

            // Input
            HorizontalDivider(color = Outline)
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0D1117)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("$ ", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = LogSuccess))
                BasicTerminalInput(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    onSubmit = {
                        if (input.isNotBlank()) {
                            val cmd = input.trim()
                            output.add(TerminalLine("$ $cmd", LineType.INPUT))
                            input = ""
                            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                val result = executeCommand(cmd, cwd)
                                withContext(Dispatchers.Main) {
                                    result.lines().forEach { line ->
                                        output.add(TerminalLine(line, LineType.OUTPUT))
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BasicTerminalInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = OnBackground),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
            cursorColor = LogSuccess
        )
    )
}

private fun executeCommand(command: String, cwd: String): String {
    return try {
        when (command.trim().lowercase()) {
            "help" -> "Available: ls, pwd, cat <file>, echo <text>, clear, date, uname"
            "clear" -> ""
            "pwd"   -> cwd
            "date"  -> java.util.Date().toString()
            "uname" -> "Linux (Android ${android.os.Build.VERSION.RELEASE})"
            else -> {
                val process = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(java.io.File(cwd))
                    .redirectErrorStream(true)
                    .start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val out = reader.readText()
                process.waitFor()
                out.ifBlank { "(no output)" }
            }
        }
    } catch (e: Exception) { "Error: ${e.message}" }
}

data class TerminalLine(val text: String, val type: LineType)
enum class LineType { SYSTEM, INPUT, OUTPUT, ERROR }
