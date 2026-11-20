package com.topjohnwu.magisk.tv.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.tv.FlashMode
import com.topjohnwu.magisk.tv.LocalTvNavigator
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvDialog
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvPageTitle
import com.topjohnwu.magisk.tv.ui.TvPillButton
import com.topjohnwu.magisk.tv.ui.TvRadioRow
import com.topjohnwu.magisk.tv.ui.TvScrollableText
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.TvSwitchRow
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.core.R as CoreR

private const val METHOD_PATCH = 0
private const val METHOD_DIRECT = 1
private const val METHOD_SLOT = 2

/**
 * Install or upgrade Magisk: options and a method on the right, the version and the start button on
 * the left. The two steps of the phone UI (options, then method) are one page here.
 */
@Composable
fun InstallScreen(viewModel: InstallViewModel = viewModel()) {
    val navigator = LocalTvNavigator.current
    val activity = rememberTvActivity()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    // The file chosen in the picker starts the patch
    val picked by PatchPicker.result.collectAsStateWithLifecycle()
    LaunchedEffect(picked) {
        picked?.let {
            PatchPicker.result.value = null
            navigator.push(TvRoute.Flash(FlashMode.PatchFile, uri = it.toString()))
        }
    }

    // Same availability rules as the phone UI
    val rooted = Info.isRooted
    val skipOptions = Info.isEmulator || (Info.isSAR && !Info.isFDE && Info.ramdisk)
    val noSecondSlot = !rooted || !Info.isAB || Info.isEmulator
    val showVerity = !Info.isSAR
    val showEncryption = Info.isFDE
    val showRecovery = !Info.ramdisk
    val showOptions = !skipOptions && (showVerity || showEncryption || showRecovery)

    var method by rememberSaveable { mutableStateOf(if (rooted) METHOD_DIRECT else METHOD_PATCH) }
    var keepVerity by remember { mutableStateOf(Config.keepVerity) }
    var keepEncryption by remember { mutableStateOf(Config.keepEnc) }
    var recovery by remember { mutableStateOf(Config.recovery) }
    var confirmSlot by remember { mutableStateOf(false) }

    // Open on the current method, like a choice page
    val methodFocus = remember { listOf(FocusRequester(), FocusRequester(), FocusRequester()) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { methodFocus[method].requestFocus() }
    }

    fun start() {
        when (method) {
            METHOD_PATCH -> activity.getContent("*/*", PatchUriCallback())
            METHOD_DIRECT -> navigator.push(TvRoute.Flash(FlashMode.DirectInstall))
            METHOD_SLOT -> navigator.push(TvRoute.Flash(FlashMode.InactiveSlot, secondSlot = true))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = TvKit.SafeHorizontal, vertical = TvKit.SafeVertical),
    ) {
        // Left: what this does, and the button that does it
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .padding(end = 32.dp),
        ) {
            TvPageTitle(stringResource(CoreR.string.install))
            Text(
                text = stringResource(R.string.tv_install_version, BuildConfig.APP_VERSION_NAME, BuildConfig.APP_VERSION_CODE),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = stringResource(R.string.tv_install_blurb),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
            if (notes.isNotBlank()) {
                Text(
                    text = stringResource(R.string.tv_install_whats_new).uppercase(),
                    fontSize = 12.sp,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
                TvScrollableText(
                    text = notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            TvPillButton(
                text = stringResource(CoreR.string.install_start),
                icon = TvIcons.Install,
                primary = true,
                onClick = { start() },
            )
        }

        // Right: options, then method
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showOptions) {
                TvSectionHeader(stringResource(CoreR.string.install_options_title))
                if (showVerity) {
                    TvSwitchRow(
                        title = stringResource(CoreR.string.keep_dm_verity),
                        checked = keepVerity,
                        onCheckedChange = {
                            keepVerity = it
                            Config.keepVerity = it
                        },
                    )
                }
                if (showEncryption) {
                    TvSwitchRow(
                        title = stringResource(CoreR.string.keep_force_encryption),
                        checked = keepEncryption,
                        onCheckedChange = {
                            keepEncryption = it
                            Config.keepEnc = it
                        },
                    )
                }
                if (showRecovery) {
                    TvSwitchRow(
                        title = stringResource(CoreR.string.recovery_mode),
                        checked = recovery,
                        onCheckedChange = {
                            recovery = it
                            Config.recovery = it
                        },
                    )
                }
            }

            TvSectionHeader(stringResource(CoreR.string.install_method_title))
            TvRadioRow(
                title = stringResource(CoreR.string.select_patch_file),
                summary = stringResource(R.string.tv_install_method_patch_hint),
                selected = method == METHOD_PATCH,
                onClick = { method = METHOD_PATCH },
                modifier = Modifier.focusRequester(methodFocus[METHOD_PATCH]),
            )
            if (rooted) {
                TvRadioRow(
                    title = stringResource(CoreR.string.direct_install),
                    summary = stringResource(R.string.tv_install_method_direct_hint),
                    selected = method == METHOD_DIRECT,
                    onClick = { method = METHOD_DIRECT },
                    modifier = Modifier.focusRequester(methodFocus[METHOD_DIRECT]),
                )
            }
            if (!noSecondSlot) {
                TvRadioRow(
                    title = stringResource(CoreR.string.install_inactive_slot),
                    summary = stringResource(R.string.tv_install_method_slot_hint),
                    selected = method == METHOD_SLOT,
                    // Only selected once the warning is accepted
                    onClick = { confirmSlot = true },
                    modifier = Modifier.focusRequester(methodFocus[METHOD_SLOT]),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmSlot) {
        TvDialog(
            title = stringResource(android.R.string.dialog_alert_title),
            message = stringResource(CoreR.string.install_inactive_slot_msg),
            onDismiss = { confirmSlot = false },
            actions = listOf(
                TvAction(
                    text = stringResource(android.R.string.cancel),
                    isDefault = true,
                    onClick = { confirmSlot = false },
                ),
                TvAction(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        method = METHOD_SLOT
                        confirmSlot = false
                    },
                ),
            ),
        )
    }
}
