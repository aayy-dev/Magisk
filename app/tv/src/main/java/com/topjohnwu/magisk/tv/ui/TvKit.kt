package com.topjohnwu.magisk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

@Composable
fun TvPageTitle(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun TvSectionHeader(text: String, modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 26.dp) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = inset, top = 22.dp, bottom = 6.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium,
    )
}

// ---------------------------------------------------------------------------------------------
// Rows (lists and settings)
// ---------------------------------------------------------------------------------------------

/** Minimum height of [TvRow]; detail panels set a smaller value to fit more rows on screen. */
val LocalTvRowMinHeight = androidx.compose.runtime.staticCompositionLocalOf { 64.dp }

/** When true, rows rest on a faint surface so a label and its control read as one unit (detail panels). */
val LocalTvRowResting = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A full-width focusable list row: transparent until focused, then a light pill with dark text, as
 * in the Google TV settings. Informational rows still take focus (with a no-op click) so long lists
 * stay scrollable with a remote.
 */
@Composable
fun TvRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable (focused: Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    titleSize: androidx.compose.ui.unit.TextUnit = 17.sp,
    titleWeight: FontWeight = FontWeight.Medium,
    /** Marks the row a detail panel is showing; the tint stays while focus is in the panel. */
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val resting = LocalTvRowResting.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        // A disabled row is skipped by the D-pad instead of showing a second, outline-only focus style
        modifier = modifier
            .fillMaxWidth()
            .focusProperties { canFocus = enabled },
        shape = ClickableSurfaceDefaults.shape(shape = TvKit.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                resting -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else -> Color.Transparent
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = tvFocusedContainer(),
            focusedContentColor = tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
            disabledContainerColor = if (resting) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f) else Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = LocalTvRowMinHeight.current)
                .padding(
                    start = if (icon != null || leading != null) 14.dp else 26.dp,
                    end = 24.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading(focused)
                Spacer(Modifier.width(18.dp))
            } else if (icon != null) {
                TvIconBadge(icon, focused)
                Spacer(Modifier.width(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    fontWeight = titleWeight,
                    lineHeight = titleSize * 1.3f,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing(focused)
            }
        }
    }
}

