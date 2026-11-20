package com.topjohnwu.magisk.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** The sections shown in the top tab bar. */
enum class TvTab { Home, Superuser, Modules, Logs, Settings }

/** What the flash console runs. */
enum class FlashMode {
    /** Patch the boot image at [TvRoute.Flash.uri] and save the result to Downloads. */
    PatchFile,

    /** Patch and flash the boot image of the running slot. */
    DirectInstall,

    /** Patch and flash the boot image of the other slot (A/B devices). */
    InactiveSlot,

    /** Run the uninstaller. */
    Uninstall,

    /** Install the module zip at [TvRoute.Flash.uri]. */
    ModuleZip,

    /** Run the action of the module [TvRoute.Flash.id]. */
    ModuleAction,
}

/** Screens that can be pushed on top of the tabs. */
sealed interface TvRoute {
    val key: String

    /** The tab bar and the section it shows. */
    data object Main : TvRoute {
        override val key = "main"
    }

    /** Install / upgrade Magisk. */
    data object Install : TvRoute {
        override val key = "install"
    }

    /** DenyList configuration. */
    data object DenyList : TvRoute {
        override val key = "denylist"
    }

    /** Console output of an install, uninstall or module operation. */
    data class Flash(
        val mode: FlashMode,
        val uri: String? = null,
        val id: String? = null,
        val label: String? = null,
        val secondSlot: Boolean = false,
    ) : TvRoute {
        override val key = "flash:$mode:$uri:$id"
    }
}

/**
 * Back stack of the TV UI. The tab bar is the root; screens pushed above it hide the bar and
 * return to the same tab, with its scroll and focus intact.
 */
class TvNavigator(initialStack: List<TvRoute> = listOf(TvRoute.Main), initialTab: TvTab = TvTab.Home) {
    val stack = mutableStateListOf<TvRoute>().apply { addAll(initialStack.ifEmpty { listOf(TvRoute.Main) }) }
    val top: TvRoute get() = stack.last()

    var tab by mutableStateOf(initialTab)
        private set

    /** Called with the key of a route that left the stack, so its saved state can be dropped. */
    internal var onRouteRemoved: (String) -> Unit = {}

    fun selectTab(tab: TvTab) {
        this.tab = tab
    }

    fun push(route: TvRoute) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) {
            val removed = stack.removeAt(stack.lastIndex)
            onRouteRemoved(removed.key)
        }
    }
}

val LocalTvNavigator = staticCompositionLocalOf<TvNavigator> { error("No TvNavigator provided") }

/**
 * Keeps the open screens and the selected tab across an activity recreation (font size, process
 * death...). A flash console is deliberately not restored: bringing it back would start the
 * operation again, so the stack falls back to the screen below it.
 */
val TvNavigatorSaver: Saver<TvNavigator, List<String>> = Saver(
    save = { nav ->
        listOf(nav.tab.name) + nav.stack.mapNotNull {
            when (it) {
                TvRoute.Main -> "main"
                TvRoute.Install -> "install"
                TvRoute.DenyList -> "denylist"
                is TvRoute.Flash -> null
            }
        }
    },
    restore = { saved ->
        val tab = TvTab.entries.firstOrNull { it.name == saved.firstOrNull() } ?: TvTab.Home
        val stack = saved.drop(1).mapNotNull {
            when (it) {
                "main" -> TvRoute.Main
                "install" -> TvRoute.Install
                "denylist" -> TvRoute.DenyList
                else -> null
            }
        }
        TvNavigator(stack, tab)
    },
)
