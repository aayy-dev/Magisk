package com.topjohnwu.magisk.tv.home

import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.DownloadEngine
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.ktx.reboot
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.tasks.MagiskInstaller
import com.topjohnwu.magisk.tv.FlashMode
import com.topjohnwu.magisk.tv.LocalTvNavigator
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.magisk.tv.TvTab
import com.topjohnwu.magisk.tv.home.HomeViewModel.MagiskState
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvActionRow
import com.topjohnwu.magisk.tv.ui.TvCard
import com.topjohnwu.magisk.tv.ui.TvChip
import com.topjohnwu.magisk.tv.ui.TvRow
import com.topjohnwu.magisk.tv.ui.TvColors
import com.topjohnwu.magisk.tv.ui.TvDialog
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.TvSectionHeader
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import kotlinx.coroutines.launch
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.magisk.core.R as CoreR

private sealed interface HomeDialog {
    data object Uninstall : HomeDialog
    data object Reboot : HomeDialog
    data object AppUpdate : HomeDialog
    data object EnvFix : HomeDialog
    data object Restoring : HomeDialog
}

/** Dashboard: Magisk status and actions, then an overview of the other sections. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalTvNavigator.current
    val activity = rememberTvActivity()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val magiskState = viewModel.magiskState

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TvKit.SafeHorizontal)
            .padding(top = 8.dp, bottom = TvKit.SafeVertical),
    ) {
        if (state.envProblem != 0) {
            EnvBanner(onFix = { dialog = HomeDialog.EnvFix })
            Spacer(Modifier.height(16.dp))
        }

        Hero(
            magiskState = magiskState,
            actions = buildList {
                add(
                    TvAction(
                        text = stringResource(
                            when (magiskState) {
                                MagiskState.OUTDATED -> CoreR.string.update
                                MagiskState.UP_TO_DATE -> R.string.tv_home_reinstall
                                else -> CoreR.string.install
                            },
                        ),
                        icon = TvIcons.Install,
                        isDefault = true,
                        onClick = { navigator.push(TvRoute.Install) },
                    ),
                )
                if (magiskState != MagiskState.INVALID) {
                    add(
                        TvAction(
                            text = stringResource(CoreR.string.uninstall_magisk_title),
                            icon = TvIcons.Delete,
                            danger = true,
                            onClick = { dialog = HomeDialog.Uninstall },
                        ),
                    )
                }
                add(
                    TvAction(
                        text = stringResource(R.string.tv_card_reboot),
                        icon = TvIcons.Restart,
                        onClick = { dialog = HomeDialog.Reboot },
                    ),
                )
            },
        )

        Spacer(Modifier.height(10.dp))
        TvSectionHeader(stringResource(R.string.tv_home_overview), inset = 4.dp)
        OverviewCards(
            state = state,
            onSuperuser = { navigator.selectTab(TvTab.Superuser) },
            onModules = { navigator.selectTab(TvTab.Modules) },
            onDenyList = { navigator.push(TvRoute.DenyList) },
            onApp = { if (state.app.updateAvailable) dialog = HomeDialog.AppUpdate else viewModel.refresh() },
        )

        if (state.noticeVisible) {
            Spacer(Modifier.height(8.dp))
            SafetyNotice(onHide = viewModel::hideNotice)
        }
    }

    when (dialog) {
        HomeDialog.Uninstall -> TvDialog(
            title = stringResource(CoreR.string.uninstall_magisk_title),
            message = stringResource(CoreR.string.uninstall_magisk_msg),
            onDismiss = { dialog = null },
            actions = listOf(
                TvAction(
                    text = stringResource(CoreR.string.restore_img),
                    onClick = {
                        dialog = HomeDialog.Restoring
                        scope.launch {
                            MagiskInstaller.Restore().exec { success ->
                                dialog = null
                                context.toast(
                                    if (success) CoreR.string.restore_done else CoreR.string.restore_fail,
                                    if (success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                )
                            }
                        }
                    },
                ),
                TvAction(
                    text = stringResource(CoreR.string.complete_uninstall),
                    danger = true,
                    onClick = {
                        dialog = null
                        navigator.push(TvRoute.Flash(FlashMode.Uninstall))
                    },
                ),
                TvAction(
                    text = stringResource(android.R.string.cancel),
                    isDefault = true,
                    onClick = { dialog = null },
                ),
            ),
        )

        HomeDialog.Restoring -> TvDialog(
            title = stringResource(R.string.tv_home_restoring),
            onDismiss = {},
            actions = emptyList(),
        )

        HomeDialog.Reboot -> RebootDialog(onDismiss = { dialog = null })

        HomeDialog.AppUpdate -> TvDialog(
            title = stringResource(CoreR.string.update_available),
            message = state.app.latest,
            onDismiss = { dialog = null },
            content = {
                if (state.app.changelog.isNotBlank()) {
                    Text(
                        text = state.app.changelog.lineSequence().take(8).joinToString("\n"),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = listOf(
                TvAction(
                    text = stringResource(CoreR.string.install),
                    isDefault = true,
                    onClick = {
                        dialog = null
                        DownloadEngine.startWithActivity(activity, Subject.App())
                    },
                ),
                TvAction(stringResource(android.R.string.cancel), onClick = { dialog = null }),
            ),
        )

        HomeDialog.EnvFix -> TvDialog(
            title = stringResource(CoreR.string.env_fix_title),
            message = stringResource(R.string.tv_home_env_hint),
            onDismiss = { dialog = null },
            actions = listOf(
                TvAction(
                    text = stringResource(android.R.string.ok),
                    isDefault = true,
                    onClick = {
                        dialog = HomeDialog.Restoring
                        scope.launch {
                            MagiskInstaller.FixEnv().exec { success ->
                                dialog = null
                                context.toast(
                                    if (success) CoreR.string.reboot_delay_toast else CoreR.string.setup_fail,
                                    Toast.LENGTH_LONG,
                                )
                                if (success) {
                                    viewModel.clearEnvProblem()
                                    // Not in the composition scope: leaving the tab must not cancel it
                                    UiThreadHandler.handler.postDelayed({ reboot() }, 5000)
                                }
                            }
                        }
                    },
                ),
                TvAction(stringResource(android.R.string.cancel), onClick = { dialog = null }),
            ),
        )

        null -> Unit
    }
}

@Composable
private fun Hero(magiskState: MagiskState, actions: List<TvAction>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(CoreR.drawable.ic_magisk),
                contentDescription = null,
                modifier = Modifier.size(84.dp),
            )
        }
        Spacer(Modifier.width(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val title = when (magiskState) {
                MagiskState.INVALID -> stringResource(R.string.tv_home_not_installed)
                else -> stringResource(R.string.tv_home_installed, Info.env.versionString)
            }
            Text(
                text = title,
                fontSize = 38.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusLine(magiskState)
            TvActionRow(actions = actions, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatusLine(magiskState: MagiskState) {
    when (magiskState) {
        MagiskState.INVALID -> Text(
            text = stringResource(R.string.tv_home_not_installed_hint),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MagiskState.UNSUPPORTED -> TvChip(
            text = stringResource(R.string.tv_home_unsupported),
            color = TvColors.Danger,
        )

        else -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (magiskState == MagiskState.OUTDATED) {
                TvChip(stringResource(CoreR.string.update_available), color = TvColors.Yellow, filled = true)
            } else {
                TvChip(stringResource(R.string.tv_home_up_to_date), color = TvColors.Good)
            }
            TvChip("${Info.env.versionCode}" + if (Info.env.isDebug) " (D)" else "", color = TvColors.Teal)
            if (Info.isZygiskEnabled) {
                TvChip(stringResource(R.string.tv_chip_zygisk), color = TvColors.Teal)
            }
            if (Info.ramdisk) {
                TvChip(stringResource(R.string.tv_chip_ramdisk), color = TvColors.Teal)
            }
            if (Info.isAB && Info.slot.isNotEmpty()) {
                TvChip(stringResource(R.string.tv_chip_slot, Info.slot), color = TvColors.Teal)
            }
        }
    }
}

@Composable
private fun OverviewCards(
    state: HomeViewModel.State,
    onSuperuser: () -> Unit,
    onModules: () -> Unit,
    onDenyList: () -> Unit,
    onApp: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OverviewCard(
            icon = TvIcons.Shield,
            title = stringResource(CoreR.string.superuser),
            value = if (state.suTotal > 0) {
                stringResource(R.string.tv_card_su_value, state.suGranted)
            } else {
                stringResource(R.string.tv_card_su_none)
            },
            caption = if (state.suTotal > 0) stringResource(R.string.tv_card_su_total, state.suTotal) else null,
            enabled = Info.showSuperUser,
            onClick = onSuperuser,
        )
        OverviewCard(
            icon = TvIcons.Extension,
            title = stringResource(CoreR.string.modules),
            value = if (state.modulesTotal > 0) {
                stringResource(R.string.tv_card_modules_value, state.modulesEnabled, state.modulesTotal)
            } else {
                stringResource(R.string.tv_card_modules_none)
            },
            enabled = state.modulesAvailable,
            onClick = onModules,
        )
        OverviewCard(
            icon = TvIcons.Bug,
            title = stringResource(CoreR.string.denylist),
            value = stringResource(if (Config.denyList) R.string.tv_card_denylist_on else R.string.tv_card_denylist_off),
            enabled = Info.env.isActive,
            onClick = onDenyList,
        )
        OverviewCard(
            icon = TvIcons.Manager,
            title = stringResource(CoreR.string.home_app_title),
            value = com.topjohnwu.magisk.core.BuildConfig.APP_VERSION_NAME,
            caption = when {
                state.app.loading -> stringResource(CoreR.string.loading)
                state.app.latest == null -> stringResource(CoreR.string.no_connection)
                state.app.updateAvailable -> stringResource(CoreR.string.update_available)
                else -> stringResource(R.string.tv_home_up_to_date)
            },
            highlight = state.app.updateAvailable,
            onClick = onApp,
        )
    }
}

@Composable
private fun OverviewCard(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    highlight: Boolean = false,
) {
    TvCard(onClick = onClick, enabled = enabled, modifier = modifier.width(200.dp)) { focused ->
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (highlight) TvColors.Yellow.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.tv.material3.Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (highlight) TvColors.Yellow else if (focused) tvFocusedContainer() else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Tells you OK opens something
                androidx.tv.material3.Icon(
                    imageVector = TvIcons.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (focused) 0.9f else 0.35f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption ?: " ",
                fontSize = 14.sp,
                color = if (highlight) TvColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EnvBanner(onFix: () -> Unit) {
    TvCard(onClick = onFix, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            androidx.tv.material3.Icon(
                TvIcons.Update,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = TvColors.Yellow,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.tv_home_env_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.tv_home_env_hint),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/** A quiet one-line footer; OK dismisses it for good (the stock notice can be hidden the same way). */
