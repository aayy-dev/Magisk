package com.topjohnwu.magisk.tv.superuser

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.superuser.SuperuserViewModel.Item
import com.topjohnwu.magisk.tv.ui.LocalTvRowResting
import com.topjohnwu.magisk.tv.ui.LocalTvBarFocused
import com.topjohnwu.magisk.tv.ui.LocalTvRowMinHeight
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvChip
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvDialog
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvPillButton
import com.topjohnwu.magisk.tv.ui.TvRadioRow
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.TvSwitchRow
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.tv.ui.rememberTvListFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.topjohnwu.magisk.core.R as CoreR

/**
 * Apps that asked for root: the list on the left, and for the focused app its real settings on the
 * right. Right on a row moves into the settings, Left returns to the same row.
 */
@Composable
fun SuperuserScreen(viewModel: SuperuserViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = rememberTvActivity()
    val scope = rememberCoroutineScope()
    val listFocus = rememberTvListFocus()
    val listRequester = remember { FocusRequester() }
    val detailRequester = remember { FocusRequester() }

    // The row the detail panel shows. It follows focus after a short pause, so scrolling quickly
    // through a long list does not redraw the panel for every row on the way.
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingKey by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<Item?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect {
            context.toast(context.getString(it.res, it.arg), Toast.LENGTH_SHORT)
        }
    }
    LaunchedEffect(pendingKey) {
        val key = pendingKey ?: return@LaunchedEffect
        delay(90)
        shownKey = key
    }
    val barFocused = LocalTvBarFocused.current
    LaunchedEffect(state.items.isNotEmpty()) {
        // Scrubbing across the tab bar must not pull focus into the screen that appears
        if (barFocused.value) return@LaunchedEffect
        if (state.items.isEmpty()) return@LaunchedEffect
        if (!listFocus.restore()) {
            withFrameNanos { }
            runCatching { listRequester.requestFocus() }
        }
    }

    fun withAuth(action: () -> Unit) {
        if (Config.suAuth) activity.withAuthentication { if (it) action() } else action()
    }

    when {
        !state.available -> EmptyState(
            title = stringResource(R.string.tv_su_unavailable),
            hint = stringResource(R.string.tv_su_unavailable_hint),
        )

        state.items.isEmpty() -> EmptyState(
            title = if (state.loading) stringResource(CoreR.string.loading) else stringResource(CoreR.string.superuser_policy_none),
            hint = null,
        )

        else -> {
            val selected = state.items.firstOrNull { it.key == shownKey } ?: state.items.first()
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
                    items(state.items, key = { it.key }) { item ->
                        PolicyRow(
                            item = item,
                            selected = item.key == shownKey,
                            modifier = listFocus.row(item.key).onPreviewKeyEvent { event ->
                                if (event.key == Key.DirectionRight) {
                                    if (event.type == KeyEventType.KeyDown) {
                                        shownKey = item.key
                                        runCatching { detailRequester.requestFocus() }
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                            onFocused = { pendingKey = item.key },
                        )
                    }
                }
                Spacer(Modifier.width(28.dp))
                CompositionLocalProvider(
                    LocalTvRowMinHeight provides 52.dp,
                    LocalTvRowResting provides true,
                ) {
DetailPanel(
                    item = selected,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .onPreviewKeyEvent { event ->
                            // Left from any option goes back to the list row it belongs to
                            if (event.key == Key.DirectionLeft) {
                                if (event.type == KeyEventType.KeyDown) scope.launch { listFocus.restore() }
                                true
                            } else {
                                false
                            }
                        },
                    entryRequester = detailRequester,
                    onPolicy = { policy -> withAuth { viewModel.setPolicy(selected, policy) } },
                    onNotification = { viewModel.setNotification(selected, it) },
                    onLogging = { viewModel.setLogging(selected, it) },
                    onRevoke = { revoking = selected },
                )
}
            }
        }
    }

    revoking?.let { item ->
        TvDialog(
            title = stringResource(CoreR.string.su_revoke_title),
            message = stringResource(CoreR.string.su_revoke_msg, item.title),
            onDismiss = { revoking = null },
            actions = listOf(
                TvAction(
                    text = stringResource(android.R.string.cancel),
                    isDefault = true,
                    onClick = { revoking = null },
                ),
                TvAction(
                    text = stringResource(CoreR.string.superuser_toggle_revoke),
                    danger = true,
                    onClick = {
                        revoking = null
                        withAuth { viewModel.revoke(item) }
                    },
                ),
            ),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

@Composable
private fun PolicyRow(item: Item, selected: Boolean, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    TvRow(
        title = item.title,
        summary = item.packageName,
        selected = selected,
        modifier = modifier,
        leading = { AppIcon(item.icon, 44.dp) },
        onFocused = onFocused,
        trailing = { focused ->
            PolicyChip(item.policy, filled = focused)
        },
        onClick = {},
    )
}

/** State of a policy. Solid while the row is focused, so it stays readable on the light pill. */
@Composable
private fun PolicyChip(policy: Int, filled: Boolean) {
    when {
        policy == SuPolicy.RESTRICT -> TvChip(stringResource(R.string.tv_su_state_restricted), color = TvColors.Yellow, filled = filled)
        policy >= SuPolicy.ALLOW -> TvChip(stringResource(R.string.tv_su_state_allowed), color = TvColors.Good, filled = filled)
        else -> TvChip(stringResource(R.string.tv_su_state_denied), color = TvColors.Danger, filled = filled)
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Detail
// ---------------------------------------------------------------------------------------------

@Composable
private fun DetailPanel(
    item: Item,
    entryRequester: FocusRequester,
    onPolicy: (Int) -> Unit,
    onNotification: (Boolean) -> Unit,
    onLogging: (Boolean) -> Unit,
    onRevoke: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Entering the panel lands on the option that is currently selected
    val entryOn = when {
        item.policy == SuPolicy.RESTRICT -> SuPolicy.RESTRICT
        item.policy >= SuPolicy.ALLOW -> SuPolicy.ALLOW
        else -> SuPolicy.DENY
    }
    fun entry(policy: Int) = if (policy == entryOn) Modifier.focusRequester(entryRequester) else Modifier

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = TvKit.SafeVertical),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(item.icon, 80.dp)
            Spacer(Modifier.width(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.appName,
                    fontSize = 28.sp,
                    lineHeight = 33.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.packageName,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.isSharedUid) {
                    TvChip(stringResource(R.string.tv_su_shared_uid), color = TvColors.Yellow)
                }
            }
        }

        TvSectionHeader(stringResource(CoreR.string.superuser_access))
        TvRadioRow(
            title = stringResource(CoreR.string.deny),
            selected = item.policy < SuPolicy.ALLOW,
            onClick = { onPolicy(SuPolicy.DENY) },
            modifier = entry(SuPolicy.DENY),
        )
        TvRadioRow(
            title = stringResource(CoreR.string.grant),
            selected = item.policy == SuPolicy.ALLOW,
            onClick = { onPolicy(SuPolicy.ALLOW) },
            modifier = entry(SuPolicy.ALLOW),
        )
        // The restricted policy is only offered when restricting is enabled in the settings,
        // or the app already has it, as in the phone UI
        if (Config.suRestrict || item.policy == SuPolicy.RESTRICT) {
            TvRadioRow(
                title = stringResource(CoreR.string.restrict),
                selected = item.policy == SuPolicy.RESTRICT,
                onClick = { onPolicy(SuPolicy.RESTRICT) },
                modifier = entry(SuPolicy.RESTRICT),
            )
        }

        Spacer(Modifier.height(6.dp))
        TvSwitchRow(
            title = stringResource(CoreR.string.superuser_toggle_notification),
            checked = item.notification,
            onCheckedChange = onNotification,
        )
        TvSwitchRow(
            title = stringResource(R.string.tv_su_logging),
            checked = item.logging,
            onCheckedChange = onLogging,
        )

        Spacer(Modifier.height(18.dp))
        TvPillButton(
            text = stringResource(CoreR.string.superuser_toggle_revoke),
            onClick = onRevoke,
            danger = true,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun EmptyState(title: String, hint: String?) {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
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
