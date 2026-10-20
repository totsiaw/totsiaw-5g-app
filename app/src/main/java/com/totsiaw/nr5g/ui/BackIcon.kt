package com.totsiaw.nr5g.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * HyperOS back arrow. Path copied from MIUIX's `MiuixIcons.Regular.Back`
 * (miuix-icons v0.8.8, Copyright 2025 compose-miuix-ui contributors, Apache-2.0),
 * which ships in a separate icon pack this app doesn't otherwise need.
 */
private val BackArrow: ImageVector by lazy {
    ImageVector.Builder(
        name = "Back.Regular",
        defaultWidth = 24.0f.dp,
        defaultHeight = 24.0f.dp,
        viewportWidth = 1330.2146f,
        viewportHeight = 1330.2146f,
    ).apply {
        group(scaleX = 1.0f, scaleY = -1.0f, translationX = -0.6366f, translationY = 1039.348f) {
            addPath(
                pathData = listOf(
                    PathNode.MoveTo(256.0f, 331.0f),
                    PathNode.HorizontalTo(1188.0f),
                    PathNode.QuadTo(1204.0f, 331.0f, 1212.0f, 339.0f),
                    PathNode.QuadTo(1220.0f, 347.0f, 1220.0f, 362.0f),
                    PathNode.VerticalTo(389.0f),
                    PathNode.QuadTo(1220.0f, 402.0f, 1211.5f, 409.5f),
                    PathNode.QuadTo(1203.0f, 417.0f, 1188.0f, 417.0f),
                    PathNode.HorizontalTo(256.0f),
                    PathNode.LineTo(540.0f, 701.0f),
                    PathNode.QuadTo(550.0f, 711.0f, 550.0f, 721.0f),
                    PathNode.QuadTo(550.0f, 731.0f, 538.0f, 743.0f),
                    PathNode.LineTo(521.0f, 760.0f),
                    PathNode.QuadTo(509.0f, 772.0f, 499.0f, 772.0f),
                    PathNode.QuadTo(489.0f, 772.0f, 477.0f, 760.0f),
                    PathNode.LineTo(130.0f, 412.0f),
                    PathNode.QuadTo(112.0f, 394.0f, 111.5f, 374.5f),
                    PathNode.QuadTo(111.0f, 355.0f, 131.0f, 335.0f),
                    PathNode.LineTo(477.0f, -11.0f),
                    PathNode.QuadTo(489.0f, -23.0f, 498.5f, -23.5f),
                    PathNode.QuadTo(508.0f, -24.0f, 521.0f, -11.0f),
                    PathNode.LineTo(540.0f, 8.0f),
                    PathNode.QuadTo(551.0f, 19.0f, 551.0f, 27.5f),
                    PathNode.QuadTo(551.0f, 36.0f, 539.0f, 48.0f),
                    PathNode.Close,
                ),
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                pathFillType = PathFillType.NonZero,
            )
        }
    }.build()
}

/** Top-bar back button, matching MIUIX's own example (mirrored for right-to-left). */
@Composable
fun BackNavigationIcon(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(modifier = modifier, onClick = onClick) {
        Icon(
            modifier = Modifier.graphicsLayer { if (rtl) scaleX = -1f },
            imageVector = BackArrow,
            contentDescription = "Back",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}
