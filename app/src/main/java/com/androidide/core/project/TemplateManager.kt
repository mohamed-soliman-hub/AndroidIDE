package com.androidide.core.project

import com.androidide.data.models.Language
import com.androidide.data.models.Project
import com.androidide.data.models.ProjectType
import java.io.File

object TemplateManager {
    fun generateTemplate(project: Project, dir: File) {
        generateGradleFiles(project, dir)
        when (project.type) {
            ProjectType.EMPTY_ACTIVITY        -> generateEmptyActivity(project, dir)
            ProjectType.APPCOMPAT_ACTIVITY    -> generateAppCompatActivity(project, dir)
            ProjectType.COMPOSE_ACTIVITY      -> generateComposeActivity(project, dir)
            ProjectType.NAVIGATION_DRAWER     -> generateNavigationDrawer(project, dir)
            ProjectType.BOTTOM_TABS           -> generateBottomTabs(project, dir)
            ProjectType.VIEWMODEL_LIVEDATA    -> generateViewModelLiveData(project, dir)
            ProjectType.SERVICE_BROADCAST     -> generateServiceBroadcast(project, dir)
            ProjectType.IMPORTED              -> Unit
        }
    }

    private fun generateGradleFiles(project: Project, dir: File) {
        File(dir, "settings.gradle.kts").writeText("""
rootProject.name = "${project.name}"
include(":app")
        """.trimIndent())

        File(dir, "build.gradle.kts").writeText("""
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
        """.trimIndent())

        File(dir, "gradle.properties").writeText("""
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
        """.trimIndent())

        val appDir = File(dir, "app").also { it.mkdirs() }
        val isCompose = project.type == ProjectType.COMPOSE_ACTIVITY ||
                project.type == ProjectType.BOTTOM_TABS ||
                project.type == ProjectType.NAVIGATION_DRAWER

        File(appDir, "build.gradle.kts").writeText(buildString {
            appendLine("""
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    ${if (isCompose) """id("org.jetbrains.kotlin.plugin.compose")""" else ""}
}

android {
    namespace = "${project.packageName}"
    compileSdk = ${project.compileSdk}
    defaultConfig {
        applicationId = "${project.packageName}"
        minSdk = ${project.minSdk}
        targetSdk = ${project.targetSdk}
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    ${if (isCompose) "buildFeatures { compose = true }" else ""}
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.appcompat:appcompat:1.7.0")
            """.trimIndent())
            if (isCompose) {
                appendLine("""
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
                """.trimIndent())
            } else {
                appendLine("""    implementation("com.google.android.material:material:1.12.0")""")
            }
            appendLine("}")
        })

        val srcDir = File(appDir, "src/main/java/${project.packageName.replace('.', '/')}").also { it.mkdirs() }
        val resDir = File(appDir, "src/main/res").also { it.mkdirs() }
        listOf("layout", "values", "drawable", "mipmap-hdpi").forEach { File(resDir, it).mkdirs() }

        File(appDir, "src/main/AndroidManifest.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="${project.name}"
        android:theme="@style/Theme.${project.name.replace(" ", "")}">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
        """.trimIndent())

        File(File(resDir, "values"), "strings.xml").writeText("""
<resources>
    <string name="app_name">${project.name}</string>
</resources>
        """.trimIndent())

        File(File(resDir, "values"), "themes.xml").writeText("""
<resources>
    <style name="Theme.${project.name.replace(" ", "")}" parent="Theme.MaterialComponents.DayNight.DarkActionBar" />
</resources>
        """.trimIndent())

        File(appDir, "proguard-rules.pro").writeText("# Add project specific ProGuard rules here.")
    }

    private fun generateEmptyActivity(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
        """.trimIndent())

        val layoutDir = File(dir, "app/src/main/res/layout")
        File(layoutDir, "activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello World!"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent())
    }

    private fun generateAppCompatActivity(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}
        """.trimIndent())
    }

    private fun generateComposeActivity(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ${project.packageName}.ui.theme.${project.name.replace(" ", "")}Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ${project.name.replace(" ", "")}Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hello \$name!",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { /* TODO */ }) {
            Text("Get Started")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ${project.name.replace(" ", "")}Theme { Greeting("Android") }
}
        """.trimIndent())

        val themeDir = File(srcDir, "ui/theme").also { it.mkdirs() }
        File(themeDir, "Theme.kt").writeText("""
package ${project.packageName}.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary   = Purple80,
    secondary = PurpleGrey80,
    tertiary  = Pink80
)

@Composable
fun ${project.name.replace(" ", "")}Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
        """.trimIndent())
    }

    private fun generateNavigationDrawer(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NavigationDrawerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationDrawerApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val items = listOf("Home", "Profile", "Settings", "About")
    var selectedItem by remember { mutableStateOf(items[0]) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item) },
                        selected = selectedItem == item,
                        onClick = { selectedItem = item; scope.launch { drawerState.close() } }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedItem) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Text("Content: \$selectedItem")
        }
    }
}
        """.trimIndent())
    }

    private fun generateBottomTabs(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { BottomTabsApp() } }
    }
}

sealed class Screen(val route: String, val label: String) {
    object Home     : Screen("home",     "Home")
    object Search   : Screen("search",   "Search")
    object Profile  : Screen("profile",  "Profile")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun BottomTabsApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(Screen.Home, Screen.Search, Screen.Profile, Screen.Settings)
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        icon     = { Icon(Icons.Default.Home, screen.label) },
                        label    = { Text(screen.label) }
                    )
                }
            }
        }
    ) { Text("Content for: \${tabs[selectedTab].label}") }
}
        """.trimIndent())
    }

    private fun generateViewModelLiveData(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count
    fun increment() { _count.value++ }
    fun decrement() { _count.value-- }
    fun reset()     { _count.value = 0 }
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { CounterScreen(viewModel) } }
    }
}

@Composable
fun CounterScreen(vm: MainViewModel) {
    val count by vm.count.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Count: \$count", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = vm::decrement) { Text("-") }
            Button(onClick = vm::reset)     { Text("Reset") }
            Button(onClick = vm::increment) { Text("+") }
        }
    }
}
        """.trimIndent())
    }

    private fun generateServiceBroadcast(project: Project, dir: File) {
        val srcDir = File(dir, "app/src/main/java/${project.packageName.replace('.', '/')}")
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.content.*
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Service & Broadcast Demo") } }
    }
}
        """.trimIndent())

        File(srcDir, "MyService.kt").writeText("""
package ${project.packageName}

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MyService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MyService", "Service started")
        return START_STICKY
    }
    override fun onDestroy() { super.onDestroy(); Log.d("MyService", "Service destroyed") }
}
        """.trimIndent())

        File(srcDir, "MyReceiver.kt").writeText("""
package ${project.packageName}

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MyReceiver", "Broadcast received: \${intent.action}")
    }
}
        """.trimIndent())
    }
}
