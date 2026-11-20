@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.topjohnwu.magisk.tv.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.utils.LocaleSetting
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.RootUtils
import com.topjohnwu.magisk.tv.LocalTvNavigator
import com.topjohnwu.magisk.tv.R
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.magisk.tv.ui.TvIcons
import com.topjohnwu.magisk.tv.ui.rememberTvActivity
import com.topjohnwu.magisk.tv.ui.rememberTvListFocus
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import com.topjohnwu.magisk.core.R as CoreR

private val RequestTimeoutValues = listOf(10, 15, 20, 30, 45, 60)

/**
 * The settings of the phone UI as a two-pane TV list. Each row reads and writes the same [Config]
 * property as its phone counterpart, so both UIs always agree. Hiding the app and the theme choice
 * are not offered: the TV UI always runs as the visible, dark app.
 */
@Composable
fun SettingsScreen() {
    val activity = rememberTvActivity()
    val context = LocalContext.current
    val navigator = LocalTvNavigator.current
    val scope = rememberCoroutineScope()
    val focus = rememberTvListFocus()

    var openChoice by remember { mutableStateOf<OpenChoice?>(null) }
    var openText by remember { mutableStateOf<OpenText?>(null) }
    var preview by remember { mutableStateOf<SettingsPreview?>(null) }
    // Config values are not observable; bumping this rebuilds the rows after a write
    var version by remember { mutableIntStateOf(0) }

    // While a sub-page is open Back closes it; otherwise the shell moves focus to the tab bar
    BackHandler(enabled = openChoice != null || openText != null) {
        openChoice = null
        openText = null
    }

    val showingList = openChoice == null && openText == null
    var leftList by remember { mutableStateOf(false) }
    LaunchedEffect(showingList) {
        if (!showingList) {
            leftList = true
        } else if (leftList) {
            focus.restore()
        }
    }

    openChoice?.let {
        SettingsChoicePage(it) { openChoice = null }
        return
    }
    openText?.let {
        SettingsTextPage(it) { openText = null }
        return
    }

    val updateChannels = stringArrayResource(CoreR.array.update_channel)
    val suAccess = stringArrayResource(CoreR.array.su_access)
    val multiuser = stringArrayResource(CoreR.array.multiuser_mode)
    val multiuserSummary = stringArrayResource(CoreR.array.multiuser_summary)
    val namespaces = stringArrayResource(CoreR.array.namespace)
    val namespaceSummary = stringArrayResource(CoreR.array.namespace_summary)
    val autoResponse = stringArrayResource(CoreR.array.auto_response)
    val timeouts = stringArrayResource(CoreR.array.request_timeout)
    val notifications = stringArrayResource(CoreR.array.su_notification)

    val customUrlTitle = stringResource(CoreR.string.settings_update_custom)
    val customUrlHint = stringResource(CoreR.string.settings_update_custom_msg)
    val unsetText = stringResource(R.string.tv_set_url_empty)
    val defaultDirText = stringResource(R.string.tv_set_download_dir_empty)
    val systemDefault = stringResource(CoreR.string.system_default)
    val notificationsDenied = stringResource(CoreR.string.post_notifications_denied)

    fun bump() {
        version++
    }

    fun openUrlEntry() {
        openText = OpenText(
            title = customUrlTitle,
            description = customUrlHint,
            value = Config.customChannelUrl,
            placeholder = "https://",
        ) {
            Config.customChannelUrl = it
            Info.resetUpdate()
            bump()
        }
    }

    // A Selector of the stock settings: entries (and optionally longer descriptions) by index
    fun SettingsListScope.selector(
        key: String,
        title: String,
        entries: Array<String>,
        selected: Int,
        onSelect: (Int) -> Unit,
        descriptions: Array<String> = entries,
        icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
        enabled: Boolean = true,
    ) = choice(
        key = key,
        title = title,
        options = entries.mapIndexed { i, entry ->
            SettingsOption(entry, descriptions.getOrNull(i)?.takeIf { it != entry })
        },
        selectedIndex = selected,
        onSelect = {
            onSelect(it)
            bump()
        },
        icon = icon,
        enabled = enabled,
        description = descriptions.getOrNull(selected)?.takeIf { it != entries.getOrNull(selected) },
    )

    SettingsTwoPane(
        focus = focus,
        preview = preview,
        // Coming back from the tab bar lands on the row used last
        listModifier = Modifier.focusRestorer().focusGroup(),
    ) {
        val list = SettingsListScope(
            lazy = this,
            onPreview = { preview = it },
            onOpenChoice = { openChoice = it },
            onOpenText = { openText = it },
            focus = focus,
        )
        // Reading the version makes this block rebuild when a value changes
        check(version >= 0)

        with(list) {
            // --- Customization
            header(context.getString(CoreR.string.settings_customization))
            if (LocaleSetting.useLocaleManager) {
                val locale = LocaleSetting.instance.appLocale
                item(
                    key = "language",
                    title = context.getString(CoreR.string.language),
                    summary = locale?.getDisplayName(locale) ?: systemDefault,
                    description = context.getString(R.string.tv_set_language_system_hint),
                    icon = TvIcons.Settings,
                    onClick = {
                        try {
                            activity.startActivity(LocaleSetting.localeSettingsIntent)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(CoreR.string.app_not_found, Toast.LENGTH_SHORT)
                        }
                    },
                )
            } else {
                val names = LocaleSetting.available.names
                val tags = LocaleSetting.available.tags
                selector(
                    key = "language",
                    title = context.getString(CoreR.string.language),
                    entries = names,
                    selected = tags.indexOf(Config.locale),
                    onSelect = { Config.locale = tags[it] },
                    icon = TvIcons.Settings,
                )
            }

            // --- App
            header(context.getString(CoreR.string.home_app_title))
            selector(
                key = "update_channel",
                title = context.getString(CoreR.string.settings_update_channel_title),
                entries = updateChannels,
                selected = Config.updateChannel,
                onSelect = {
                    Config.updateChannel = it
                    Info.resetUpdate()
                    // Stock opens the URL entry right away when a custom channel has no URL yet
                    if (it == Config.Value.CUSTOM_CHANNEL && Config.customChannelUrl.isBlank()) openUrlEntry()
                },
                icon = TvIcons.Update,
            )
            text(
                key = "update_channel_url",
                title = customUrlTitle,
                value = Config.customChannelUrl,
                summary = Config.customChannelUrl.ifBlank { unsetText },
                description = customUrlHint,
                icon = TvIcons.Forward,
                enabled = Config.updateChannel == Config.Value.CUSTOM_CHANNEL,
                placeholder = "https://",
                onValueChange = {
                    Config.customChannelUrl = it
                    Info.resetUpdate()
                    bump()
                },
            )
            switch(
                key = "doh",
                title = context.getString(CoreR.string.settings_doh_title),
                summary = context.getString(CoreR.string.settings_doh_description),
                checked = Config.doh,
                icon = TvIcons.Download,
                onCheckedChange = {
                    Config.doh = it
                    bump()
                },
            )
            switch(
                key = "check_update",
                title = context.getString(CoreR.string.settings_check_update_title),
                summary = context.getString(CoreR.string.settings_check_update_summary),
                checked = Config.checkUpdate,
                icon = TvIcons.Notifications,
                onCheckedChange = { enable ->
                    if (!enable) {
                        Config.checkUpdate = false
                        bump()
                    } else {
                        activity.withPermission(Manifest.permission.POST_NOTIFICATIONS) { granted ->
                            if (granted) {
                                Config.checkUpdate = true
                            } else {
                                context.toast(notificationsDenied, Toast.LENGTH_SHORT)
                            }
                            bump()
                        }
                    }
                },
            )
            text(
                key = "download_dir",
                title = context.getString(CoreR.string.settings_download_path_title),
                value = Config.downloadDir,
                summary = runCatching { MediaStoreUtils.fullPath(Config.downloadDir) }.getOrDefault(defaultDirText),
                description = context.getString(
                    CoreR.string.settings_download_path_message,
                    runCatching { MediaStoreUtils.fullPath(Config.downloadDir) }.getOrDefault(defaultDirText),
                ) + "\n\n" + context.getString(R.string.tv_set_download_dir_hint),
                icon = TvIcons.Folder,
                onValueChange = {
                    Config.downloadDir = it
                    bump()
                },
            )
            switch(
                key = "rand_name",
                title = context.getString(CoreR.string.settings_random_name_title),
                summary = context.getString(CoreR.string.settings_random_name_description),
                checked = Config.randName,
                icon = TvIcons.Paint,
                onCheckedChange = {
                    Config.randName = it
                    bump()
                },
            )

            // --- Magisk
            if (Info.env.isActive) {
                header(context.getString(CoreR.string.magisk))
                item(
                    key = "hosts",
                    title = context.getString(CoreR.string.settings_hosts_title),
                    summary = context.getString(CoreR.string.settings_hosts_summary),
                    icon = TvIcons.Extension,
                    onClick = {
                        scope.launch {
                            RootUtils.addSystemlessHosts()
                            context.toast(CoreR.string.settings_hosts_toast, Toast.LENGTH_SHORT)
                        }
                    },
                )
                if (Const.Version.atLeast_24_0()) {
                    val zygiskMismatch = Config.zygisk != Info.isZygiskEnabled
                    switch(
                        key = "zygisk",
                        title = context.getString(CoreR.string.zygisk),
                        summary = context.getString(
                            if (zygiskMismatch) CoreR.string.reboot_apply_change else CoreR.string.settings_zygisk_summary,
                        ),
                        checked = Config.zygisk,
                        icon = TvIcons.Shield,
                        onCheckedChange = {
                            Config.zygisk = it
                            bump()
                            if (Config.zygisk != Info.isZygiskEnabled) {
                                context.toast(CoreR.string.reboot_apply_change, Toast.LENGTH_SHORT)
                            }
                        },
                    )
                    switch(
                        key = "denylist",
                        title = context.getString(CoreR.string.settings_denylist_title),
                        summary = context.getString(CoreR.string.settings_denylist_summary),
                        checked = Config.denyList,
                        icon = TvIcons.Bug,
                        onCheckedChange = { enable ->
                            val cmd = if (enable) "enable" else "disable"
                            Shell.cmd("magisk --denylist $cmd").submit { result ->
                                if (result.isSuccess) Config.denyList = enable
                                bump()
                            }
                        },
                    )
                    item(
                        key = "denylist_config",
                        title = context.getString(CoreR.string.settings_denylist_config_title),
                        summary = context.getString(CoreR.string.settings_denylist_config_summary),
                        icon = TvIcons.Bug,
                        drill = true,
                        onClick = { navigator.push(TvRoute.DenyList) },
                    )
                }
            }

            // --- Superuser
            if (Info.showSuperUser) {
                header(context.getString(CoreR.string.superuser))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    // Overlay windows can be hidden from Android 12 on, so the option only exists below
                    switch(
                        key = "tapjack",
                        title = context.getString(CoreR.string.settings_su_tapjack_title),
                        summary = context.getString(CoreR.string.settings_su_tapjack_summary),
                        checked = Config.suTapjack,
                        icon = TvIcons.Shield,
                        onCheckedChange = {
                            Config.suTapjack = it
                            bump()
                        },
                    )
                }
                switch(
                    key = "su_auth",
                    title = context.getString(CoreR.string.settings_su_auth_title),
                    summary = context.getString(
                        if (Info.isDeviceSecure) CoreR.string.settings_su_auth_summary else CoreR.string.settings_su_auth_insecure,
                    ),
                    checked = Config.suAuth,
                    enabled = Info.isDeviceSecure,
                    icon = TvIcons.Shield,
                    onCheckedChange = { enable ->
                        // Changing it needs the current user authentication, as in the phone UI
                        activity.withAuthentication { ok ->
                            if (ok) {
                                Config.suAuth = enable
                                bump()
                            }
                        }
                    },
                )
                selector(
                    key = "su_access",
                    title = context.getString(CoreR.string.superuser_access),
                    entries = suAccess,
                    selected = Config.rootMode,
                    onSelect = { Config.rootMode = it },
                    icon = TvIcons.Shield,
                )
                selector(
                    key = "multiuser",
                    title = context.getString(CoreR.string.multiuser_mode),
                    entries = multiuser,
                    descriptions = multiuserSummary,
                    selected = Config.suMultiuserMode,
                    onSelect = { Config.suMultiuserMode = it },
                    icon = TvIcons.Shield,
                    enabled = Const.USER_ID == 0,
                )
                selector(
                    key = "namespace",
                    title = context.getString(CoreR.string.mount_namespace_mode),
                    entries = namespaces,
                    descriptions = namespaceSummary,
                    selected = Config.suMntNamespaceMode,
                    onSelect = { Config.suMntNamespaceMode = it },
                    icon = TvIcons.Shield,
                )
                selector(
                    key = "auto_response",
                    title = context.getString(CoreR.string.auto_response),
                    entries = autoResponse,
                    selected = Config.suAutoResponse,
                    onSelect = { choice ->
                        // With authentication on, changing the automatic response needs it too
                        if (Config.suAuth) {
                            activity.withAuthentication { ok ->
                                if (ok) {
                                    Config.suAutoResponse = choice
                                    bump()
                                }
                            }
                        } else {
                            Config.suAutoResponse = choice
                        }
                    },
                    icon = TvIcons.Shield,
                )
                selector(
                    key = "request_timeout",
                    title = context.getString(CoreR.string.request_timeout),
                    entries = timeouts,
                    selected = RequestTimeoutValues.indexOf(Config.suDefaultTimeout),
                    onSelect = { Config.suDefaultTimeout = RequestTimeoutValues[it] },
                    icon = TvIcons.Shield,
                )
                selector(
                    key = "su_notification",
                    title = context.getString(CoreR.string.superuser_notification),
                    entries = notifications,
                    selected = Config.suNotification,
                    onSelect = { Config.suNotification = it },
                    icon = TvIcons.Notifications,
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    // Re-authenticating after an upgrade is not feasible on Android 8.0+
                    switch(
                        key = "reauth",
                        title = context.getString(CoreR.string.settings_su_reauth_title),
                        summary = context.getString(CoreR.string.settings_su_reauth_summary),
                        checked = Config.suReAuth,
                        icon = TvIcons.Shield,
                        onCheckedChange = {
                            Config.suReAuth = it
                            bump()
                        },
                    )
                }
                if (Const.Version.atLeast_30_1()) {
                    switch(
                        key = "restrict",
                        title = context.getString(CoreR.string.settings_su_restrict_title),
                        summary = context.getString(CoreR.string.settings_su_restrict_summary),
                        checked = Config.suRestrict,
                        icon = TvIcons.Shield,
                        onCheckedChange = {
                            Config.suRestrict = it
                            bump()
                        },
                    )
                }
            }
        }
    }
}
