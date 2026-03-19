# AndroidIDE — Professional Android IDE for Android

A full-featured Android IDE that runs **natively on Android devices**, built with Kotlin and Jetpack Compose Material 3.

---

## ✦ Features

### Core Editor
- **Multi-Tab Code Editor** with syntax highlighting for Kotlin, Java, XML, Gradle, JSON, ProGuard
- **IntelliSense Engine** — context-aware completions for Compose, Android APIs, Modifier chains, dot-completions
- **Search & Replace** across files with match count
- **File Tree Manager** with nested directories, context menu (create/rename/delete)
- **Auto-save** and manual save

### Build System
- **Gradle Integration** — build, clean, sync with real-time stdout/stderr logs
- **Dependency Manager** — search Maven Central/Google Maven, one-click `implementation(...)` injection
- **Build Logs Panel** — colored log stream with ERROR/WARNING/SUCCESS indicators

### AI Co-Pilot
- **OpenAI / Gemini / Claude** compatible API
- **Generate Code** from natural language prompts
- **Fix Errors** — sends file + error log to AI
- **Code Review** and refactoring suggestions
- **Chat** with full project context awareness
- **File Generation** — AI creates files and folders via JSON command protocol

### Git Integration (JGit)
- Stage, unstage files and commit
- Push / Pull with credential support
- Branch creation, checkout, deletion
- Commit history viewer
- Real-time status indicators in file tree

### Project Templates (7 templates)
1. Jetpack Compose Activity
2. Empty Activity (XML)
3. AppCompat Activity
4. Navigation Drawer
5. Bottom Tabs
6. ViewModel + LiveData / StateFlow
7. Service + BroadcastReceiver

### Terminal Emulator
- Full Linux shell via `/system/bin/sh`
- Command history, `ls`, `pwd`, `cat`, and arbitrary shell commands
- Color-coded output (system/input/output/error)

### Logcat Viewer
- Real-time log stream from `adb logcat`
- Filter by level (V/D/I/W/E/F), tag, and search text
- Color-coded by log level

### Other Features
- **Import** existing Gradle projects from local storage
- **Splash Screen** with custom branding
- **Dark theme** with professional IDE color palette (GitHub-inspired)
- **Settings** for AI provider, API key, model, font size, tab width

---

## 🔧 Setup & Build

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+
- Android device / emulator API 26+

### Build Steps
```bash
git clone <repo-url>
cd AndroidIDE
./gradlew assembleDebug
```

Install on device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Configure AI
1. Open the app → Settings (⚙)
2. Select AI Provider (OpenAI / Gemini / Claude / Custom)
3. Enter your API Key
4. Select model (default: `gpt-4o`)

---

## 🏗 Architecture

```
com.androidide/
├── core/
│   ├── ai/          AIService.kt, AIManager.kt
│   ├── build/       GradleManager.kt
│   ├── editor/      CodeCompletionEngine.kt, SyntaxHighlighter.kt
│   ├── git/         GitManager.kt (JGit wrapper)
│   └── project/     ProjectManager.kt, TemplateManager.kt
├── data/
│   └── models/      Models.kt (all data classes + enums)
├── di/              AppModule.kt (Hilt)
├── ui/
│   ├── components/  CodeEditorPane, FileTreePanel, BottomPanel,
│   │                AIPanel, GitPanel, Dialogs, SharedComponents
│   ├── screens/     HomeScreen, EditorScreen, GitScreen,
│   │                SettingsScreen, TerminalScreen, AboutScreen
│   └── theme/       Color.kt, Type.kt, Theme.kt
└── viewmodels/      MainViewModel.kt
```

---

## 📦 Dependencies

| Library | Purpose |
|---|---|
| Jetpack Compose BOM 2024.09 | UI framework |
| Material 3 | Design system |
| Hilt 2.52 | Dependency injection |
| JGit 6.7 | Git operations |
| Retrofit 2.11 + OkHttp 4.12 | AI API calls |
| Gson 2.11 | JSON parsing |
| Coil 2.7 | Image loading |
| Room 2.6 | Local database |
| Accompanist Permissions | Runtime permissions |
| Sora Editor 0.23 | Enhanced code editor |
| Coroutines 1.8 | Async operations |

---

## 🎨 Design System

The IDE uses a GitHub-inspired dark color palette:
- **Background**: `#0D1117`
- **Surface**: `#161B22`
- **Primary (Electric Blue)**: `#82AAFF`
- **Secondary (Soft Green)**: `#C3E88D`
- **Tertiary (Amber)**: `#FFCB6B`
- Syntax colors tuned for long coding sessions

---

## 📄 License
MIT License — free to use, modify, and distribute.
