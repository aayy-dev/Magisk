package com.topjohnwu.magisk.tv.flash

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.ktx.reboot
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.tv.FlashMode
import com.topjohnwu.magisk.tv.LocalTvNavigator
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.magisk.tv.flash.FlashViewModel.State
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvActionRow
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.rememberInitialFocus
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import kotlinx.coroutines.launch
import java.util.UUID
import com.topjohnwu.magisk.core.R as CoreR

/**
 * Console of an install, uninstall, module install or module action. The operation starts once, when
 * the screen opens. Back is ignored while it runs, as in the phone UI, so a flash is never cut off.
 */
@Composable
fun FlashScreen(route: TvRoute.Flash) {
    val navigator = LocalTvNavigator.current
    val context = LocalContext.current
    val activity = rememberTvActivity()
    val scope = rememberCoroutineScope()

    // A fresh view model per opening, so opening the same screen twice runs twice
    val runId = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel: FlashViewModel = viewModel(key = "flash-$runId")
    LaunchedEffect(viewModel) { viewModel.start(route) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val flashing = state == State.FLASHING
    val offerReboot = state == State.SUCCESS && viewModel.showReboot

    BackHandler(enabled = flashing) {
        context.toast(R.string.tv_flash_back_blocked, Toast.LENGTH_SHORT)
    }

    val consoleFocus = rememberInitialFocus()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvKit.SafeHorizontal, vertical = TvKit.SafeVertical),
    ) {
        Header(route = route, state = state)
        Spacer(Modifier.height(18.dp))
        ConsoleView(
            lines = viewModel.lines,
            focusRequester = consoleFocus,
            emptyText = stringResource(R.string.tv_flash_empty),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        TvActionRow(
            actions = buildList {
                if (offerReboot) {
                    add(
                        TvAction(
                            text = stringResource(CoreR.string.reboot),
                            icon = TvIcons.Restart,
                            isDefault = true,
                            onClick = { reboot() },
                        ),
                    )
                }
                add(
                    TvAction(
                        text = stringResource(R.string.tv_flash_save),
                        icon = TvIcons.Save,
                        onClick = {
                            activity.withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
                                if (!granted) return@withPermission
                                scope.launch {
                                    val saved = viewModel.saveLog()
                                    context.toast(
                                        if (saved != null) {
                                            context.getString(R.string.tv_log_saved, saved)
                                        } else {
                                            context.getString(R.string.tv_log_save_failed)
                                        },
                                        Toast.LENGTH_LONG,
                                    )
                                }
                            }
                        },
                    ),
                )
                add(
                    TvAction(
                        text = stringResource(CoreR.string.close),
                        icon = TvIcons.Close,
                        enabled = !flashing,
                        isDefault = !offerReboot,
                        onClick = { navigator.pop() },
                    ),
                )
            },
            // While running the console keeps focus; when it ends the primary action takes over
            requestInitialFocus = !flashing,
        )
    }
}

@Composable
private fun Header(route: TvRoute.Flash, state: State) {
    val title = when (route.mode) {
        FlashMode.Uninstall -> stringResource(CoreR.string.uninstall_magisk_title)
        FlashMode.ModuleZip -> route.label ?: stringResource(R.string.tv_flash_module)
        FlashMode.ModuleAction -> route.label ?: stringResource(R.string.tv_flash_action)
        else -> stringResource(CoreR.string.flash_screen_title)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    when (state) {
                        State.FLASHING -> CoreR.string.flashing
                        State.SUCCESS -> CoreR.string.done
                        State.FAILED -> CoreR.string.failure
                    },
                ),
                fontSize = 17.sp,
                color = when (state) {
                    State.FLASHING -> MaterialTheme.colorScheme.onSurfaceVariant
                    State.SUCCESS -> TvColors.Good
                    State.FAILED -> TvColors.Danger
                },
            )
        }
        if (state == State.FLASHING) {
            IndeterminateBar(Modifier.width(220.dp))
        }
    }
}

/** A looping progress bar; TV Material has no progress indicator. */
@Composable
private fun IndeterminateBar(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "flash_progress")
    val bias by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash_progress_bias",
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(TvKit.PillShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.34f)
                .align(BiasAlignment(bias, 0f))
                .clip(TvKit.PillShape)
                .background(TvColors.Teal),
        )
    }
}