@Composable
private fun SafetyNotice(onHide: () -> Unit) {
    TvRow(
        title = stringResource(CoreR.string.home_notice_content),
        titleSize = 14.sp,
        titleWeight = FontWeight.Normal,
        onClick = onHide,
        trailing = {
            Text(
                stringResource(R.string.tv_home_hide_notice),
                fontSize = 14.sp,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )
        },
    )
}

@Composable
private fun RebootDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val softReboot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        context.getSystemService<PowerManager>()?.isRebootingUserspaceSupported == true
    var safeMode by remember { mutableStateOf(Config.bootloop >= 2) }

    TvDialog(
        title = stringResource(R.string.tv_reboot_title),
        onDismiss = onDismiss,
        actions = buildList {
            add(TvAction(stringResource(R.string.tv_reboot_normal), onClick = { reboot() }))
            if (softReboot) add(TvAction(stringResource(CoreR.string.reboot_userspace), onClick = { reboot("userspace") }))
            add(TvAction(stringResource(CoreR.string.reboot_recovery), onClick = { reboot("recovery") }))
            add(TvAction(stringResource(CoreR.string.reboot_bootloader), onClick = { reboot("bootloader") }))
            add(TvAction(stringResource(CoreR.string.reboot_download), onClick = { reboot("download") }))
            add(TvAction(stringResource(CoreR.string.reboot_edl), onClick = { reboot("edl") }))
            if (Const.Version.atLeast_28_0()) {
                add(
                    TvAction(
                        text = stringResource(
                            if (safeMode) R.string.tv_reboot_safe_mode_on else R.string.tv_reboot_safe_mode_off,
                        ),
                        onClick = {
                            safeMode = !safeMode
                            Config.bootloop = if (safeMode) 2 else 0
                        },
                    ),
                )
            }
            // Cancel holds the initial focus, so a stray OK never reboots the device
            add(TvAction(stringResource(android.R.string.cancel), isDefault = true, onClick = onDismiss))
        },
    )
}
