package com.androidide.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary Brand Colors ──────────────────────────────────────────────────────
val Primary        = Color(0xFF82AAFF)   // Soft electric blue
val PrimaryVariant = Color(0xFF5E7CE8)
val Secondary      = Color(0xFFC3E88D)   // Soft green accent
val Tertiary       = Color(0xFFFFCB6B)   // Amber accent

// ── IDE Surface Palette (Dark Theme) ─────────────────────────────────────────
val Background      = Color(0xFF0D1117)  // GitHub-style dark
val Surface         = Color(0xFF161B22)  // Card / Panel surface
val SurfaceVariant  = Color(0xFF1C2128)  // Slightly lighter panel
val SurfaceElevated = Color(0xFF21262D)  // Elevated cards
val Outline         = Color(0xFF30363D)  // Borders / dividers
val OutlineVariant  = Color(0xFF21262D)

// ── Text Colors ───────────────────────────────────────────────────────────────
val OnBackground   = Color(0xFFE6EDF3)   // Primary text
val OnSurface      = Color(0xFFCDD9E5)   // Secondary text
val OnSurfaceDim   = Color(0xFF768390)   // Dimmed / placeholder text
val OnPrimary      = Color(0xFF0D1117)

// ── Syntax Highlighting Colors ────────────────────────────────────────────────
val SyntaxKeyword   = Color(0xFFFF7B72)  // Red – keywords
val SyntaxString    = Color(0xFFA5D6FF)  // Light blue – strings
val SyntaxComment   = Color(0xFF8B949E)  // Gray – comments
val SyntaxNumber    = Color(0xFFD2A8FF)  // Purple – numbers
val SyntaxFunction  = Color(0xFFD2A8FF)  // Purple – functions
val SyntaxClass     = Color(0xFFFFA657)  // Orange – class names
val SyntaxAnnotation= Color(0xFFFFCB6B)  // Amber – annotations
val SyntaxType      = Color(0xFF79C0FF)  // Blue – types
val SyntaxOperator  = Color(0xFFFF7B72)  // Red – operators
val SyntaxVariable  = Color(0xFFCDD9E5)  // Default text

// ── Build / Log Level Colors ──────────────────────────────────────────────────
val LogError   = Color(0xFFFF7B72)
val LogWarning = Color(0xFFFFCB6B)
val LogInfo    = Color(0xFF79C0FF)
val LogDebug   = Color(0xFF8B949E)
val LogVerbose = Color(0xFF6E7681)
val LogSuccess = Color(0xFF56D364)

// ── Git Status Colors ─────────────────────────────────────────────────────────
val GitAdded    = Color(0xFF56D364)
val GitModified = Color(0xFFFFCB6B)
val GitDeleted  = Color(0xFFFF7B72)
val GitUntracked= Color(0xFF8B949E)

// ── Editor Specific ───────────────────────────────────────────────────────────
val EditorBackground    = Color(0xFF0D1117)
val EditorLineNumber    = Color(0xFF3D444D)
val EditorCurrentLine   = Color(0xFF161B22)
val EditorSelection     = Color(0xFF264F78)
val EditorCursor        = Color(0xFF82AAFF)
val EditorGutter        = Color(0xFF0D1117)
val TabActive           = Color(0xFF161B22)
val TabInactive         = Color(0xFF0D1117)
val TabActiveIndicator  = Color(0xFF82AAFF)

// ── File Tree Icon Colors ─────────────────────────────────────────────────────
val IconKotlin  = Color(0xFF7F52FF)
val IconJava    = Color(0xFFE76F51)
val IconXml     = Color(0xFF4DB6AC)
val IconGradle  = Color(0xFF02A9F4)
val IconJson    = Color(0xFFFFD54F)
val IconFolder  = Color(0xFF79C0FF)
val IconImage   = Color(0xFFA5D6FF)

// ── Completion Item Type Colors ───────────────────────────────────────────────
val CompletionClass    = Color(0xFFFFA657)
val CompletionMethod   = Color(0xFFD2A8FF)
val CompletionProperty = Color(0xFF79C0FF)
val CompletionKeyword  = Color(0xFFFF7B72)
val CompletionSnippet  = Color(0xFFC3E88D)
