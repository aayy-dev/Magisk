package com.topjohnwu.magisk.tv.surequest

import android.graphics.drawable.Drawable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.ui.MagiskTvTheme
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvPillButton
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import com.topjohnwu.magisk.tv.ui.tvFocusedContent
import kotlinx.coroutines.flow.StateFlow
import com.topjohnwu.magisk.core.R as CoreR

/**
 * The root-access question, drawn the way Google TV draws permission prompts: a dark scrim over
 * whatever is behind and a centered card. Deny holds the initial focus, so an unattended or
 * accidental OK never grants root.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSuRequestPrompt(
    icon: Drawable,
    title: String,
    packageName: String,
    totalSeconds: Int,
    state: StateFlow<TvSuState>,
    onSelect: (Int) -> Unit,
    onInteract: () -> Unit,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
) {
    MagiskTvTheme {
        val s by state.collectAsState()
        val durations = stringArrayResource(CoreR.array.allow_timeout)
        val iconBitmap = remember(icon) { icon.toBitmap(width = 192, height = 192).asImageBitmap() }
        val denyFocus = remember { FocusRequester() }
        // The prompt can pop up over an app while the user is still pressing keys for it. For a
        // moment after it appears every key is swallowed, and so are held-key repeats, so a stray
        // press can never move to or press Grant.
        val armedAt = remember { SystemClock.uptimeMillis() + 700L }

        // A system Back is a refusal, as with the phone dialog
        BackHandler(onBack = onDeny)
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { denyFocus.requestFocus() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .onPreviewKeyEvent { event ->
                    SystemClock.uptimeMillis() < armedAt ||
                        (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount > 0)
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    // Same tapjacking guard as the phone dialog: drop touches through an overlay
                    .pointerInteropFilter { event ->
                        Config.suTapjack && (
                            event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0 ||
                                event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
                            )
                    }
                    .padding(horizontal = 36.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                    Spacer(Modifier.width(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(CoreR.string.su_request_title).uppercase(),
                            fontSize = 12.sp,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Medium,
                            color = TvColors.Teal,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // One line of plain text: an app label with newlines must not be able to draw
                            // a second, fake line (such as another package name) in the prompt
                            text = stringResource(R.string.tv_sur_requesting, title.filterNot { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }),
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = packageName,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s.seconds > 0) {
                        Spacer(Modifier.width(18.dp))
                        CountdownRing(seconds = s.seconds, total = totalSeconds)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(CoreR.string.su_warning),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth(),
                    color = TvColors.Yellow,
                )

                Spacer(Modifier.height(20.dp))
                DurationRow(
                    label = durations.getOrElse(s.selected) { "" },
                    onClick = {
                        // Choosing a duration means the user is deciding: stop the countdown
                        onInteract()
                        onSelect((s.selected + 1) % durations.size)
                    },
                )

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TvPillButton(
                        text = stringResource(CoreR.string.deny),
                        onClick = onDeny,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(denyFocus),
                    )
                    TvPillButton(
                        text = stringResource(CoreR.string.grant),
                        onClick = onGrant,
                        enabled = s.grantEnabled,
                        danger = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "Allow for: Forever": OK moves to the next duration, wrapping around. */
@Composable
private fun DurationRow(label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(TvKit.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = tvFocusedContainer(),
            focusedContentColor = tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.tv_sur_allow_for),
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Text(text = label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "  ›",
                fontSize = 20.sp,
                color = if (focused) tvFocusedContent() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountdownRing(seconds: Int, total: Int) {
    val progress = if (total > 0) (seconds.toFloat() / total).coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            drawArc(TvColors.Teal, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Text(text = seconds.toString(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
