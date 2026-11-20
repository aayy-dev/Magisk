package com.topjohnwu.magisk.tv.denylist

import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.ktx.concurrentMap
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.superuser.Shell
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The apps of the DenyList screen, built from the package manager and `magisk --denylist ls`. */
internal class DenyListViewModel : ViewModel() {

    var loading by mutableStateOf(true)
        private set

    var apps by mutableStateOf<List<DenyApp>>(emptyList())
        private set

    var showSystem by mutableStateOf(false)
    var showOS by mutableStateOf(false)

    var enforced by mutableStateOf(Config.denyList)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            apps = withContext(Dispatchers.Default) {
                val pm = AppContext.packageManager
                val denyList = Shell.cmd("magisk --denylist ls").exec().out.map { CmdlineListItem(it) }
                val all = pm.getInstalledApplications(MATCH_UNINSTALLED_PACKAGES)
                all.asFlow()
                    .filter { AppContext.packageName != it.packageName }
                    .concurrentMap { buildDenyApp(it, it.getLabel(pm), pm, denyList) }
                    .filter { it.processes.isNotEmpty() }
                    .toCollection(ArrayList(all.size))
                    // Apps already on the list first, then by name, as in the phone UI
                    .sortedWith(
                        compareBy<DenyApp>(
                            { !it.isChecked },
                            { it.label.lowercase(Locale.ROOT) },
                            { it.packageName },
                        ),
                    )
            }
            loading = false
        }
    }

    /** Same command as the Enforce DenyList setting; the state only changes when it succeeds. */
    fun applyEnforced(enable: Boolean) {
        val cmd = if (enable) "enable" else "disable"
        Shell.cmd("magisk --denylist $cmd").submit { result ->
            if (result.isSuccess) {
                Config.denyList = enable
                enforced = enable
            }
        }
    }

    /** Apps to show: those on the list, plus whatever the filters allow. */
    fun visibleApps(): List<DenyApp> = apps.filter {
        it.isChecked || ((showSystem || !it.isSystemApp) && ((showSystem && showOS) || it.isApp))
    }
}

/** Icons of the app rows, decoded off the main thread and kept for a while. */
internal object DenyIcons {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun cached(app: DenyApp): ImageBitmap? = cache.get(app.packageName)

    suspend fun load(app: DenyApp): ImageBitmap? {
        cache.get(app.packageName)?.let { return it }
        val icon = withContext(Dispatchers.IO) {
            runCatching {
                val pm = AppContext.packageManager
                app.info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
        if (icon != null) cache.put(app.packageName, icon)
        return icon
    }
}
