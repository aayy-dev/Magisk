package com.topjohnwu.magisk.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A modal question in the style of Google TV's confirmation screens: a title, an optional message
 * and a vertical list of full-width pill actions. Focus stays inside; Back dismisses it.
 *
 * The first enabled action marked [TvAction.isDefault] (or just the first one) is focused, so a
 * destructive action is never the default.
 */
@Composable
fun TvDialog(
    title: String,
    actions: List<TvAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .width(480.dp)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
                Spacer(Modifier.height(22.dp))
                TvDialogActions(actions)
            }
        }
    }
}

@Composable
private fun TvDialogActions(actions: List<TvAction>) {
    val defaultIndex = actions.indexOfFirst { it.isDefault && it.enabled }
        .takeIf { it >= 0 } ?: actions.indexOfFirst { it.enabled }
    val requesters = remember(actions.size) { List(actions.size) { FocusRequester() } }
    LaunchedEffect(actions.size) {
        withFrameNanos { }
        if (defaultIndex >= 0) runCatching { requesters[defaultIndex].requestFocus() }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEachIndexed { index, action ->
            TvPillButton(
                text = action.text,
                onClick = action.onClick,
                icon = action.icon,
                enabled = action.enabled,
                danger = action.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requesters[index]),
            )
        }
    }
}
