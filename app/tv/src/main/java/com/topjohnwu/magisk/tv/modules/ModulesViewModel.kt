package com.topjohnwu.magisk.tv.modules

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.base.ContentResultCallback
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.download.Subject
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.module.OnlineModule
import com.topjohnwu.magisk.tv.TvActivity
import com.topjohnwu.magisk.view.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

/**
 * Installed modules for the TV UI. Same sources and status rules as the phone's ModuleViewModel
 * and LocalModuleRvItem, exposed as immutable state.
 */
class ModulesViewModel : ViewModel() {

    /** Why a module is not running although it is enabled. */
    enum class Notice { ZygiskUnloaded, NeedsZygiskOff, NeedsZygiskOn }

    data class Item(
        val key: String,
        val id: String,
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val enabled: Boolean,
        val removed: Boolean,
        /** An update was staged and applies after a reboot. */
        val updated: Boolean,
        val outdated: Boolean,
        val hasUpdateInfo: Boolean,
        val notice: Notice?,
        val hasAction: Boolean,
    ) {
        /** The module can be updated right now, as the phone UI enables its Update button. */
        val updateReady get() = hasUpdateInfo && outdated && !removed && enabled

        /** Matches the phone UI: the Action button is hidden while a notice is shown. */
        val showAction get() = hasAction && notice == null
    }

    data class State(
        val loading: Boolean = true,
        /** False when Magisk is inactive or the module directory is not there. */
        val available: Boolean = true,
        val items: List<Item> = emptyList(),
    )

    private var modules: List<LocalModule> = emptyList()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Reloads the module list. Update information that was already fetched is carried over, so
     * coming back to the screen does not hit the network again.
     */
    fun load() {
        viewModelScope.launch {
            val loaded = Info.env.isActive && withContext(Dispatchers.IO) { LocalModule.loaded() }
            if (!loaded) {
                _state.value = State(loading = false, available = false)
                return@launch
            }
            val previous = modules.associateBy { it.id }
            val installed = LocalModule.installed()
            installed.forEach { module ->
                previous[module.id]?.let {
                    module.updateInfo = it.updateInfo
                    module.outdated = it.outdated
                }
            }
            modules = installed
            _state.value = State(loading = false, available = true, items = snapshot())
            fetchUpdates(installed.filter { it.updateInfo == null })
        }
    }

    private suspend fun fetchUpdates(list: List<LocalModule>) {
        withContext(Dispatchers.IO) {
            for (module in list) {
                if (module.fetch()) publish()
            }
        }
    }

    fun setEnabled(item: Item, enabled: Boolean) {
        val module = module(item) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { module.enable = enabled }
            publish()
        }
    }

    fun setRemoved(item: Item, removed: Boolean) {
        val module = module(item) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { module.remove = removed }
            publish()
        }
    }

    /** The online version of a module with a known update, or null. */
    fun onlineModule(item: Item): OnlineModule? = module(item)?.updateInfo

    /** Changelog text of an update, shortened like the phone dialog does. */
    suspend fun changelog(online: OnlineModule): String {
        val text = ServiceLocator.networkService.fetchString(online.changelog)
        return if (text.length > 1000) text.substring(0, 1000) else text
    }

    // Keyed by the module's folder: the id in module.prop can be duplicated across folders
    private fun module(item: Item) = modules.firstOrNull { "module:${it.base.name}" == item.key }

    private fun publish() = _state.update { it.copy(items = snapshot()) }

    private fun snapshot() = modules.map { module ->
        val zygiskUnloaded = module.isZygisk && module.zygiskUnloaded
        val notice = when {
            zygiskUnloaded -> Notice.ZygiskUnloaded
            Info.isZygiskEnabled && module.isRiru -> Notice.NeedsZygiskOff
            !Info.isZygiskEnabled && module.isZygisk -> Notice.NeedsZygiskOn
            else -> null
        }
        Item(
            key = "module:${module.base.name}",
            id = module.id,
            name = module.name,
            version = module.version,
            author = module.author,
            description = module.description,
            enabled = module.enable,
            removed = module.remove,
            updated = module.updated,
            outdated = module.outdated,
            hasUpdateInfo = module.updateInfo != null,
            notice = notice,
            hasAction = module.hasAction,
        )
    }
}

/**
 * Hand-over of the zip picked in the system file picker. The picker callback has to be Parcelable
 * and can outlive the screen, so it writes to this process-wide holder (as the phone UI does).
 */
object ModuleZipPicker {
    val picked = MutableStateFlow<Uri?>(null)
}

@Parcelize
internal class ModuleZipCallback : ContentResultCallback {
    override fun onActivityResult(result: Uri) {
        ModuleZipPicker.picked.value = result
    }
}

/**
 * Download of a module update. The notification only opens the app. Installing the downloaded zip
 * is never driven by data carried in an intent: [TvActivity] is exported (it is the launcher entry),
 * so any app could otherwise make Magisk flash a zip of its choosing as root. The zip is installed
 * from the confirmation in the Modules screen ("Install from storage"), as for any local file.
 */
@Parcelize
internal class ModuleDownload(
    override val module: OnlineModule,
    override val autoLaunch: Boolean,
    override val notifyId: Int = Notifications.nextId(),
) : Subject.Module() {

    override fun pendingIntent(context: Context): PendingIntent {
        val intent = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return intent.toPending(context)
    }
}
