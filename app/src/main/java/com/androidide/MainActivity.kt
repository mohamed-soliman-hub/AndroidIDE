package com.androidide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.androidide.ui.screens.IDEApp
import com.androidide.ui.theme.AndroidIDETheme
import com.androidide.ui.theme.Background
import com.androidide.viewmodels.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidIDETheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                    val viewModel: MainViewModel = hiltViewModel()
                    IDEApp(viewModel)
                }
            }
        }
    }
}
