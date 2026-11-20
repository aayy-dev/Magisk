package com.topjohnwu.magisk.tv.denylist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.tv.material3.LocalContentColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.settings.openOnRight
import com.topjohnwu.magisk.tv.ui.TvChip
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvPageTitle
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.TvSwitchRow
import com.topjohnwu.magisk.tv.ui.rememberInitialFocus
import com.topjohnwu.magisk.tv.ui.rememberTvListFocus
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import com.topjohnwu.magisk.tv.ui.tvFocusedContent
import com.topjohnwu.magisk.core.R as CoreR

/**
 * Full-screen DenyList editor: the enforce switch and the app filters on top, then the apps. OK (or
 * Right) on an app opens its processes, each a switch that applies immediately.
 */
@Composable
fun DenyListScreen() {
    val viewModel: DenyListViewModel = viewModel()
    val focus = rememberTvListFocus()
    var openPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val openApp = openPackage?.let { pkg -> viewModel.apps.firstOrNull { it.packageName == pkg } }
    val enforceRequester = rememberInitialFocus()

    // The shell's Back pops the screen; while a process page is open this closes it instead
    BackHandler(enabled = openApp != null) { openPackage = null }

    var leftList by remember { mutableStateOf(false) }
    LaunchedEffect(openApp == null) {
        if (openApp != null) {
            leftList = true
        } else if (leftList && !focus.restore()) {
            // The remembered app may have dropped out of the filtered list
            runCatching { enforceRequester.requestFocus() }
        }
    }

    if (openApp != null) {
        ProcessPage(openApp, onClose = { openPackage = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvKit.SafeHorizontal),
    ) {
        Spacer(Modifier.height(TvKit.SafeVertical))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvPageTitle(stringResource(CoreR.string.denylist))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToggleChip(
                    text = stringResource(CoreR.string.settings_denylist_title),
                    checked = viewModel.enforced,
                    onToggle = { viewModel.applyEnforced(!viewModel.enforced) },
                    modifier = Modifier.focusRequester(enforceRequester),
                )
                ToggleChip(
                    text = stringResource(CoreR.string.show_system_app),
                    checked = viewModel.showSystem,
                    onToggle = { viewModel.showSystem = !viewModel.showSystem },
                )
                ToggleChip(
                    text = stringResource(CoreR.string.show_os_app),
                    checked = viewModel.showOS && viewModel.showSystem,
                    enabled = viewModel.showSystem,
                    onToggle = { viewModel.showOS = !viewModel.showOS },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val visible = viewModel.visibleApps()
        when {
            viewModel.loading -> CenteredText(stringResource(CoreR.string.loading))
            visible.isEmpty() -> CenteredText(stringResource(R.string.tv_deny_empty))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = focus.listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = TvKit.SafeVertical + 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    val open = { openPackage = app.packageName }
                    TvRow(
                        title = app.label,
                        summary = app.packageName,
                        leading = { AppIcon(app) },
                        modifier = focus.row(app.packageName).openOnRight(true, open),
                        trailing = { focused -> CountLabel(app, focused) },
                        onClick = open,
                    )
                }
            }
        }
    }
}

/** The processes of one app, each a switch; the first row switches the app's default processes. */
@Composable
private fun ProcessPage(app: DenyApp, onClose: () -> Unit) {
    val firstRow = rememberInitialFocus()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = TvKit.SafeHorizontal)
            .onKeyEvent { event ->
                // Left steps back to the app list, the mirror of Right opening it
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onClose()
                    true
                } else {
                    false
                }
            },
    ) {
        Spacer(Modifier.height(TvKit.SafeVertical))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, size = 56.dp)
            Spacer(Modifier.width(18.dp))
            Column {
                TvPageTitle(app.label)
                Text(app.packageName, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = TvKit.SafeVertical + 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "defaults") {
                TvSwitchRow(
                    title = stringResource(R.string.tv_deny_default_processes),
                    summary = stringResource(R.string.tv_deny_default_processes_summary),
                    checked = app.defaultsOn,
                    onCheckedChange = { app.setDefaults(it) },
                    modifier = Modifier.focusRequester(firstRow),
                )
            }
            item(key = "header") { TvSectionHeader(stringResource(R.string.tv_deny_processes)) }
            items(app.processes, key = { "${it.isIsolated}:${it.name}" }) { process ->
                TvSwitchRow(
                    title = process.displayName,
                    checked = process.enabled,
                    onCheckedChange = { process.set(it) },
                )
            }
        }
    }
}

@Composable
private fun CountLabel(app: DenyApp, focused: Boolean) {
    val count = app.checkedCount
    if (count == 0) return
    val text = stringResource(R.string.tv_deny_count, count, app.processes.size)
    if (focused) {
        // The chip colors are tuned for the dark row; on the pale focused row plain text reads better
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    } else {
        TvChip(text = text, color = TvColors.Teal)
    }
}

/** The app icon, decoded off the main thread; a neutral tile shows until it is ready. */
@Composable
private fun AppIcon(app: DenyApp, size: Dp = 44.dp) {
    val icon by produceState<ImageBitmap?>(initialValue = DenyIcons.cached(app), app.packageName) {
        if (value == null) value = DenyIcons.load(app)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

/** A pill that shows its state with a check; used for the enforce switch and the app filters. */
@Composable
private fun ToggleChip(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TvKit.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (checked) TvColors.Teal.copy(alpha = 0.26f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = tvFocusedContainer(),
            focusedContentColor = tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Explicit on/off mark: a hollow ring when off, a filled check when on
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .then(
                        if (checked) {
                            Modifier.background(TvColors.Teal, androidx.compose.foundation.shape.CircleShape)
                        } else {
                            Modifier.border(2.dp, LocalContentColor.current.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        TvIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = androidx.compose.ui.graphics.Color(0xFF00201C),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
