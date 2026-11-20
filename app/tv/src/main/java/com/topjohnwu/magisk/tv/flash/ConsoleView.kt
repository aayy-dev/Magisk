package com.topjohnwu.magisk.tv.flash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import kotlinx.coroutines.launch

/**
 * A read-only monospace console for the remote: Up/Down scroll it, and at either end the key is
 * released so focus can move on to the buttons around it.
 *
 * While [followTail] is on, the view keeps the newest line visible as lines arrive. Scrolling up
 * pauses that, and scrolling back to the end resumes it, as in a terminal.
 */
@Composable
internal fun ConsoleView(
    lines: List<String>,
    modifier: Modifier = Modifier,
    followTail: Boolean = true,
    focusRequester: FocusRequester? = null,
    emptyText: String = "",
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var following by remember { mutableStateOf(followTail) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    // Keep the newest line in view while following
    LaunchedEffect(lines.size, following) {
        if (following && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    // Reaching the end again resumes following
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { if (!it) following = true }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.09f else 0.05f))
            .then(if (focused) Modifier.border(3.dp, tvFocusedContainer(), shape) else Modifier)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val page = listState.layoutInfo.viewportSize.height * 0.45f
                when (event.key) {
                    Key.DirectionUp -> listState.canScrollBackward.also { canScroll ->
                        if (canScroll) {
                            following = false
                            scope.launch { listState.animateScrollBy(-page) }
                        }
                    }

                    Key.DirectionDown -> listState.canScrollForward.also { canScroll ->
                        if (canScroll) scope.launch { listState.animateScrollBy(page) }
                    }

                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = emptyText,
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                userScrollEnabled = false,
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = if (line.startsWith("!")) TvColors.Danger else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
