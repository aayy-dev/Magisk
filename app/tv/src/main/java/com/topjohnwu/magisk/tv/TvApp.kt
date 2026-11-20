package com.topjohnwu.magisk.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import com.topjohnwu.magisk.tv.ui.TvColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.tv.denylist.DenyListScreen
import com.topjohnwu.magisk.tv.flash.FlashScreen
import com.topjohnwu.magisk.tv.home.HomeScreen
import com.topjohnwu.magisk.tv.install.InstallScreen
import com.topjohnwu.magisk.tv.logs.LogsScreen
import com.topjohnwu.magisk.tv.modules.ModulesScreen
import com.topjohnwu.magisk.tv.settings.SettingsScreen
import com.topjohnwu.magisk.tv.superuser.SuperuserScreen
import com.topjohnwu.magisk.tv.ui.LocalTvBarFocused
import com.topjohnwu.magisk.tv.ui.MagiskTvTheme
import com.topjohnwu.magisk.tv.ui.TvBackdrop
import com.topjohnwu.magisk.tv.ui.TvKit
import com.topjohnwu.magisk.tv.ui.tvFocusedContainer
import com.topjohnwu.magisk.tv.ui.tvFocusedContent
import kotlinx.coroutines.delay
import com.topjohnwu.magisk.core.R as CoreR

/** The remote-driven Magisk UI: a tab bar over the main sections, with detail screens pushed on top. */
@Composable
fun TvApp() {
    MagiskTvTheme {
        val navigator = rememberSaveable(saver = TvNavigatorSaver) { TvNavigator() }
        // Each route (and each tab) keeps its scroll position and focus while it is below the top
        val stateHolder = rememberSaveableStateHolder()
        navigator.onRouteRemoved = { stateHolder.removeState(it) }

        BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
        TvStartupWarnings()

        CompositionLocalProvider(LocalTvNavigator provides navigator) {
            TvBackdrop {
                AnimatedContent(
                    targetState = navigator.top,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "tv_route",
                ) { route ->
                    stateHolder.SaveableStateProvider(route.key) {
                        when (route) {
                            TvRoute.Main -> TvMain(stateHolder)
                            else -> TvRouteContent(route)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvMain(stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder) {
    val navigator = LocalTvNavigator.current
    val tabRequesters = remember { TvTab.entries.associateWith { FocusRequester() } }
    val barFocusedState = remember { mutableStateOf(false) }
    var barFocused by barFocusedState

    // Back from the content goes up to the tab bar first, then leaves the app
    BackHandler(enabled = !barFocused) {
        runCatching { tabRequesters.getValue(navigator.tab).requestFocus() }
    }

    CompositionLocalProvider(LocalTvBarFocused provides barFocusedState) {
    Column(Modifier.fillMaxSize()) {
        TvTopBar(
            selected = navigator.tab,
            requesters = tabRequesters,
            onSelect = navigator::selectTab,
            modifier = Modifier.onFocusChanged { barFocused = it.hasFocus },
        )
        Box(
            Modifier
                .weight(1f)
                // Left/Right inside a screen must never jump up into the tab bar; Up still can
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Left || direction == FocusDirection.Right) {
                            FocusRequester.Cancel
                        } else {
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            AnimatedContent(
                targetState = navigator.tab,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                label = "tv_tab",
            ) { tab ->
                stateHolder.SaveableStateProvider("tab:$tab") {
                    TvTabContent(tab)
                }
            }
        }
    }
    }
}

@Composable
private fun TvTabContent(tab: TvTab) {
    when (tab) {
        TvTab.Home -> HomeScreen()
        TvTab.Superuser -> SuperuserScreen()
        TvTab.Modules -> ModulesScreen()
        TvTab.Logs -> LogsScreen()
        TvTab.Settings -> SettingsScreen()
    }
}

@Composable
private fun TvRouteContent(route: TvRoute) {
    when (route) {
        TvRoute.Install -> InstallScreen()
        is TvRoute.Flash -> FlashScreen(route)
        TvRoute.DenyList -> DenyListScreen()
        TvRoute.Main -> Unit
    }
}

@Composable
private fun TvComingSoon(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun TvTopBar(
    selected: TvTab,
    requesters: Map<TvTab, FocusRequester>,
    onSelect: (TvTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = mapOf(
        TvTab.Home to true,
        TvTab.Superuser to Info.showSuperUser,
        TvTab.Modules to Info.env.isActive,
        TvTab.Logs to true,
        TvTab.Settings to true,
    )
    // Inside the overscan-safe area (about 27dp), with the tabs centered like Google TV's home bar
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TvKit.SafeHorizontal)
            .padding(top = TvKit.SafeVertical, bottom = 12.dp),
    ) {
        Image(
            painter = painterResource(CoreR.drawable.ic_magisk),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart),
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (tab in TvTab.entries) {
                TvTabPill(
                    text = stringResource(tab.label()),
                    selected = tab == selected,
                    enabled = enabled.getValue(tab),
                    onFocused = { onSelect(tab) },
                    modifier = Modifier.focusRequester(requesters.getValue(tab)),
                )
            }
        }
        TvClock(Modifier.align(Alignment.CenterEnd))
    }
}

/** The time, as on the Google TV home screen. */
@Composable
private fun TvClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val format = remember { android.text.format.DateFormat.getTimeFormat(context) }
    var time by remember { mutableStateOf(format.format(java.util.Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            time = format.format(java.util.Date())
            delay(15_000)
        }
    }
    Text(
        text = time,
        modifier = modifier,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun TvTab.label() = when (this) {
    TvTab.Home -> R.string.tv_tab_home
    TvTab.Superuser -> CoreR.string.superuser
    TvTab.Modules -> CoreR.string.modules
    TvTab.Logs -> CoreR.string.logs
    TvTab.Settings -> CoreR.string.settings
}

/**
 * A tab pill. The tab becomes selected when it takes focus, shortly after, so scrubbing across the
 * bar with the remote does not load every section on the way.
 */
@Composable
private fun TvTabPill(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) {
            delay(140)
            onFocused()
        }
    }
    Surface(
        onClick = onFocused,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(TvKit.PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = tvFocusedContainer(),
            focusedContentColor = tvFocusedContent(),
            pressedContainerColor = tvFocusedContainer(),
            pressedContentColor = tvFocusedContent(),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            // The selected tab is marked by an underline, never by a button-like fill
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .width(22.dp)
                    .background(
                        if (selected && !focused) TvColors.Teal else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}
