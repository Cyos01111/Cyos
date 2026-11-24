package com.cyosplay.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-contrast accent red tuned for crisp legibility on Pure AMOLED Black and dark/light themes.
 */
val NewFolderNotificationDotColor = Color(0xFFFF3B30)

/**
 * Lightweight, zero-animation point notification dot for newly discovered video folders
 * and the Folders navigation tab indicator. Designed for minimal recomposition cost on
 * 1 GB–12 GB RAM devices.
 */
@Composable
fun NotificationDot(
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    dotColor: Color = NewFolderNotificationDotColor,
    borderColor: Color = MaterialTheme.colorScheme.surface,
    borderWidth: Dp = 1.5.dp,
    contentDesc: String = "New folder notification",
    tag: String? = null
) {
    val totalSize = size + (borderWidth * 2)
    Box(
        modifier = modifier
            .size(totalSize)
            .background(color = dotColor, shape = CircleShape)
            .border(width = borderWidth, color = borderColor, shape = CircleShape)
            .semantics { contentDescription = contentDesc }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

/**
 * Lightweight container that overlays a [NotificationDot] at the top-right corner of
 * a folder thumbnail/icon or bottom navigation icon when [showDot] is true.
 */
@Composable
fun NotificationDotAnchor(
    showDot: Boolean,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    dotOffsetX: Dp = 2.dp,
    dotOffsetY: Dp = (-2).dp,
    dotTag: String? = null,
    dotContentDescription: String = "New video folder available",
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (showDot) {
            NotificationDot(
                size = dotSize,
                contentDesc = dotContentDescription,
                tag = dotTag,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = dotOffsetX, y = dotOffsetY)
            )
        }
    }
}
