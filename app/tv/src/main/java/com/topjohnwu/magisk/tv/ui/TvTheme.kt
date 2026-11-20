package com.topjohnwu.magisk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Layout constants shared by every TV screen. Margins keep content clear of the overscan area. */
object TvKit {
    val SafeHorizontal = 56.dp
    val SafeVertical = 32.dp
    val PillShape = RoundedCornerShape(percent = 50)
    val CardShape = RoundedCornerShape(20.dp)
}

/** Colors of the Magisk brand (the mask logo's teal, yellow and orange) on a near-black base. */
object TvColors {
    val Teal = Color(0xFF00AF9C)
    val Yellow = Color(0xFFFFB327)
    val Orange = Color(0xFFFF6E40)
    val Danger = Color(0xFFFF8A80)
    val Good = Color(0xFF69DBA1)
}

private val MagiskDarkColors = darkColorScheme(
    primary = TvColors.Teal,
    onPrimary = Color(0xFF00201C),
    secondary = TvColors.Yellow,
    tertiary = TvColors.Orange,
    background = Color(0xFF0B0F0F),
    onBackground = Color(0xFFE3EEEC),
    surface = Color(0xFF131A19),
    onSurface = Color(0xFFE3EEEC),
    surfaceVariant = Color(0xFF1B2524),
    onSurfaceVariant = Color(0xFFB9CBC8),
    error = TvColors.Danger,
    onError = Color(0xFF410002),
)

/** The TV UI is always dark, like Google TV. */
@Composable
fun MagiskTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MagiskDarkColors, content = content)
}

/** Light, teal-tinted fill used for every focused element. */
@Composable
@ReadOnlyComposable
fun tvFocusedContainer(): Color = lerp(MaterialTheme.colorScheme.primary, Color.White, 0.74f)

/** Dark content color drawn on [tvFocusedContainer]. */
@Composable
@ReadOnlyComposable
fun tvFocusedContent(): Color = lerp(MaterialTheme.colorScheme.background, Color.Black, 0.4f)

/** Dark backdrop with a faint teal glow from the top-right corner. */
@Composable
fun TvBackdrop(modifier: Modifier = Modifier, glow: Color = TvColors.Teal, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.8f,
                    ),
                )
            },
        content = content,
    )
}