/** Round icon badge that turns dark on a focused row. */
@Composable
fun TvIconBadge(icon: ImageVector, focused: Boolean, modifier: Modifier = Modifier) {
    val badge = if (focused) {
        lerp(MaterialTheme.colorScheme.primary, Color.Black, 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(badge),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (focused) tvFocusedContainer() else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun TvSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
) {
    TvRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onFocused = onFocused,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        onClick = { onCheckedChange(!checked) },
    )
}

@Composable
fun TvRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
) {
    TvRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        onFocused = onFocused,
        trailing = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** A pill button: dark until focused, then filled with [tvFocusedContainer]. */
@Composable
fun TvPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    /** The main action of a screen: filled with the brand color even when not focused. */
    primary: Boolean = false,
) {
    Button(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ButtonDefaults.shape(shape = TvKit.PillShape),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) TvColors.Teal.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            contentColor = if (danger) TvColors.Danger else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = if (danger) TvColors.Danger else tvFocusedContainer(),
            focusedContentColor = if (danger) Color(0xFF410002) else tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = if (icon != null) 26.dp else 34.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** One action shown by [TvActionRow]. */
data class TvAction(
    val text: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val danger: Boolean = false,
)

/**
 * A horizontal row of pill buttons. The default action (or the first enabled one) takes focus when
 * the set of actions changes. Re-entering the row from elsewhere returns to the button used last.
 */
@Composable
fun TvActionRow(actions: List<TvAction>, modifier: Modifier = Modifier, requestInitialFocus: Boolean = true) {
    val defaultIndex = actions.indexOfFirst { it.isDefault && it.enabled }
        .takeIf { it >= 0 } ?: actions.indexOfFirst { it.enabled }
    val requesters = remember(actions.size) { List(actions.size) { FocusRequester() } }
    var lastFocused by remember { mutableIntStateOf(-1) }
    val barFocused = LocalTvBarFocused.current
    LaunchedEffect(actions.map { it.text }, actions.any { it.enabled }) {
        if (requestInitialFocus && defaultIndex >= 0 && !barFocused.value) {
            runCatching { requesters[defaultIndex].requestFocus() }
        }
    }
    Row(
        modifier = modifier
            .focusProperties {
                onEnter = {
                    val target = lastFocused.takeIf { it in actions.indices && actions[it].enabled } ?: defaultIndex
                    if (target >= 0) requesters[target].requestFocus()
                }
            }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        actions.forEachIndexed { index, action ->
            TvPillButton(
                text = action.text,
                onClick = action.onClick,
                icon = action.icon,
                enabled = action.enabled,
                danger = action.danger,
                modifier = Modifier
                    .focusRequester(requesters[index])
                    .onFocusChanged { if (it.isFocused) lastFocused = index },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cards and chips
// ---------------------------------------------------------------------------------------------

/**
 * A focusable card for dashboards and grids. Focus lifts it, adds a light border and brightens the
 * fill, like the rows of tiles on the Google TV home screen.
 */
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFocused: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TvKit.CardShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = lerp(MaterialTheme.colorScheme.surface, tvFocusedContainer(), 0.20f),
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
            pressedContainerColor = lerp(MaterialTheme.colorScheme.surface, tvFocusedContainer(), 0.28f),
            pressedContentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(4.dp, tvFocusedContainer())),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        interactionSource = interactionSource,
    ) {
        content(focused)
    }
}

/**
 * A small, non-interactive status label: a colored dot and text with no pill chrome, so it is never
 * mistaken for a button. [filled] draws a compact rounded badge instead, for the one thing that
 * needs attention (for example "Update available").
 */
@Composable
fun TvChip(text: String, modifier: Modifier = Modifier, color: Color = TvColors.Teal, filled: Boolean = false) {
    if (filled) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C1300), maxLines = 1)
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Long text
// ---------------------------------------------------------------------------------------------

/**
 * Long text that the remote can scroll: the block takes focus, Up/Down scroll it, and at either end
 * the key is released so focus can move on.
 */
@Composable
fun TvScrollableText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    monospace: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (scrollState.canScrollForward) Modifier.fadingBottomEdge() else Modifier)
            .background(if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> (scrollState.value < scrollState.maxValue).also { canScroll ->
                        if (canScroll) scope.launch { scrollState.animateScrollBy(160f) }
                    }

                    Key.DirectionUp -> (scrollState.value > 0).also { canScroll ->
                        if (canScroll) scope.launch { scrollState.animateScrollBy(-160f) }
                    }

                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .verticalScroll(scrollState)
            .padding(20.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = if (monospace) 14.sp else 16.sp,
            lineHeight = if (monospace) 20.sp else 24.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Focus helpers
// ---------------------------------------------------------------------------------------------

/**
 * True while the tab bar has focus. A screen that appears because the user is scrubbing across the
 * tabs must not pull focus away from the bar, so initial-focus helpers check this first.
 */
val LocalTvBarFocused = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> {
    androidx.compose.runtime.mutableStateOf(false)
}

/** A [FocusRequester] that takes focus once the content is laid out (unless the tab bar has it). */
@Composable
fun rememberInitialFocus(vararg keys: Any?): FocusRequester {
    val requester = remember { FocusRequester() }
    val barFocused = LocalTvBarFocused.current
    LaunchedEffect(*keys) {
        withFrameNanos { }
        if (!barFocused.value) runCatching { requester.requestFocus() }
    }
    return requester
}

/**
 * Scroll position and focused row of a list. Coming back to the list (from a detail screen or
 * another tab) scrolls to where it was and focuses the row that had focus. A requester on a row
 * that is not composed yet fails silently, so [restore] retries across a few frames.
 */
@Stable
class TvListFocus internal constructor(
    val listState: LazyListState,
    private val savedKey: androidx.compose.runtime.MutableState<String?>,
) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /** Attach to each focusable row of the list. */
    fun row(key: String): Modifier = Modifier
        .focusRequester(requesters.getOrPut(key) { FocusRequester() })
        .onFocusChanged { if (it.hasFocus) savedKey.value = key }

    /** Returns true when the remembered row was focused. */
    suspend fun restore(): Boolean {
        val key = savedKey.value ?: return false
        repeat(8) {
            withFrameNanos { }
            val requester = requesters[key]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) return true
        }
        return false
    }
}

@Composable
fun rememberTvListFocus(): TvListFocus {
    val saved = rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val state = rememberLazyListState()
    return remember(state, saved) { TvListFocus(state, saved) }
}

/** Fades the last 28dp of a clipped block, so text is never cut off mid-line at a hard edge. */
fun Modifier.fadingBottomEdge(height: androidx.compose.ui.unit.Dp = 28.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - h,
                endY = size.height,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
            size = androidx.compose.ui.geometry.Size(size.width, h),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
