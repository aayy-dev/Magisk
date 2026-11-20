package com.topjohnwu.magisk.tv.logs

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.ktx.timeDateFormat
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.model.su.SuLog
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.flash.ConsoleView
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvActionRow
import com.topjohnwu.magisk.tv.ui.TvChip
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvDialog
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.tv.ui.rememberTvListFocus
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import com.topjohnwu.magisk.tv.ui.tvFocusedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.magisk.core.R as CoreR

private const val SECTION_SU = 0
private const val SECTION_MAGISK = 1

/**
 * Superuser log (a list with details of the focused entry) and the Magisk log (a scrolling console),
 * switched with a pill row at the top. Opens without grabbing focus so the tab bar above stays usable.
 */
@Composable
fun LogsScreen(viewModel: LogsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = rememberTvActivity()
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(SECTION_SU) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvKit.SafeHorizontal)
            .padding(top = 4.dp, bottom = TvKit.SafeVertical),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SegmentPill(
                    text = stringResource(R.string.tv_log_section_su),
                    selected = section == SECTION_SU,
                    onSelect = { section = SECTION_SU },
                )
                SegmentPill(
                    text = stringResource(R.string.tv_log_section_magisk),
                    selected = section == SECTION_MAGISK,
                    onSelect = { section = SECTION_MAGISK },
                )
            }
            Spacer(Modifier.weight(1f))
            TvActionRow(
                requestInitialFocus = false,
                actions = buildList {
                    add(
                        TvAction(
                            text = stringResource(R.string.tv_log_refresh),
                            icon = TvIcons.Restart,
                            onClick = viewModel::load,
                        ),
                    )
                    if (section == SECTION_MAGISK) {
                        add(
                            TvAction(
                                text = stringResource(R.string.tv_log_save),
                                icon = TvIcons.Save,
                                onClick = {
                                    activity.withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) { granted ->
                                        if (!granted) return@withPermission
                                        scope.launch {
                                            val saved = viewModel.saveMagiskLog()
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
                    }
                    add(
                        TvAction(
                            text = stringResource(R.string.tv_log_clear),
                            icon = TvIcons.Delete,
                            danger = true,
                            onClick = { confirmClear = true },
                        ),
                    )
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Centered(stringResource(CoreR.string.loading))

                section == SECTION_SU -> SuLogPane(state.suLogs)

                else -> MagiskLogPane(state)
            }
        }
    }

    if (confirmClear) {
        TvDialog(
            title = stringResource(R.string.tv_log_clear_title),
            message = stringResource(
                if (section == SECTION_SU) R.string.tv_log_clear_su_msg else R.string.tv_log_clear_magisk_msg,
            ),
            onDismiss = { confirmClear = false },
            actions = listOf(
                TvAction(
                    text = stringResource(android.R.string.cancel),
                    isDefault = true,
                    onClick = { confirmClear = false },
                ),
                TvAction(
                    text = stringResource(R.string.tv_log_clear),
                    danger = true,
                    onClick = {
                        confirmClear = false
                        if (section == SECTION_SU) viewModel.clearSuLog() else viewModel.clearMagiskLog()
                    },
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Superuser log
// ---------------------------------------------------------------------------------------------

@Composable
private fun SuLogPane(logs: List<SuLog>) {
    if (logs.isEmpty()) {
        Centered(stringResource(CoreR.string.log_data_none))
        return
    }
    val focus = rememberTvListFocus()
    var shown by remember { mutableStateOf<SuLog?>(null) }
    val selected = shown?.takeIf { it in logs } ?: logs.first()

    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            state = focus.listState,
            modifier = Modifier
                .weight(0.52f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(logs, key = { it.id }) { log ->
                val allowed = log.action >= 2
                TvRow(
                    title = log.appName,
                    summary = log.time.toDateString(),
                    leading = { SuAppIcon(log.packageName, 44.dp) },
                    trailing = {
                        TvChip(
                            text = stringResource(if (allowed) R.string.tv_log_allowed else R.string.tv_log_denied),
                            color = if (allowed) TvColors.Good else TvColors.Danger,
                        )
                    },
                    onFocused = { shown = log },
                    modifier = focus.row(log.id.toString()),
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        SuLogDetails(selected, Modifier.weight(0.48f))
    }
}

@Composable
private fun SuLogDetails(log: SuLog, modifier: Modifier = Modifier) {
    val allowed = log.action >= 2
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(TvKit.CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuAppIcon(log.packageName, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = log.appName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = log.packageName,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TvChip(
            text = stringResource(if (allowed) R.string.tv_log_allowed else R.string.tv_log_denied),
            color = if (allowed) TvColors.Good else TvColors.Danger,
        )
        DetailLine(log.time.toDateString())
        val target = buildString {
            append(stringResource(CoreR.string.target_uid, log.toUid))
            append("   ")
            append(stringResource(CoreR.string.pid, log.fromPid))
            if (log.target != -1) {
                append("   ")
                append(stringResource(CoreR.string.target_pid, if (log.target == 0) "magiskd" else log.target.toString()))
            }
        }
        DetailLine(target)
        if (log.context.isNotEmpty()) DetailLine(stringResource(CoreR.string.selinux_context, log.context))
        if (log.gids.isNotEmpty()) DetailLine(stringResource(CoreR.string.supp_group, log.gids))
        Text(
            text = stringResource(R.string.tv_log_su_command).uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = log.command,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Long.toDateString(): String = toTime(timeDateFormat)

/** App icon on a rounded tile; shown blank-ish when the package is gone or is a bare UID. */
@Composable
private fun SuAppIcon(packageName: String, size: Dp) {
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                AppContext.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(TvIcons.Shield, contentDescription = null, modifier = Modifier.size(size * 0.5f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Magisk log
// ---------------------------------------------------------------------------------------------

@Composable
private fun MagiskLogPane(state: LogsViewModel.State) {
    Column(Modifier.fillMaxSize()) {
        ConsoleView(
            lines = state.magiskLines,
            emptyText = stringResource(CoreR.string.log_data_magisk_none),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        if (state.magiskTruncated) {
            Text(
                text = stringResource(R.string.tv_log_truncated, state.magiskLines.size, state.magiskTotalLines),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A selectable pill; it becomes the current section as soon as it takes focus. */
@Composable
private fun SegmentPill(text: String, selected: Boolean, onSelect: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onSelect() }
    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(TvKit.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = tvFocusedContainer(),
            focusedContentColor = tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        interactionSource = interactionSource,
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            maxLines = 1,
        )
    }
}
