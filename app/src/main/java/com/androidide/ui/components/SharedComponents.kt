package com.androidide.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import com.androidide.data.models.EditorLanguage
import com.androidide.ui.theme.*

@Composable
fun IDEIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = OnSurface,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(size + 12.dp),
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun FileTypeIcon(language: EditorLanguage, size: Dp = 16.dp) {
    val (text, color) = when (language) {
        EditorLanguage.KOTLIN   -> "K"  to IconKotlin
        EditorLanguage.JAVA     -> "J"  to IconJava
        EditorLanguage.XML      -> "X"  to IconXml
        EditorLanguage.GRADLE   -> "G"  to IconGradle
        EditorLanguage.JSON     -> "{}" to IconJson
        EditorLanguage.MARKDOWN -> "M"  to OnSurfaceDim
        EditorLanguage.PROGUARD -> "P"  to LogWarning
        else                    -> "T"  to OnSurfaceDim
    }
    Box(
        modifier = Modifier
            .size(size + 4.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = (size.value * 0.7f).sp,
                color = color,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(color = color, fontSize = 10.sp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color = OnSurfaceDim,
            letterSpacing = 1.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        ),
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun LoadingOverlay(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Primary, strokeWidth = 2.dp)
                Text(message, style = MaterialTheme.typography.bodyMedium.copy(color = OnBackground))
            }
        }
    }
}
