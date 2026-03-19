package com.androidide.ui.designer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

// ── Component on the canvas ───────────────────────────────────────────────────
data class DesignComponent(
    val id: String,
    val type: DesignComponentType,
    var x: Float = 0f,           // position on canvas (dp)
    var y: Float = 0f,
    var width: Float = 120f,     // size in dp
    var height: Float = 48f,
    var props: ComponentProps = ComponentProps()
)

data class ComponentProps(
    val text: String        = "Label",
    val textSize: Int       = 14,
    val textColor: String   = "#E6EDF3",
    val backgroundColor: String = "#82AAFF",
    val cornerRadius: Int   = 8,
    val paddingH: Int       = 16,
    val paddingV: Int       = 8,
    val imageRes: String    = "",
    val hint: String        = "Enter text...",
    val isEnabled: Boolean  = true,
    val alpha: Float        = 1f,
    val fontWeight: String  = "Normal",   // Normal / Bold / SemiBold
    val arrangement: String = "Start",    // Start / Center / End / SpaceBetween
    val elevation: Int      = 0
)

enum class DesignComponentType(
    val displayName: String,
    val codeTag: String,
    val defaultWidth: Float,
    val defaultHeight: Float,
    val category: PaletteCategory
) {
    TEXT(          "Text",             "Text",              100f,  32f,  PaletteCategory.BASIC),
    BUTTON(        "Button",           "Button",            120f,  44f,  PaletteCategory.BASIC),
    OUTLINED_BUTTON("OutlinedButton", "OutlinedButton",    130f,  44f,  PaletteCategory.BASIC),
    TEXT_FIELD(    "TextField",        "OutlinedTextField", 180f,  56f,  PaletteCategory.BASIC),
    IMAGE(         "Image",            "Image",             100f, 100f,  PaletteCategory.BASIC),
    ICON(          "Icon",             "Icon",               36f,  36f,  PaletteCategory.BASIC),
    DIVIDER(       "Divider",          "HorizontalDivider", 180f,   2f,  PaletteCategory.BASIC),
    SWITCH(        "Switch",           "Switch",             52f,  32f,  PaletteCategory.BASIC),
    CHECKBOX(      "Checkbox",         "Checkbox",           24f,  24f,  PaletteCategory.BASIC),
    SLIDER(        "Slider",           "Slider",            180f,  40f,  PaletteCategory.BASIC),
    CARD(          "Card",             "Card",              160f, 100f,  PaletteCategory.CONTAINERS),
    SURFACE(       "Surface",          "Surface",           160f, 100f,  PaletteCategory.CONTAINERS),
    COLUMN(        "Column",           "Column",            140f, 120f,  PaletteCategory.CONTAINERS),
    ROW(           "Row",              "Row",               180f,  60f,  PaletteCategory.CONTAINERS),
    BOX(           "Box",              "Box",               140f, 100f,  PaletteCategory.CONTAINERS),
    SPACER(        "Spacer",           "Spacer",             60f,  16f,  PaletteCategory.CONTAINERS),
    PROGRESS_BAR(  "ProgressBar",      "LinearProgressIndicator", 180f, 8f, PaletteCategory.FEEDBACK),
    CIRCULAR_PROGRESS("CircularProgress","CircularProgressIndicator", 48f, 48f, PaletteCategory.FEEDBACK),
    BADGE(         "Badge",            "Badge",              24f,  24f,  PaletteCategory.FEEDBACK),
    CHIP(          "Chip",             "AssistChip",         90f,  32f,  PaletteCategory.FEEDBACK),
    FAB(           "FAB",              "FloatingActionButton", 56f, 56f, PaletteCategory.NAVIGATION),
    TOP_BAR(       "TopAppBar",        "TopAppBar",         280f,  56f,  PaletteCategory.NAVIGATION),
    BOTTOM_BAR(    "BottomNavBar",     "NavigationBar",     280f,  80f,  PaletteCategory.NAVIGATION),
}

enum class PaletteCategory(val label: String) {
    BASIC("Basic"),
    CONTAINERS("Containers"),
    FEEDBACK("Feedback"),
    NAVIGATION("Navigation")
}

