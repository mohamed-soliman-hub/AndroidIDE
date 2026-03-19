package com.androidide.ui.designer

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.androidide.ui.theme.*
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Visual Designer Screen — Split-pane design view + code view
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualDesignerScreen(
    initialCode: String = "",
    onCodeChanged: (String) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val components  = remember { mutableStateListOf<DesignComponent>() }
    var selected    by remember { mutableStateOf<String?>(null) }
    var showCode    by remember { mutableStateOf(false) }
    var showAttribs by remember { mutableStateOf(true) }
    var screenName  by remember { mutableStateOf("MyScreen") }

    // Derived generated code (always in sync with components list)
    val generatedCode by remember(components.toList(), screenName) {
        derivedStateOf { DesignCodeGenerator.generateFullScreen(components.toList(), screenName) }
    }

    // Notify parent on change
    LaunchedEffect(generatedCode) { onCodeChanged(generatedCode) }

    val selectedComponent = components.find { it.id == selected }

    Scaffold(
        containerColor = Background,
        topBar = {
            DesignerTopBar(
                screenName    = screenName,
                onScreenName  = { screenName = it },
                showCode      = showCode,
                onToggleCode  = { showCode = !showCode },
                showAttribs   = showAttribs,
                onToggleAttribs = { showAttribs = !showAttribs },
                onClearCanvas = { components.clear(); selected = null },
                onClose       = onClose,
                componentCount = components.size
            )
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Left: Component Palette ────────────────────────────────────────
            ComponentPalette(
                modifier = Modifier.width(140.dp).fillMaxHeight(),
                onAddComponent = { type ->
                    val comp = DesignComponent(
                        id     = UUID.randomUUID().toString().take(8),
                        type   = type,
                        x      = 20f,
                        y      = (components.size * 60f + 20f).coerceAtMost(500f),
                        width  = type.defaultWidth,
                        height = type.defaultHeight,
                        props  = ComponentProps(text = type.displayName)
                    )
                    components.add(comp)
                    selected = comp.id
                }
            )

            VerticalDivider(color = Outline)

            // ── Center: Design Canvas ──────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DesignCanvas(
                    components      = components,
                    selectedId      = selected,
                    onSelect        = { selected = it },
                    onMove          = { id, dx, dy ->
                        val idx = components.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            val c = components[idx]
                            components[idx] = c.copy(
                                x = max(0f, c.x + dx),
                                y = max(0f, c.y + dy)
                            )
                        }
                    },
                    onResize        = { id, dw, dh ->
                        val idx = components.indexOfFirst { it.id == id }
                        if (idx >= 0) {
                            val c = components[idx]
                            components[idx] = c.copy(
                                width  = max(40f, c.width + dw),
                                height = max(20f, c.height + dh)
                            )
                        }
                    },
                    onDelete        = { id ->
                        components.removeAll { it.id == id }
                        if (selected == id) selected = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Code overlay toggle
                AnimatedVisibility(
                    visible = showCode,
                    modifier = Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit  = slideOutVertically { it }
                ) {
                    GeneratedCodePane(code = generatedCode)
                }
            }

            // ── Right: Attribute Editor ────────────────────────────────────────
            AnimatedVisibility(
                visible = showAttribs,
                enter = slideInHorizontally { it },
                exit  = slideOutHorizontally { it }
            ) {
                VerticalDivider(color = Outline)
                AttributeEditorPanel(
                    component = selectedComponent,
                    modifier  = Modifier.width(180.dp).fillMaxHeight(),
                    onUpdate  = { updated ->
                        val idx = components.indexOfFirst { it.id == updated.id }
                        if (idx >= 0) components[idx] = updated
                    },
                    onDelete  = { id ->
                        components.removeAll { it.id == id }
                        selected = null
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Designer Top Bar
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignerTopBar(
    screenName: String,
    onScreenName: (String) -> Unit,
    showCode: Boolean,
    onToggleCode: () -> Unit,
    showAttribs: Boolean,
    onToggleAttribs: () -> Unit,
    onClearCanvas: () -> Unit,
    onClose: () -> Unit,
    componentCount: Int
) {
    var editingName by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Close", tint = OnSurfaceDim)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DesignServices, null, tint = Tertiary, modifier = Modifier.size(18.dp))
                if (editingName) {
                    BasicEditText(
                        value = screenName,
                        onValueChange = onScreenName,
                        onDone = { editingName = false },
                        modifier = Modifier.width(140.dp)
                    )
                } else {
                    Text(
                        screenName,
                        color = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { editingName = true }
                    )
                }
                if (componentCount > 0) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Primary.copy(alpha = 0.15f)) {
                        Text("$componentCount", style = MaterialTheme.typography.labelSmall.copy(color = Primary),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        },
        actions = {
            // Toggle attributes panel
            IconButton(onClick = onToggleAttribs) {
                Icon(Icons.Default.Tune, "Attributes",
                    tint = if (showAttribs) Tertiary else OnSurfaceDim)
            }
            // Toggle code view
            IconButton(onClick = onToggleCode) {
                Icon(Icons.Default.Code, "Code",
                    tint = if (showCode) Primary else OnSurfaceDim)
            }
            // Clear canvas
            IconButton(onClick = onClearCanvas) {
                Icon(Icons.Default.LayersClear, "Clear", tint = LogError)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Component Palette
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ComponentPalette(
    modifier: Modifier = Modifier,
    onAddComponent: (DesignComponentType) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(PaletteCategory.BASIC) }

    Surface(modifier = modifier, color = SurfaceVariant) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Text(
                "PALETTE",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = OnSurfaceDim, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
            HorizontalDivider(color = Outline)

            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = PaletteCategory.values().indexOf(selectedCategory),
                containerColor   = SurfaceVariant,
                contentColor     = Primary,
                edgePadding      = 0.dp,
                indicator        = {},
                divider          = {}
            ) {
                PaletteCategory.values().forEachIndexed { i, cat ->
                    Tab(
                        selected = selectedCategory == cat,
                        onClick  = { selectedCategory = cat },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            cat.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selectedCategory == cat) Primary else OnSurfaceDim,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
            HorizontalDivider(color = Outline)

            // Component list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filtered = DesignComponentType.values().filter { it.category == selectedCategory }
                items(filtered) { type ->
                    PaletteItem(type = type, onClick = { onAddComponent(type) })
                }
            }
        }
    }
}

@Composable
private fun PaletteItem(type: DesignComponentType, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(8.dp),
        color   = Surface,
        border  = BorderStroke(1.dp, Outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = paletteIcon(type),
                contentDescription = null,
                tint = paletteIconColor(type),
                modifier = Modifier.size(15.dp)
            )
            Text(
                type.displayName,
                style = MaterialTheme.typography.labelSmall.copy(color = OnSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Design Canvas — the main draggable, resizable area
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DesignCanvas(
    components: List<DesignComponent>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMove: (id: String, dx: Float, dy: Float) -> Unit,
    onResize: (id: String, dw: Float, dh: Float) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    // Phone frame + canvas
    Box(
        modifier = modifier
            .background(EditorBackground)
            .pointerInput(Unit) {
                detectTapGestures { onSelect(null) }  // deselect on bg tap
            }
    ) {
        // Grid lines (subtle)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val gridColor = OnSurfaceDim.copy(alpha = 0.06f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        // Phone frame overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(260.dp)
                .height(520.dp)
                .border(2.dp, Outline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        )

        // Render all components
        components.forEach { comp ->
            val isSelected = comp.id == selectedId
            with(density) {
                DraggableResizableComponent(
                    component  = comp,
                    isSelected = isSelected,
                    onSelect   = { onSelect(comp.id); haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onMove     = { dx, dy -> onMove(comp.id, dx, dy) },
                    onResize   = { dw, dh -> onResize(comp.id, dw, dh) },
                    onDelete   = { onDelete(comp.id) }
                )
            }
        }

        // Empty state hint
        if (components.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.TouchApp, null, tint = OnSurfaceDim.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                Text("Tap a component from\nthe palette to add it",
                    style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim.copy(alpha = 0.5f), textAlign = TextAlign.Center))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Draggable + Resizable Component Wrapper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DraggableResizableComponent(
    component: DesignComponent,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (dw: Float, dh: Float) -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val handleSize = 10.dp
    val handleSizePx = with(density) { handleSize.toPx() }

    Box(
        modifier = Modifier
            .absoluteOffset(x = component.x.dp, y = component.y.dp)
            .size(width = component.width.dp, height = component.height.dp)
            .then(
                if (isSelected)
                    Modifier.border(2.dp, Primary, RoundedCornerShape(4.dp))
                else
                    Modifier.border(1.dp, Outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            )
            // Drag to move
            .pointerInput(component.id) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { _, dragAmount ->
                        with(density) {
                            onMove(dragAmount.x.toDp().value, dragAmount.y.toDp().value)
                        }
                    }
                )
            }
            .clickable { onSelect() }
    ) {
        // ── Component preview rendering ────────────────────────────────────────
        ComponentPreview(component = component, modifier = Modifier.fillMaxSize())

        // ── Selection overlay ──────────────────────────────────────────────────
        if (isSelected) {
            // Dimension label
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-18).dp)
                    .background(Primary, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "${component.width.roundToInt()}×${component.height.roundToInt()}dp",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = OnPrimary, fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .size(20.dp)
                    .background(LogError, CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }

            // ── Resize handles (corners + edges) ──────────────────────────────
            // Bottom-right corner resize
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (handleSize / 2), y = (handleSize / 2))
                    .size(handleSize),
                cursor = PointerIcon.Crosshair
            ) { dragAmount ->
                with(density) {
                    onResize(dragAmount.x.toDp().value, dragAmount.y.toDp().value)
                }
            }
            // Right-middle resize
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (handleSize / 2))
                    .size(handleSize),
                cursor = PointerIcon.Crosshair
            ) { dragAmount ->
                with(density) { onResize(dragAmount.x.toDp().value, 0f) }
            }
            // Bottom-middle resize
            ResizeHandle(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (handleSize / 2))
                    .size(handleSize),
                cursor = PointerIcon.Crosshair
            ) { dragAmount ->
                with(density) { onResize(0f, dragAmount.y.toDp().value) }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    cursor: PointerIcon = PointerIcon.Crosshair,
    onDrag: (Offset) -> Unit
) {
    Box(
        modifier = modifier
            .background(Primary, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount) }
            }
            .pointerHoverIcon(cursor)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Live Component Preview Renderer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ComponentPreview(component: DesignComponent, modifier: Modifier = Modifier) {
    val p = component.props
    Box(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        when (component.type) {
            DesignComponentType.TEXT -> {
                Text(
                    text = p.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OnBackground,
                        fontSize = p.textSize.sp,
                        fontWeight = when (p.fontWeight) {
                            "Bold" -> FontWeight.Bold; "SemiBold" -> FontWeight.SemiBold
                            else -> FontWeight.Normal
                        }
                    ),
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                    maxLines = 3, overflow = TextOverflow.Ellipsis
                )
            }
            DesignComponentType.BUTTON -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Primary, RoundedCornerShape(p.cornerRadius.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.text, style = MaterialTheme.typography.labelMedium.copy(color = OnPrimary, fontWeight = FontWeight.SemiBold))
                }
            }
            DesignComponentType.OUTLINED_BUTTON -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .border(1.5.dp, Primary, RoundedCornerShape(p.cornerRadius.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.text, style = MaterialTheme.typography.labelMedium.copy(color = Primary, fontWeight = FontWeight.SemiBold))
                }
            }
            DesignComponentType.TEXT_FIELD -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .border(1.5.dp, Outline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(p.hint, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                }
            }
            DesignComponentType.IMAGE -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(SurfaceVariant, RoundedCornerShape(p.cornerRadius.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Image, null, tint = OnSurfaceDim, modifier = Modifier.size(28.dp))
                }
            }
            DesignComponentType.ICON -> {
                Icon(Icons.Default.Star, null, tint = Primary,
                    modifier = Modifier.fillMaxSize().padding(4.dp))
            }
            DesignComponentType.CARD -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .shadow(p.elevation.dp, RoundedCornerShape(p.cornerRadius.dp))
                        .background(Surface, RoundedCornerShape(p.cornerRadius.dp))
                        .border(1.dp, Outline, RoundedCornerShape(p.cornerRadius.dp))
                )
            }
            DesignComponentType.SURFACE -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(SurfaceVariant, RoundedCornerShape(p.cornerRadius.dp))
                        .border(1.dp, Outline, RoundedCornerShape(p.cornerRadius.dp))
                )
            }
            DesignComponentType.COLUMN -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .border(1.5.dp, IconKotlin.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text("Column", style = MaterialTheme.typography.labelSmall.copy(color = IconKotlin, fontSize = 9.sp))
                }
            }
            DesignComponentType.ROW -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .border(1.5.dp, Secondary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Row", style = MaterialTheme.typography.labelSmall.copy(color = Secondary, fontSize = 9.sp))
                }
            }
            DesignComponentType.BOX -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .border(1.5.dp, Tertiary.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Box", style = MaterialTheme.typography.labelSmall.copy(color = Tertiary, fontSize = 9.sp))
                }
            }
            DesignComponentType.SPACER -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = OnSurfaceDim.copy(alpha = 0.15f))
                    val step = 8.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(OnSurfaceDim.copy(alpha = 0.2f), Offset(x, 0f), Offset(x + step / 2, size.height), strokeWidth = 1f)
                        x += step
                    }
                }
            }
            DesignComponentType.DIVIDER -> {
                Box(modifier = Modifier.fillMaxSize().background(Outline))
            }
            DesignComponentType.SWITCH -> {
                Row(modifier = Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(36.dp).height(20.dp)
                        .background(Primary, RoundedCornerShape(10.dp)), contentAlignment = Alignment.CenterEnd) {
                        Box(modifier = Modifier.padding(2.dp).size(16.dp).background(Color.White, CircleShape))
                    }
                }
            }
            DesignComponentType.CHECKBOX -> {
                Box(modifier = Modifier.fillMaxSize().padding(2.dp)
                    .background(Primary, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            DesignComponentType.SLIDER -> {
                Box(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Outline, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(4.dp).background(Primary, RoundedCornerShape(2.dp)))
                    Box(modifier = Modifier.offset(x = (component.width * 0.6f - 8).dp, y = (-6).dp)
                        .size(16.dp).background(Primary, CircleShape).border(2.dp, Color.White, CircleShape))
                }
            }
            DesignComponentType.CIRCULAR_PROGRESS -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size((component.width * 0.7f).dp), color = Primary, strokeWidth = 3.dp, progress = { 0.65f })
                }
            }
            DesignComponentType.PROGRESS_BAR -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(progress = { 0.65f }, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = Primary, trackColor = SurfaceVariant)
                }
            }
            DesignComponentType.CHIP -> {
                Box(modifier = Modifier.fillMaxSize()
                    .border(1.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center) {
                    Text(p.text, style = MaterialTheme.typography.labelSmall.copy(color = Primary))
                }
            }
            DesignComponentType.BADGE -> {
                Box(modifier = Modifier.size(24.dp)
                    .background(LogError, CircleShape), contentAlignment = Alignment.Center) {
                    Text(p.text.take(2), style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp))
                }
            }
            DesignComponentType.FAB -> {
                Box(modifier = Modifier.fillMaxSize()
                    .background(Primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, null, tint = OnPrimary, modifier = Modifier.size(22.dp))
                }
            }
            DesignComponentType.TOP_BAR -> {
                Row(modifier = Modifier.fillMaxSize()
                    .background(Surface), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowBack, null, tint = OnSurface, modifier = Modifier.padding(8.dp).size(18.dp))
                    Text(p.text, style = MaterialTheme.typography.titleSmall.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
                }
            }
            DesignComponentType.BOTTOM_BAR -> {
                Row(modifier = Modifier.fillMaxSize()
                    .background(Surface), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Person).forEachIndexed { i, icon ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = if (i == 0) Primary else OnSurfaceDim, modifier = Modifier.size(18.dp))
                            Box(modifier = Modifier.height(2.dp).width(4.dp).background(if (i == 0) Primary else Color.Transparent, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Attribute Editor Panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AttributeEditorPanel(
    component: DesignComponent?,
    modifier: Modifier = Modifier,
    onUpdate: (DesignComponent) -> Unit,
    onDelete: (String) -> Unit
) {
    Surface(modifier = modifier, color = SurfaceVariant) {
        if (component == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Tune, null, tint = OnSurfaceDim.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
                    Text("Select a component\nto edit its attributes",
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, textAlign = TextAlign.Center))
                }
            }
            return@Surface
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(component.type.displayName, style = MaterialTheme.typography.labelMedium.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
                    Text(component.id, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontFamily = FontFamily.Monospace, fontSize = 9.sp))
                }
                IconButton(onClick = { onDelete(component.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = LogError, modifier = Modifier.size(14.dp))
                }
            }
            HorizontalDivider(color = Outline)

            // ── Position & Size ───────────────────────────────────────────
            AttribSection("LAYOUT") {
                AttribRow("X", "${component.x.roundToInt()} dp") {
                    onUpdate(component.copy(x = it.toFloatOrNull() ?: component.x))
                }
                AttribRow("Y", "${component.y.roundToInt()} dp") {
                    onUpdate(component.copy(y = it.toFloatOrNull() ?: component.y))
                }
                AttribRow("W", "${component.width.roundToInt()} dp") {
                    onUpdate(component.copy(width = (it.toFloatOrNull() ?: component.width).coerceAtLeast(20f)))
                }
                AttribRow("H", "${component.height.roundToInt()} dp") {
                    onUpdate(component.copy(height = (it.toFloatOrNull() ?: component.height).coerceAtLeast(10f)))
                }
            }

            // ── Text Properties ───────────────────────────────────────────
            AttribSection("TEXT") {
                AttribRow("Text", component.props.text) {
                    onUpdate(component.copy(props = component.props.copy(text = it)))
                }
                AttribRow("Hint", component.props.hint) {
                    onUpdate(component.copy(props = component.props.copy(hint = it)))
                }
                AttribRow("Size", "${component.props.textSize}") {
                    onUpdate(component.copy(props = component.props.copy(textSize = it.toIntOrNull() ?: component.props.textSize)))
                }
                // Font weight selector
                Text("Weight", style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim), modifier = Modifier.padding(start = 10.dp, top = 6.dp))
                Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Normal", "Bold").forEach { w ->
                        val isSelected = component.props.fontWeight == w
                        Surface(
                            onClick = { onUpdate(component.copy(props = component.props.copy(fontWeight = w))) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) Primary.copy(alpha = 0.2f) else Surface,
                            border = BorderStroke(1.dp, if (isSelected) Primary else Outline),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(w, style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) Primary else OnSurfaceDim,
                                textAlign = TextAlign.Center),
                                modifier = Modifier.padding(4.dp))
                        }
                    }
                }
            }

            // ── Appearance ────────────────────────────────────────────────
            AttribSection("APPEARANCE") {
                AttribRow("Radius", "${component.props.cornerRadius}") {
                    onUpdate(component.copy(props = component.props.copy(cornerRadius = it.toIntOrNull() ?: component.props.cornerRadius)))
                }
                AttribRow("Elevation", "${component.props.elevation}") {
                    onUpdate(component.copy(props = component.props.copy(elevation = it.toIntOrNull() ?: component.props.elevation)))
                }
                AttribRow("Pad H", "${component.props.paddingH}") {
                    onUpdate(component.copy(props = component.props.copy(paddingH = it.toIntOrNull() ?: component.props.paddingH)))
                }
                AttribRow("Pad V", "${component.props.paddingV}") {
                    onUpdate(component.copy(props = component.props.copy(paddingV = it.toIntOrNull() ?: component.props.paddingV)))
                }
                // Alpha slider label
                Text("Alpha: ${(component.props.alpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim), modifier = Modifier.padding(start = 10.dp, top = 4.dp))
                Slider(
                    value = component.props.alpha,
                    onValueChange = { onUpdate(component.copy(props = component.props.copy(alpha = it))) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttribSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelSmall.copy(
            color = OnSurfaceDim, letterSpacing = 0.8.sp, fontSize = 9.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp))
        content()
        HorizontalDivider(color = Outline.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AttribRow(label: String, value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value.replace(" dp", "")) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim, fontSize = 10.sp),
            modifier = Modifier.width(36.dp))
        BasicEditText(
            value = text,
            onValueChange = { text = it },
            onDone = { onCommit(text) },
            modifier = Modifier.weight(1f).height(28.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Generated Code Pane
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GeneratedCodePane(code: String) {
    Surface(color = Color(0xFF0A0A14), tonalElevation = 8.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Code header
            Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Code, null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text("Generated Compose Code", style = MaterialTheme.typography.labelSmall.copy(color = OnBackground, fontWeight = FontWeight.SemiBold))
                }
                Surface(shape = RoundedCornerShape(4.dp), color = LogSuccess.copy(alpha = 0.15f)) {
                    Text("Live Sync", style = MaterialTheme.typography.labelSmall.copy(color = LogSuccess, fontSize = 9.sp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            HorizontalDivider(color = Outline)
            // Scrollable code
            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Text(
                    code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = OnSurface
                    ),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Basic edit text (no outline)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BasicEditText(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.labelSmall.copy(
            color = OnBackground, fontSize = 11.sp, fontFamily = FontFamily.Monospace
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onDone() }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Primary.copy(alpha = 0.6f),
            unfocusedBorderColor = Outline.copy(alpha = 0.4f),
            focusedTextColor     = OnBackground,
            unfocusedTextColor   = OnBackground,
            cursorColor          = Primary,
            focusedContainerColor   = SurfaceElevated,
            unfocusedContainerColor = SurfaceElevated
        ),
        shape = RoundedCornerShape(4.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Palette icon + color helpers
// ─────────────────────────────────────────────────────────────────────────────
private fun paletteIcon(type: DesignComponentType) = when (type) {
    DesignComponentType.TEXT             -> Icons.Default.TextFields
    DesignComponentType.BUTTON           -> Icons.Default.SmartButton
    DesignComponentType.OUTLINED_BUTTON  -> Icons.Default.RadioButtonUnchecked
    DesignComponentType.TEXT_FIELD       -> Icons.Default.EditNote
    DesignComponentType.IMAGE            -> Icons.Default.Image
    DesignComponentType.ICON             -> Icons.Default.Star
    DesignComponentType.DIVIDER          -> Icons.Default.HorizontalRule
    DesignComponentType.SWITCH           -> Icons.Default.ToggleOn
    DesignComponentType.CHECKBOX         -> Icons.Default.CheckBox
    DesignComponentType.SLIDER           -> Icons.Default.Tune
    DesignComponentType.CARD             -> Icons.Default.CreditCard
    DesignComponentType.SURFACE          -> Icons.Default.WebAsset
    DesignComponentType.COLUMN           -> Icons.Default.ViewColumn
    DesignComponentType.ROW              -> Icons.Default.TableRows
    DesignComponentType.BOX              -> Icons.Default.CheckBoxOutlineBlank
    DesignComponentType.SPACER           -> Icons.Default.SpaceBar
    DesignComponentType.PROGRESS_BAR     -> Icons.Default.LinearScale
    DesignComponentType.CIRCULAR_PROGRESS-> Icons.Default.Loop
    DesignComponentType.BADGE            -> Icons.Default.FiberManualRecord
    DesignComponentType.CHIP             -> Icons.Default.Label
    DesignComponentType.FAB              -> Icons.Default.AddCircle
    DesignComponentType.TOP_BAR          -> Icons.Default.WebAsset
    DesignComponentType.BOTTOM_BAR       -> Icons.Default.TableRows
}

private fun paletteIconColor(type: DesignComponentType) = when (type.category) {
    PaletteCategory.BASIC      -> Primary
    PaletteCategory.CONTAINERS -> Secondary
    PaletteCategory.FEEDBACK   -> Tertiary
    PaletteCategory.NAVIGATION -> LogInfo
}
