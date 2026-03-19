package com.androidide.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.androidide.data.models.EditorLanguage
import com.androidide.ui.theme.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyntaxHighlighter @Inject constructor() {

    fun highlight(code: String, language: EditorLanguage): AnnotatedString = when (language) {
        EditorLanguage.KOTLIN -> highlightKotlin(code)
        EditorLanguage.JAVA   -> highlightJava(code)
        EditorLanguage.XML    -> highlightXml(code)
        EditorLanguage.GRADLE -> highlightGradle(code)
        EditorLanguage.JSON   -> highlightJson(code)
        EditorLanguage.PROGUARD -> highlightProguard(code)
        else -> AnnotatedString(code)
    }

    // ── Kotlin Highlighting ────────────────────────────────────────────────────
    private fun highlightKotlin(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            // Multi-line strings
            Regex("\"\"\"[\\s\\S]*?\"\"\"") to SyntaxString,
            // Single-line strings
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SyntaxString,
            // Char literals
            Regex("'(?:[^'\\\\]|\\\\.)'") to SyntaxString,
            // Block comments
            Regex("/\\*[\\s\\S]*?\\*/") to SyntaxComment,
            // Line comments
            Regex("//[^\n]*") to SyntaxComment,
            // Annotations
            Regex("@[A-Z][A-Za-z0-9]*") to SyntaxAnnotation,
            // Numbers
            Regex("\\b(\\d+\\.\\d+[fFdDlL]?|0x[0-9A-Fa-f]+|\\d+[lL]?)\\b") to SyntaxNumber,
            // Keywords
            Regex("\\b(abstract|actual|annotation|as|break|by|catch|class|companion|const|constructor|continue|crossinline|data|delegate|do|dynamic|else|enum|expect|external|false|field|file|final|finally|for|fun|get|if|import|in|infix|init|inline|inner|interface|internal|is|it|lateinit|noinline|null|object|open|operator|out|override|package|private|protected|public|reified|return|sealed|set|super|suspend|tailrec|this|throw|true|try|typealias|val|var|vararg|when|where|while)\\b") to SyntaxKeyword,
            // Built-in types
            Regex("\\b(Any|Boolean|Byte|Char|Double|Float|Int|Long|Nothing|Number|Short|String|Unit|Array|List|Map|MutableList|MutableMap|Set|MutableSet|Pair|Triple)\\b") to SyntaxType,
            // Class names (capitalized)
            Regex("\\b([A-Z][A-Za-z0-9]*)\\b") to SyntaxClass,
            // Function calls
            Regex("\\b([a-z][a-zA-Z0-9]*)(?=\\s*\\()") to SyntaxFunction,
        )
        applyRules(rules)
    }

    // ── Java Highlighting ──────────────────────────────────────────────────────
    private fun highlightJava(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SyntaxString,
            Regex("'(?:[^'\\\\]|\\\\.)'") to SyntaxString,
            Regex("/\\*[\\s\\S]*?\\*/") to SyntaxComment,
            Regex("//[^\n]*") to SyntaxComment,
            Regex("@[A-Z][A-Za-z0-9]*") to SyntaxAnnotation,
            Regex("\\b(\\d+\\.\\d+[fFdDlL]?|0x[0-9A-Fa-f]+|\\d+[lLfFdD]?)\\b") to SyntaxNumber,
            Regex("\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|implements|import|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|true|try|var|void|volatile|while)\\b") to SyntaxKeyword,
            Regex("\\b(Boolean|Byte|Character|Double|Float|Integer|Long|Object|Short|String|Void)\\b") to SyntaxType,
            Regex("\\b([A-Z][A-Za-z0-9]*)\\b") to SyntaxClass,
            Regex("\\b([a-z][a-zA-Z0-9]*)(?=\\s*\\()") to SyntaxFunction,
        )
        applyRules(rules)
    }

    // ── XML Highlighting ───────────────────────────────────────────────────────
    private fun highlightXml(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            Regex("<!--[\\s\\S]*?-->") to SyntaxComment,
            Regex("\"[^\"]*\"") to SyntaxString,
            Regex("'[^']*'") to SyntaxString,
            Regex("&[a-zA-Z]+;|&#\\d+;") to SyntaxNumber,
            Regex("</?[A-Za-z][A-Za-z0-9.:-]*") to SyntaxKeyword,
            Regex("[A-Za-z]+:[A-Za-z]+(?=\\s*=)") to SyntaxAnnotation,
            Regex("[A-Za-z]+(?=\\s*=)") to SyntaxType,
            Regex("@[a-zA-Z+/]+/[a-zA-Z_]+") to SyntaxClass,
            Regex("@style/[a-zA-Z_.]+") to SyntaxClass,
            Regex("[/>]") to SyntaxOperator,
        )
        applyRules(rules)
    }

    // ── Gradle Highlighting ────────────────────────────────────────────────────
    private fun highlightGradle(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            Regex("/\\*[\\s\\S]*?\\*/") to SyntaxComment,
            Regex("//[^\n]*") to SyntaxComment,
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"") to SyntaxString,
            Regex("'(?:[^'\\\\]|\\\\.)*'") to SyntaxString,
            Regex("\\b(\\d+)\\b") to SyntaxNumber,
            Regex("\\b(implementation|debugImplementation|testImplementation|androidTestImplementation|ksp|kapt|api|compileOnly|runtimeOnly|plugins|android|dependencies|apply|classpath|buildscript|repositories|google|mavenCentral|jcenter|allprojects|subprojects|ext|task|def|true|false|null)\\b") to SyntaxKeyword,
            Regex("\\b(compileSdk|minSdk|targetSdk|versionCode|versionName|applicationId|buildFeatures|compose|composeOptions|kotlinOptions|jvmTarget)\\b") to SyntaxAnnotation,
        )
        applyRules(rules)
    }

    // ── JSON Highlighting ──────────────────────────────────────────────────────
    private fun highlightJson(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            Regex("\"(?:[^\"\\\\]|\\\\.)*\"\\s*:") to SyntaxAnnotation, // keys
            Regex(":\\s*\"(?:[^\"\\\\]|\\\\.)*\"") to SyntaxString,     // string values
            Regex("\\b(true|false|null)\\b") to SyntaxKeyword,
            Regex("\\b-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b") to SyntaxNumber,
            Regex("[{}\\[\\]]") to SyntaxOperator,
        )
        applyRules(rules)
    }

    // ── ProGuard Highlighting ──────────────────────────────────────────────────
    private fun highlightProguard(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        val rules = listOf(
            Regex("#[^\n]*") to SyntaxComment,
            Regex("-[a-zA-Z]+") to SyntaxKeyword,
            Regex("\\b[a-zA-Z.]+\\*?\\b") to SyntaxClass,
        )
        applyRules(rules)
    }

    // ── Helper: apply regex rules to AnnotatedString.Builder ──────────────────
    private fun AnnotatedString.Builder.applyRules(rules: List<Pair<Regex, Color>>) {
        val text = toString()
        rules.forEach { (regex, color) ->
            regex.findAll(text).forEach { match ->
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
    }

    // ── Line-based error underline ─────────────────────────────────────────────
    fun addErrorUnderline(text: AnnotatedString, startOffset: Int, endOffset: Int): AnnotatedString =
        buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(color = LogError, fontStyle = FontStyle.Italic),
                startOffset, minOf(endOffset, text.length)
            )
        }
}
