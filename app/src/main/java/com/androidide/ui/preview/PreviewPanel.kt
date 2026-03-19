package com.androidide.ui.preview

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.data.models.EditorLanguage
import com.androidide.data.models.EditorTab
import com.androidide.ui.theme.*

enum class PreviewDevice(val label: String, val width: Int, val height: Int) {
    PHONE("Phone", 360, 740), TABLET("Tablet", 600, 960),
    FOLD("Fold", 344, 882),   WATCH("Watch", 192, 192)
}

// ── @Preview annotation metadata ─────────────────────────────────────────────
data class PreviewAnnotation(
    val functionName: String, val showBackground: Boolean = true,
    val widthDp: Int = 360, val heightDp: Int = 640,
    val device: PreviewDevice = PreviewDevice.PHONE, val name: String = ""
)

// ── Parser ────────────────────────────────────────────────────────────────────
object ComposePreviewParser {
    fun extractPreviews(source: String): List<PreviewAnnotation> {
        val previews = mutableListOf<PreviewAnnotation>()
        val lines = source.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("@Preview")) {
                // Collect annotation block
                val block = buildString {
                    var depth = 0; var j = i
                    while (j < lines.size && j < i + 8) {
                        val l = lines[j]; append(l)
                        depth += l.count { it == '(' } - l.count { it == ')' }
                        if (depth <= 0 && j > i) break; j++
                    }
                }
                val wDp = Regex("""widthDp\s*=\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 360
                val hDp = Regex("""heightDp\s*=\s*(\d+)""").find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 640
                val name = Regex("""name\s*=\s*[""]([^""]+)[""]""").find(block)?.groupValues?.get(1) ?: ""
                val device = when { block.contains("TABLET") -> PreviewDevice.TABLET; block.contains("WATCH") -> PreviewDevice.WATCH; else -> PreviewDevice.PHONE }
                var j = i + 1
                while (j < lines.size && j < i + 5) {
                    val funMatch = Regex("""fun\s+([A-Za-z][A-Za-z0-9]*)""").find(lines[j])
                    if (funMatch != null) {
                        previews.add(PreviewAnnotation(funMatch.groupValues[1], true, wDp, hDp, device, name.ifEmpty { funMatch.groupValues[1] }))
                        break
                    }
                    j++
                }
            }
            i++
        }
        return previews
    }

    fun detectComponents(funcName: String, source: String): List<String> {
        val funcRegex = Regex("""fun\s+${Regex.escape(funcName)}[^{]*\{""")
        val startIdx = funcRegex.find(source)?.range?.last ?: return emptyList()
        val body = source.substring(startIdx, minOf(source.length, startIdx + 2000))
        return listOf("Column","Row","Box","Text","Button","OutlinedButton","TextField","OutlinedTextField",
            "Image","Card","LazyColumn","LazyRow","TopAppBar","NavigationBar","FloatingActionButton",
            "CircularProgressIndicator","LinearProgressIndicator","Checkbox","Switch","Slider",
            "Surface","Scaffold","TabRow","AlertDialog","HorizontalDivider","Spacer","Icon","IconButton")
            .filter { body.contains("$it(") || body.contains("$it\n") }
    }
}

// ── XML Parser ────────────────────────────────────────────────────────────────
data class XmlViewNode(
    val tag: String, val attrs: Map<String, String> = emptyMap(),
    val children: MutableList<XmlViewNode> = mutableListOf()
) {
    val id      get() = attrs["android:id"]?.removePrefix("@+id/")?.removePrefix("@id/") ?: ""
    val text    get() = attrs["android:text"]?.removePrefix("@string/") ?: ""
    val hint    get() = attrs["android:hint"]?.removePrefix("@string/") ?: ""
    val width   get() = attrs["android:layout_width"] ?: "wrap_content"
    val height  get() = attrs["android:layout_height"] ?: "wrap_content"
    val orientation get() = attrs["android:orientation"] ?: "vertical"
    val visibility  get() = attrs["android:visibility"] ?: "visible"
    val isMatchParent get() = width == "match_parent" || width == "fill_parent"
    val simpleName get() = tag.substringAfterLast('.')
}

object XmlLayoutParser {
    fun parse(xml: String): XmlViewNode? = try {
        val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(xml.reader()) }
        val stack = ArrayDeque<XmlViewNode>()
        var root: XmlViewNode? = null
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    val attrs = (0 until parser.attributeCount).associate { i ->
                        val ns = parser.getAttributeNamespace(i)
                        val k = if (ns.isNotEmpty()) "${ns.substringAfterLast('/')}:${parser.getAttributeName(i)}" else parser.getAttributeName(i)
                        k to parser.getAttributeValue(i)
                    }
                    val node = XmlViewNode(parser.name, attrs)
                    if (stack.isNotEmpty()) stack.last().children.add(node) else root = node
                    stack.addLast(node)
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeLast()
            }
            event = parser.next()
        }
        root
    } catch (e: Exception) { null }
}

// ════════════════════════════════════════════════════════════════════════════
// Main Preview Panel
// ════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewPanel(
    activeTab: EditorTab?,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
    val code     = activeTab?.content ?: ""
    val language = activeTab?.language ?: EditorLanguage.KOTLIN
    var device   by remember { mutableStateOf(PreviewDevice.PHONE) }
    var zoom     by remember { mutableFloatStateOf(0.85f) }
    var darkMode by remember { mutableStateOf(true) }

    val previews = remember(code) {
        if (language == EditorLanguage.KOTLIN || language == EditorLanguage.JAVA)
            ComposePreviewParser.extractPreviews(code) else emptyList()
    }
    val xmlRoot = remember(code) { if (language == EditorLanguage.XML) XmlLayoutParser.parse(code) else null }

    Surface(modifier = modifier, color = Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Surface(color = Surface) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Preview, null, tint = Tertiary, modifier = Modifier.size(16.dp))
                        Text("Preview", style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
                        Text(activeTab?.file?.name ?: "", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (previews.isNotEmpty()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Primary.copy(alpha = 0.15f)) {
                                Text("${previews.size}",style = MaterialTheme.typography.labelSmall.copy(color = Primary),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    // Controls
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Device selector
                        PreviewDevice.values().forEach { d ->
                            val sel = device == d
                            Surface(onClick = { device = d }, shape = RoundedCornerShape(6.dp),
                                color = if (sel) Primary.copy(alpha = 0.2f) else SurfaceVariant,
                                border = BorderStroke(1.dp, if (sel) Primary else Outline),
                                modifier = Modifier.height(26.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Icon(deviceIcon(d), null, tint = if (sel) Primary else OnSurfaceDim, modifier = Modifier.size(12.dp))
                                    Text(d.label, style = MaterialTheme.typography.labelSmall.copy(color = if (sel) Primary else OnSurfaceDim, fontSize = 9.sp))
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { zoom = (zoom - 0.1f).coerceIn(0.4f, 2.0f) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ZoomOut, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = SurfaceVariant) {
                            Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(color = OnSurface, fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                        IconButton(onClick = { zoom = (zoom + 0.1f).coerceIn(0.4f, 2.0f) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.ZoomIn, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = { darkMode = !darkMode }, modifier = Modifier.size(26.dp)) {
                            Icon(if (darkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                null, tint = if (darkMode) Tertiary else OnSurfaceDim, modifier = Modifier.size(14.dp))
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Close, null, tint = OnSurfaceDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
            HorizontalDivider(color = Outline)

            // Content
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080B10))) {
                when {
                    xmlRoot != null -> XmlPreviewContent(xmlRoot, device, zoom, darkMode)
                    previews.isNotEmpty() -> ComposePreviewContent(code, previews, device, zoom, darkMode)
                    language == EditorLanguage.KOTLIN || language == EditorLanguage.JAVA ->
                        ComposePreviewContent(code, emptyList(), device, zoom, darkMode)
                    else -> EmptyPreview()
                }
            }
        }
    }
}

// ── Compose Preview ───────────────────────────────────────────────────────────
@Composable
private fun ComposePreviewContent(
    code: String, previews: List<PreviewAnnotation>,
    device: PreviewDevice, zoom: Float, darkMode: Boolean
) {
    val effectivePreviews = if (previews.isEmpty()) {
        val fn = Regex("""@Composable\s+fun\s+([A-Za-z][A-Za-z0-9]*)""").find(code)?.groupValues?.get(1)
        listOf(PreviewAnnotation(fn ?: "Preview", name = fn ?: "Screen"))
    } else previews

    LazyRow(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        items(effectivePreviews) { preview ->
            val components = ComposePreviewParser.detectComponents(preview.functionName, code)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preview.name.ifEmpty { preview.functionName },
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
                PhoneFrame(device = device, zoom = zoom, darkMode = darkMode) {
                    InferredComposeCanvas(components = components, code = code, darkMode = darkMode)
                }
            }
        }
    }
}

@Composable
private fun InferredComposeCanvas(components: List<String>, code: String, darkMode: Boolean) {
    val bg = if (darkMode) Color(0xFF0D1117) else Color(0xFFF5F5F5)
    val textColor = if (darkMode) OnBackground else Color(0xFF1C1B1F)
    val surfaceColor = if (darkMode) Surface else Color.White

    Column(modifier = Modifier.fillMaxSize().background(bg).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if ("TopAppBar" in components) {
            Row(modifier = Modifier.fillMaxWidth().height(40.dp).background(if (darkMode) Surface else Color(0xFF6200EE)),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White, modifier = Modifier.padding(8.dp).size(18.dp))
                Text("Title", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            }
        }
        if ("Text" in components || components.isEmpty()) {
            Text("Hello World", style = MaterialTheme.typography.bodyMedium.copy(color = textColor))
        }
        if ("OutlinedTextField" in components || "TextField" in components) {
            Box(Modifier.fillMaxWidth().height(48.dp).border(1.5.dp, Outline, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart) {
                Text("Enter text...", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
            }
        }
        if ("Button" in components || "OutlinedButton" in components || "FilledTonalButton" in components) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if ("Button" in components)
                    Box(Modifier.height(36.dp).width(100.dp).background(Primary, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Button", style = MaterialTheme.typography.labelMedium.copy(color = OnPrimary))
                    }
                if ("OutlinedButton" in components)
                    Box(Modifier.height(36.dp).width(100.dp).border(1.dp, Primary, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Outlined", style = MaterialTheme.typography.labelMedium.copy(color = Primary))
                    }
            }
        }
        if ("Card" in components) {
            Box(Modifier.fillMaxWidth().height(80.dp).shadow(2.dp, RoundedCornerShape(8.dp)).background(surfaceColor, RoundedCornerShape(8.dp)).padding(12.dp)) {
                Text("Card content", style = MaterialTheme.typography.bodySmall.copy(color = textColor))
            }
        }
        if ("LazyColumn" in components || "LazyRow" in components) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Row(Modifier.fillMaxWidth().background(surfaceColor, RoundedCornerShape(4.dp)).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(SurfaceVariant, CircleShape))
                        Column { Box(Modifier.width(100.dp).height(10.dp).background(OnSurfaceDim.copy(0.3f), RoundedCornerShape(4.dp)))
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(60.dp).height(8.dp).background(OnSurfaceDim.copy(0.15f), RoundedCornerShape(4.dp))) }
                    }
                }
            }
        }
        if ("CircularProgressIndicator" in components) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Primary, strokeWidth = 3.dp, progress = { 0.65f })
            }
        }
        if ("LinearProgressIndicator" in components) {
            LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.fillMaxWidth(), color = Primary, trackColor = SurfaceVariant)
        }
        if ("Switch" in components || "Checkbox" in components) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if ("Switch" in components) Box(Modifier.width(44.dp).height(24.dp).background(Primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.padding(2.dp).size(20.dp).background(Color.White, CircleShape))
                }
                if ("Checkbox" in components) Box(Modifier.size(20.dp).background(Primary, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text("Label", style = MaterialTheme.typography.bodySmall.copy(color = textColor))
            }
        }
        if ("FloatingActionButton" in components) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                Box(Modifier.size(48.dp).background(Primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = OnPrimary, modifier = Modifier.size(22.dp))
                }
            }
        }
        if ("NavigationBar" in components) {
            Row(Modifier.fillMaxWidth().height(56.dp).background(if (darkMode) Surface else Color.White),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Person).forEachIndexed { i, icon ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, tint = if (i == 0) Primary else OnSurfaceDim, modifier = Modifier.size(18.dp))
                        if (i == 0) Box(Modifier.size(4.dp).background(Primary, CircleShape))
                    }
                }
            }
        }
    }
}

// ── XML Preview ───────────────────────────────────────────────────────────────
@Composable
private fun XmlPreviewContent(root: XmlViewNode, device: PreviewDevice, zoom: Float, darkMode: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PhoneFrame(device = device, zoom = zoom, darkMode = darkMode) {
            val bg = if (darkMode) Color(0xFF121212) else Color(0xFFFAFAFA)
            Box(modifier = Modifier.fillMaxSize().background(bg)) {
                XmlViewRenderer(node = root, darkMode = darkMode)
            }
        }
    }
}

@Composable
fun XmlViewRenderer(node: XmlViewNode, darkMode: Boolean, depth: Int = 0) {
    if (node.visibility == "gone") return
    val textColor = if (darkMode) OnBackground else Color(0xFF1C1B1F)
    val surfaceColor = if (darkMode) Surface else Color.White
    val alpha = if (node.visibility == "invisible") 0f else 1f

    when (node.simpleName) {
        "TextView" -> Text(node.text.ifEmpty { "TextView" },
            style = MaterialTheme.typography.bodyMedium.copy(color = textColor.copy(alpha = alpha)),
            modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier).padding(node.attrs["android:padding"]?.replace("dp","")?.toIntOrNull()?.dp ?: 0.dp))
        "EditText" -> Box(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.width(200.dp)).height(48.dp).alpha(alpha).border(1.dp, Outline, RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
            Text(node.hint.ifEmpty { node.text.ifEmpty { "EditText" } }, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
        }
        "Button" -> Box(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).height(40.dp).alpha(alpha).background(Primary, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(node.text.ifEmpty { "Button" }, style = MaterialTheme.typography.labelMedium.copy(color = OnPrimary))
        }
        "ImageView" -> Box(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.size(100.dp)).height(100.dp).alpha(alpha).background(SurfaceVariant, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, null, tint = OnSurfaceDim, modifier = Modifier.size(32.dp))
        }
        "CheckBox" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(20.dp).background(Primary, RoundedCornerShape(3.dp)).alpha(alpha), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            if (node.text.isNotEmpty()) Text(node.text, style = MaterialTheme.typography.bodySmall.copy(color = textColor))
        }
        "Switch" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(44.dp).height(24.dp).background(Primary, RoundedCornerShape(12.dp)).alpha(alpha), contentAlignment = Alignment.CenterEnd) { Box(Modifier.padding(2.dp).size(20.dp).background(Color.White, CircleShape)) }
            if (node.text.isNotEmpty()) Text(node.text, style = MaterialTheme.typography.bodySmall.copy(color = textColor))
        }
        "ProgressBar" -> LinearProgressIndicator(progress = { 0.5f }, modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.width(120.dp)).alpha(alpha), color = Primary, trackColor = SurfaceVariant)
        "SeekBar" -> Slider(value = 0.5f, onValueChange = {}, modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.width(120.dp)).alpha(alpha), colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary))
        "LinearLayout" -> {
            val isH = node.orientation == "horizontal"
            if (isH) Row(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).alpha(alpha), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
            } else Column(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).alpha(alpha), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
            }
        }
        "ConstraintLayout","RelativeLayout","FrameLayout" -> Box(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).alpha(alpha)) {
            node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
        }
        "ScrollView","NestedScrollView" -> Column(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).verticalScroll(rememberScrollState()).alpha(alpha)) {
            node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
        }
        "CardView","MaterialCardView" -> Card(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).alpha(alpha), colors = CardDefaults.cardColors(containerColor = surfaceColor)) {
            node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
        }
        "RecyclerView" -> Column(modifier = Modifier.fillMaxWidth().alpha(alpha), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Row(modifier = Modifier.fillMaxWidth().padding(4.dp).background(surfaceColor, RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(SurfaceVariant, CircleShape))
                Column { Box(Modifier.width(80.dp).height(10.dp).background(OnSurfaceDim.copy(0.3f), RoundedCornerShape(4.dp))); Spacer(Modifier.height(4.dp)); Box(Modifier.width(50.dp).height(8.dp).background(OnSurfaceDim.copy(0.15f), RoundedCornerShape(4.dp))) }
            }}
        }
        "View" -> Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Outline).alpha(alpha))
        else -> if (node.children.isNotEmpty()) Column(modifier = Modifier.then(if (node.isMatchParent) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()).alpha(alpha), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children.forEach { XmlViewRenderer(it, darkMode, depth + 1) }
        }
    }
}

// ── Phone Frame ───────────────────────────────────────────────────────────────
@Composable
fun PhoneFrame(device: PreviewDevice, zoom: Float, darkMode: Boolean, content: @Composable BoxScope.() -> Unit) {
    val w = (device.width * zoom).dp
    val h = (device.height * zoom).dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(width = w + 16.dp, height = h + 32.dp).clip(RoundedCornerShape(24.dp)).border(3.dp, Color(0xFF2D333B), RoundedCornerShape(24.dp)).background(Color(0xFF1C2128))) {
            Box(modifier = Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF0D1117).copy(alpha = 0.95f)).align(Alignment.TopCenter), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 60.dp, height = 6.dp).background(Color(0xFF2D333B), RoundedCornerShape(3.dp)))
            }
            Box(modifier = Modifier.fillMaxSize().padding(top = 24.dp, bottom = 8.dp, start = 8.dp, end = 8.dp).clip(RoundedCornerShape(16.dp)).background(if (darkMode) Color(0xFF0D1117) else Color(0xFFFAFAFA)).verticalScroll(rememberScrollState()), content = content)
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 80.dp, height = 4.dp).background(Color(0xFF3D444D), RoundedCornerShape(2.dp)))
        }
        Text("${device.label} ${(device.width * zoom).toInt()}×${(device.height * zoom).toInt()}",
            style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontSize = 9.sp))
    }
}

@Composable
private fun EmptyPreview() = Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.VisibilityOff, null, tint = OnSurfaceDim.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
        Text("No preview available", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim.copy(alpha = 0.5f)))
        Text("Open a .kt or .xml file", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim.copy(alpha = 0.35f)))
    }
}

private fun deviceIcon(d: PreviewDevice) = when (d) {
    PreviewDevice.PHONE  -> Icons.Default.PhoneAndroid
    PreviewDevice.TABLET -> Icons.Default.Tablet
    PreviewDevice.FOLD   -> Icons.Default.TabletAndroid
    PreviewDevice.WATCH  -> Icons.Default.Watch
}
