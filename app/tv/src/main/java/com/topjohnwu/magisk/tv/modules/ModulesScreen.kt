package com.topjohnwu.magisk.tv.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.displayName
import com.topjohnwu.magisk.tv.FlashMode
import com.topjohnwu.magisk.tv.LocalTvNavigator
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.magisk.tv.modules.ModulesViewModel.Item
import com.topjohnwu.magisk.tv.modules.ModulesViewModel.Notice
import com.topjohnwu.magisk.tv.ui.LocalTvRowResting
import com.topjohnwu.magisk.tv.ui.LocalTvBarFocused
import com.topjohnwu.magisk.tv.ui.LocalTvRowMinHeight
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvChip
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvDialog
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvPillButton
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.TvScrollableText
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.TvSwitchRow
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.tv.ui.rememberTvListFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.topjohnwu.magisk.core.R as CoreR

private const val INSTALL_KEY = "install"

/** A module the user picked or that is being updated, waiting for confirmation. */
private class PendingZip(val uri: android.net.Uri, val name: String)

/**
 * Installed modules: the list on the left (starting with "Install from storage"), and for the
 * focused module its controls on the right. Right on a row moves into them, Left comes back.
 */
@Composable
fun ModulesScreen(viewModel: ModulesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalTvNavigator.current
    val activity = rememberTvActivity()
    val scope = rememberCoroutineScope()
    val listFocus = rememberTvListFocus()
    val listRequester = remember { FocusRequester() }
    val detailRequester = remember { FocusRequester() }

    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var pendingZip by remember { mutableStateOf<PendingZip?>(null) }
    var updating by remember { mutableStateOf<Pair<Item, OnlineModule>?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(pendingKey) {
        val key = pendingKey ?: return@LaunchedEffect
        delay(90)
        shownKey = key
    }

    // A zip chosen in the file picker waits for confirmation, as in the phone UI
    val picked by ModuleZipPicker.picked.collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        val uri = picked ?: return@LaunchedEffect
        ModuleZipPicker.picked.value = null
        val name = runCatching { uri.displayName }.getOrNull() ?: return@LaunchedEffect
        pendingZip = PendingZip(uri, name)
    }

    val barFocused = LocalTvBarFocused.current
    LaunchedEffect(state.available) {
        // Scrubbing across the tab bar must not pull focus into the screen that appears
        if (barFocused.value) return@LaunchedEffect
        if (!state.available) return@LaunchedEffect
        if (!listFocus.restore()) {
            withFrameNanos { }
            runCatching { listRequester.requestFocus() }
        }
    }

    if (!state.available) {
        EmptyState(
            title = if (state.loading) stringResource(CoreR.string.loading) else stringResource(R.string.tv_mod_unavailable),
            hint = if (state.loading) null else stringResource(R.string.tv_mod_unavailable_hint),
        )
    } else {
        val selected = state.items.firstOrNull { it.key == shownKey }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = TvKit.SafeHorizontal),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .focusRequester(listRequester),
                state = listFocus.listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = TvKit.SafeVertical),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = INSTALL_KEY) {
                    TvRow(
                        title = stringResource(CoreR.string.module_action_install_external),
                        icon = TvIcons.ModuleStorage,
                        selected = shownKey == INSTALL_KEY,
                        modifier = listFocus.row(INSTALL_KEY),
                        onFocused = { pendingKey = INSTALL_KEY },
                        onClick = { activity.getContent("application/zip", ModuleZipCallback()) },
                    )
                }
                if (state.items.isEmpty() && !state.loading) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(CoreR.string.module_empty),
                            modifier = Modifier.padding(start = 26.dp, top = 22.dp),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.items, key = { it.key }) { module ->
                    ModuleRow(
                        item = module,
                        selected = module.key == shownKey,
                        modifier = listFocus.row(module.key).onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionRight) {
                                if (event.type == KeyEventType.KeyDown) {
                                    shownKey = module.key
                                    runCatching { detailRequester.requestFocus() }
                                }
                                true
                            } else {
                                false
                            }
                        },
                        onFocused = { pendingKey = module.key },
                    )
                }
            }
            Spacer(Modifier.width(28.dp))
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionLeft) {
                            if (event.type == KeyEventType.KeyDown) scope.launch { listFocus.restore() }
                            true
                        } else {
                            false
                        }
                    },
            ) {
                if (selected == null) {
                    InstallHint()
                } else {
                    CompositionLocalProvider(
                        LocalTvRowMinHeight provides 52.dp,
                        LocalTvRowResting provides true,
                    ) {
DetailPanel(
                        item = selected,
                        entryRequester = detailRequester,
                        onEnabled = { viewModel.setEnabled(selected, it) },
                        onRemoved = { viewModel.setRemoved(selected, it) },
                        onAction = {
                            navigator.push(TvRoute.Flash(FlashMode.ModuleAction, id = selected.id, label = selected.name))
                        },
                        onUpdate = { viewModel.onlineModule(selected)?.let { updating = selected to it } },
                    )
}
                }
            }
        }
    }

    pendingZip?.let { zip ->
        TvDialog(
            title = stringResource(CoreR.string.confirm_install_title),
            message = stringResource(CoreR.string.confirm_install, zip.name),
            onDismiss = { pendingZip = null },
            actions = listOf(
                TvAction(
                    text = stringResource(android.R.string.ok),
                    isDefault = true,
                    onClick = {
                        pendingZip = null
                        navigator.push(TvRoute.Flash(FlashMode.ModuleZip, uri = zip.uri.toString(), label = zip.name))
                    },
                ),
                TvAction(stringResource(android.R.string.cancel), onClick = { pendingZip = null }),
            ),
        )
    }

    updating?.let { (item, online) ->
        val loadingText = stringResource(R.string.tv_mod_changelog_loading)
        var changelog by remember(online) { mutableStateOf<String?>(null) }
        LaunchedEffect(online) { changelog = runCatching { viewModel.changelog(online) }.getOrDefault("") }

        TvDialog(
            title = stringResource(CoreR.string.repo_install_title, online.name, online.version, online.versionCode),
            onDismiss = { updating = null },
            content = {
                Text(
                    text = changelog ?: loadingText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            actions = listOf(
                TvAction(
                    text = stringResource(CoreR.string.download),
                    isDefault = true,
                    onClick = {
                        updating = null
                        DownloadEngine.startWithActivity(activity, ModuleDownload(online, false))
                    },
                ),
                TvAction(stringResource(android.R.string.cancel), onClick = { updating = null }),
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModuleRow(item: Item, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val summary = stringResource(CoreR.string.module_version_author, item.version, item.author)
    TvRow(
        title = item.name,
        summary = summary,
        icon = TvIcons.Extension,
        selected = selected,
        modifier = modifier,
        onFocused = onFocused,
        trailing = { focused -> StatusChip(item, filled = focused) },
        onClick = {},
    )
}

/** The most relevant state of a module as one chip: what needs attention comes first. */
@Composable
private fun StatusChip(item: Item, filled: Boolean) {
    when {
        item.removed -> TvChip(stringResource(R.string.tv_mod_state_removed), color = TvColors.Danger, filled = filled)
        item.updated -> TvChip(stringResource(R.string.tv_mod_state_updated), color = TvColors.Yellow, filled = filled)
        item.notice != null -> TvChip(stringResource(R.string.tv_mod_state_suspended), color = TvColors.Danger, filled = filled)
        !item.enabled -> TvChip(stringResource(R.string.tv_mod_state_disabled), color = TvColors.Teal, filled = filled)
        item.updateReady -> TvChip(stringResource(CoreR.string.update), color = TvColors.Yellow, filled = filled)
        else -> TvChip(stringResource(R.string.tv_mod_state_enabled), color = TvColors.Good)
    }
}

// ---------------------------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailPanel(
    item: Item,
    entryRequester: FocusRequester,
    onEnabled: (Boolean) -> Unit,
    onRemoved: (Boolean) -> Unit,
    onAction: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = TvKit.SafeVertical),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.name,
                fontSize = 28.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(CoreR.string.module_version_author, item.version, item.author),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (item.removed) TvChip(stringResource(R.string.tv_mod_state_removed), color = TvColors.Danger)
                if (item.updated) TvChip(stringResource(R.string.tv_mod_state_updated), color = TvColors.Yellow)
                if (!item.enabled) TvChip(stringResource(R.string.tv_mod_state_disabled), color = TvColors.Teal)
                if (item.updateReady) TvChip(stringResource(CoreR.string.update_available), color = TvColors.Yellow, filled = true)
            }
            item.notice?.let {
                Text(
                    text = noticeText(it),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = TvColors.Danger,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (item.removed || item.updated) {
                Text(
                    text = stringResource(CoreR.string.reboot_apply_change),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        TvScrollableText(
            text = item.description.ifBlank { stringResource(CoreR.string.no_info_provided) },
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
        )

        TvSectionHeader(stringResource(CoreR.string.modules))
        TvSwitchRow(
            title = stringResource(R.string.tv_mod_enabled),
            checked = item.enabled,
            enabled = !item.removed,
            onCheckedChange = onEnabled,
            // A module that is marked for removal cannot be toggled, so entry goes to Restore instead
            modifier = if (item.removed) Modifier else Modifier.focusRequester(entryRequester),
        )
        if (item.showAction) {
            TvRow(
                title = stringResource(CoreR.string.module_action),
                icon = TvIcons.Action,
                onClick = onAction,
            )
        }
        if (item.hasUpdateInfo) {
            TvRow(
                title = stringResource(CoreR.string.update),
                icon = TvIcons.Download,
                enabled = item.updateReady,
                onClick = onUpdate,
            )
        }

        Spacer(Modifier.height(14.dp))
        TvPillButton(
            text = stringResource(if (item.removed) CoreR.string.module_state_restore else CoreR.string.module_state_remove),
            icon = if (item.removed) TvIcons.Restart else TvIcons.Delete,
            danger = !item.removed,
            onClick = { onRemoved(!item.removed) },
            modifier = Modifier
                .padding(start = 12.dp)
                .then(if (item.removed) Modifier.focusRequester(entryRequester) else Modifier),
        )
    }
}

@Composable
private fun noticeText(notice: Notice): String = when (notice) {
    Notice.ZygiskUnloaded -> stringResource(CoreR.string.zygisk_module_unloaded)
    Notice.NeedsZygiskOff -> stringResource(CoreR.string.suspend_text_riru, stringResource(CoreR.string.zygisk))
    Notice.NeedsZygiskOn -> stringResource(CoreR.string.suspend_text_zygisk, stringResource(CoreR.string.zygisk))
}

@Composable
private fun InstallHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 8.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(CoreR.string.module_action_install_external),
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.tv_mod_install_hint),
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.tv_mod_install_ok),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = TvColors.Teal,
        )
    }
}

@Composable
private fun EmptyState(title: String, hint: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = TvKit.SafeHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
