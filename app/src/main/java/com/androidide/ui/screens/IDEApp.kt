package com.androidide.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.androidide.viewmodels.IDEScreen
import com.androidide.viewmodels.MainViewModel

@Composable
fun IDEApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    AnimatedContent(
        targetState = uiState.currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith
            fadeOut(animationSpec = tween(200))
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            IDEScreen.HOME     -> HomeScreen(viewModel = viewModel)
            IDEScreen.EDITOR   -> EditorScreen(viewModel = viewModel)
            IDEScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
            IDEScreen.TERMINAL -> TerminalScreen(viewModel = viewModel)
            IDEScreen.GIT      -> GitScreen(viewModel = viewModel)
            IDEScreen.ABOUT    -> AboutScreen(viewModel = viewModel)
            IDEScreen.DESIGNER -> com.androidide.ui.designer.VisualDesignerScreen(
                onClose = { viewModel.setScreen(IDEScreen.EDITOR) },
                onCodeChanged = { code -> viewModel.onDesignerCodeChanged(code) }
            )
            IDEScreen.DEPENDENCIES -> DependencyManagerScreen(
                viewModel = viewModel,
                onClose   = { viewModel.setScreen(IDEScreen.EDITOR) }
            )
            IDEScreen.CLOUD_BUILD -> CloudBuildScreen(
                viewModel = viewModel,
                onClose   = { viewModel.setScreen(IDEScreen.EDITOR) }
            )
            IDEScreen.BUILD -> BuildScreen(
                viewModel = viewModel,
                onClose   = { viewModel.setScreen(IDEScreen.EDITOR) }
            )
            IDEScreen.PREVIEW_SPLIT -> {
                val activeTab by viewModel.activeTab.collectAsState()
                com.androidide.ui.preview.SplitPreviewScreen(
                    viewModel = viewModel,
                    activeTab = activeTab,
                    onClose   = { viewModel.setScreen(IDEScreen.EDITOR) }
                )
            }
        }
    }
}
