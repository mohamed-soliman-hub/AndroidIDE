package com.androidide.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.androidide.ui.theme.*
import com.androidide.viewmodels.IDEScreen
import com.androidide.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: MainViewModel) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("About AndroidIDE", color = OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setScreen(IDEScreen.SETTINGS) }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = OnSurfaceDim)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF0288D1))))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Code, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Text("AndroidIDE", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.7f)))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.15f)) {
                        Text("Professional Android IDE", style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp))
                    }
                }
            }

            // Features list
            Spacer(Modifier.height(8.dp))
            Text("Features", style = MaterialTheme.typography.titleMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.Start).padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))

            val features = listOf(
                Icons.Default.Edit           to "Advanced Code Editor with Syntax Highlighting",
                Icons.Default.AutoAwesome    to "AI Co-Pilot (OpenAI / Gemini / Claude)",
                Icons.Default.Build          to "Gradle Build System Integration",
                Icons.Default.AccountTree    to "Native Git Client",
                Icons.Default.Terminal       to "Linux Terminal Emulator",
                Icons.Default.Layers         to "Jetpack Compose Visual Designer",
                Icons.Default.Extension      to "7 Project Templates",
                Icons.Default.BugReport      to "Real-time Logcat Viewer",
                Icons.Default.Storage        to "Database Inspector",
                Icons.Default.Search         to "Code Search & Replace",
                Icons.Default.FindReplace    to "Refactoring Tools",
                Icons.Default.Widgets        to "Resource Manager",
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { (icon, desc) ->
                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceVariant, border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Text(desc, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Tech stack
            Text("Built With", style = MaterialTheme.typography.titleMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.Start).padding(horizontal = 20.dp))
            Spacer(Modifier.height(10.dp))

            val techStack = listOf("Kotlin 2.0", "Jetpack Compose", "Hilt DI", "JGit", "Coroutines", "Room DB", "Material 3")
            androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(techStack.size) { i ->
                    Surface(shape = RoundedCornerShape(20.dp), color = Primary.copy(alpha = 0.1f), border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f))) {
                        Text(techStack[i], style = MaterialTheme.typography.labelMedium.copy(color = Primary),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
            Text("© 2024 AndroidIDE. Open Source.", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
            Spacer(Modifier.height(40.dp))
        }
    }
}