// ── Code generation ────────────────────────────────────────────────────────────
object DesignCodeGenerator {
    fun toComposeCode(component: DesignComponent): String {
        val p = component.props
        return when (component.type) {
            DesignComponentType.TEXT -> """
Text(
    text = "${p.text}",
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp),
    style = MaterialTheme.typography.bodyMedium,
    color = Color(0xFF${p.textColor.removePrefix("#")})
)""".trimIndent()
            DesignComponentType.BUTTON -> """
Button(
    onClick = { /* TODO */ },
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp)
        .height(${component.height.toInt()}.dp),
    shape = RoundedCornerShape(${p.cornerRadius}.dp)
) {
    Text("${p.text}")
}""".trimIndent()
            DesignComponentType.OUTLINED_BUTTON -> """
OutlinedButton(
    onClick = { /* TODO */ },
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp)
        .height(${component.height.toInt()}.dp)
) {
    Text("${p.text}")
}""".trimIndent()
            DesignComponentType.TEXT_FIELD -> """
var ${p.text.lowercase().replace(" ", "")}Text by remember { mutableStateOf("") }
OutlinedTextField(
    value = ${p.text.lowercase().replace(" ", "")}Text,
    onValueChange = { ${p.text.lowercase().replace(" ", "")}Text = it },
    label = { Text("${p.hint}") },
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.IMAGE -> """
Image(
    painter = painterResource(id = R.drawable.ic_launcher),
    contentDescription = "${p.text}",
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp, ${component.height.toInt()}.dp)
        .clip(RoundedCornerShape(${p.cornerRadius}.dp)),
    contentScale = ContentScale.Crop
)""".trimIndent()
            DesignComponentType.CARD -> """
Card(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp, ${component.height.toInt()}.dp),
    shape = RoundedCornerShape(${p.cornerRadius}.dp),
    elevation = CardDefaults.cardElevation(${p.elevation}.dp)
) {
    // Card content
}""".trimIndent()
            DesignComponentType.COLUMN -> """
Column(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp, ${component.height.toInt()}.dp)
        .padding(${p.paddingH}.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.${p.arrangement}
) {
    // Children
}""".trimIndent()
            DesignComponentType.ROW -> """
Row(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .fillMaxWidth()
        .height(${component.height.toInt()}.dp)
        .padding(${p.paddingH}.dp),
    horizontalArrangement = Arrangement.${p.arrangement},
    verticalAlignment = Alignment.CenterVertically
) {
    // Children
}""".trimIndent()
            DesignComponentType.SPACER -> """
Spacer(modifier = Modifier.height(${component.height.toInt()}.dp))""".trimIndent()
            DesignComponentType.DIVIDER -> """
HorizontalDivider(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp),
    color = MaterialTheme.colorScheme.outline
)""".trimIndent()
            DesignComponentType.ICON -> """
Icon(
    imageVector = Icons.Default.Star,
    contentDescription = "${p.text}",
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp),
    tint = Color(0xFF${p.textColor.removePrefix("#")})
)""".trimIndent()
            DesignComponentType.SWITCH -> """
var ${component.id}Checked by remember { mutableStateOf(false) }
Switch(
    checked = ${component.id}Checked,
    onCheckedChange = { ${component.id}Checked = it },
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.CHECKBOX -> """
var ${component.id}Checked by remember { mutableStateOf(false) }
Checkbox(
    checked = ${component.id}Checked,
    onCheckedChange = { ${component.id}Checked = it },
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.SLIDER -> """
var ${component.id}Value by remember { mutableFloatStateOf(0.5f) }
Slider(
    value = ${component.id}Value,
    onValueChange = { ${component.id}Value = it },
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.CIRCULAR_PROGRESS -> """
CircularProgressIndicator(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp),
    color = MaterialTheme.colorScheme.primary
)""".trimIndent()
            DesignComponentType.PROGRESS_BAR -> """
LinearProgressIndicator(
    progress = { 0.7f },
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .width(${component.width.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.CHIP -> """
AssistChip(
    onClick = { /* TODO */ },
    label = { Text("${p.text}") },
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
)""".trimIndent()
            DesignComponentType.FAB -> """
FloatingActionButton(
    onClick = { /* TODO */ },
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp),
    containerColor = MaterialTheme.colorScheme.primary
) {
    Icon(Icons.Default.Add, contentDescription = "${p.text}")
}""".trimIndent()
            DesignComponentType.TOP_BAR -> """
TopAppBar(
    title = { Text("${p.text}") },
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp),
    navigationIcon = {
        IconButton(onClick = { /* TODO */ }) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
    }
)""".trimIndent()
            DesignComponentType.BOTTOM_BAR -> """
NavigationBar(
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
) {
    NavigationBarItem(selected = true,  onClick = {}, icon = { Icon(Icons.Default.Home, "Home") },   label = { Text("Home") })
    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Search, "Search") }, label = { Text("Search") })
    NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") })
}""".trimIndent()
            DesignComponentType.BOX -> """
Box(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp, ${component.height.toInt()}.dp)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(${p.cornerRadius}.dp)),
    contentAlignment = Alignment.Center
) {
    // Box content
}""".trimIndent()
            DesignComponentType.SURFACE -> """
Surface(
    modifier = Modifier
        .offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp)
        .size(${component.width.toInt()}.dp, ${component.height.toInt()}.dp),
    shape = RoundedCornerShape(${p.cornerRadius}.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = ${p.elevation}.dp
) {
    // Surface content
}""".trimIndent()
            DesignComponentType.BADGE -> """
Badge(
    modifier = Modifier.offset(x = ${component.x.toInt()}.dp, y = ${component.y.toInt()}.dp),
    containerColor = MaterialTheme.colorScheme.error
) {
    Text("${p.text}")
}""".trimIndent()
        }
    }

    fun generateFullScreen(components: List<DesignComponent>, screenName: String = "MyScreen"): String {
        val imports = buildString {
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.foundation.background")
            appendLine("import androidx.compose.foundation.shape.RoundedCornerShape")
            appendLine("import androidx.compose.material.icons.Icons")
            appendLine("import androidx.compose.material.icons.filled.*")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.runtime.*")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.draw.clip")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.layout.ContentScale")
            appendLine("import androidx.compose.ui.res.painterResource")
            appendLine("import androidx.compose.ui.tooling.preview.Preview")
            appendLine("import androidx.compose.ui.unit.dp")
        }
        val body = components.joinToString("\n\n") { "    ${toComposeCode(it).replace("\n", "\n    ")}" }
        return """
$imports
@Composable
fun ${screenName}Screen() {
    Box(modifier = Modifier.fillMaxSize()) {
$body
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
fun ${screenName}ScreenPreview() {
    MaterialTheme { ${screenName}Screen() }
}
""".trimIndent()
    }
}
