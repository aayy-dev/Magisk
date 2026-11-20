package com.topjohnwu.magisk.tv.superuser

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.os.Process
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.R as CoreR
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.getLabel
import com.topjohnwu.magisk.core.model.su.SuPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Superuser policies for the TV UI. Same data and rules as the phone's SuperuserViewModel, exposed
 * as immutable state: one [Item] per package, with all packages of a shared UID following the same
 * [SuPolicy].
 */
class SuperuserViewModel : ViewModel() {

    data class Item(
        val key: String,
        val uid: Int,
        val packageName: String,
        val appName: String,
        val isSharedUid: Boolean,
        val policy: Int,
        val notification: Boolean,
        val logging: Boolean,
        val icon: ImageBitmap?,
    ) {
        /** Restricted apps count as allowed, as in the phone UI. */
        val allowed get() = policy >= SuPolicy.ALLOW
        val title get() = if (isSharedUid) "[SharedUID] $appName" else appName
    }

    data class State(
        val loading: Boolean = true,
        /** False when superuser cannot be managed (Magisk inactive or not the owner user). */
        val available: Boolean = true,
        val items: List<Item> = emptyList(),
    )

    /** A short confirmation for the UI to toast, like the phone's snackbar. */
    class Message(@StringRes val res: Int, val arg: String)

    private class Entry(
        val policy: SuPolicy,
        val packageName: String,
        val isSharedUid: Boolean,
        val appName: String,
        val icon: ImageBitmap,
    )

    private val db get() = ServiceLocator.policyDB
    private var entries: List<Entry> = emptyList()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 8)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Loads the policies. Keeps showing the previous list while it runs. */
    @SuppressLint("InlinedApi")
    fun load() {
        viewModelScope.launch {
            if (!Info.showSuperUser) {
                _state.value = State(loading = false, available = false)
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                db.deleteOutdated()
                db.delete(AppContext.applicationInfo.uid)
                val pm = AppContext.packageManager
                val result = ArrayList<Entry>()
                for (policy in db.fetchAll()) {
                    val pkgs =
                        if (policy.uid == Process.SYSTEM_UID) arrayOf("android")
                        else pm.getPackagesForUid(policy.uid)
                    if (pkgs == null) {
                        db.delete(policy.uid)
                        continue
                    }
                    val mapped = pkgs.mapNotNull { pkg ->
                        try {
                            @Suppress("DEPRECATION")
                            val info = pm.getPackageInfo(pkg, MATCH_UNINSTALLED_PACKAGES)
                            val drawable = info.applicationInfo?.loadIcon(pm) ?: pm.defaultActivityIcon
                            Entry(
                                policy = policy,
                                packageName = info.packageName,
                                isSharedUid = info.sharedUserId != null,
                                appName = info.applicationInfo?.getLabel(pm) ?: info.packageName,
                                icon = drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                            )
                        } catch (e: PackageManager.NameNotFoundException) {
                            null
                        }
                    }
                    if (mapped.isEmpty()) {
                        db.delete(policy.uid)
                        continue
                    }
                    result.addAll(mapped)
                }
                result.sortedWith(compareBy({ it.appName.lowercase(Locale.ROOT) }, { it.packageName }))
            }
            entries = loaded
            _state.value = State(loading = false, available = true, items = snapshot())
        }
    }

    fun setPolicy(item: Item, policy: Int) {
        val entry = entry(item) ?: return
        viewModelScope.launch {
            entry.policy.policy = policy
            db.update(entry.policy)
            publish()
            val res = if (policy >= SuPolicy.ALLOW) CoreR.string.su_snack_grant else CoreR.string.su_snack_deny
            _messages.tryEmit(Message(res, item.appName))
        }
    }

    fun setNotification(item: Item, enabled: Boolean) {
        val entry = entry(item) ?: return
        viewModelScope.launch {
            entry.policy.notification = enabled
            db.update(entry.policy)
            publish()
            val res = if (enabled) CoreR.string.su_snack_notif_on else CoreR.string.su_snack_notif_off
            _messages.tryEmit(Message(res, item.appName))
        }
    }

    fun setLogging(item: Item, enabled: Boolean) {
        val entry = entry(item) ?: return
        viewModelScope.launch {
            entry.policy.logging = enabled
            db.update(entry.policy)
            publish()
            val res = if (enabled) CoreR.string.su_snack_log_on else CoreR.string.su_snack_log_off
            _messages.tryEmit(Message(res, item.appName))
        }
    }

    /** Removes the policy, and with it every package that shares the UID. */
    fun revoke(item: Item) {
        viewModelScope.launch {
            db.delete(item.uid)
            entries = entries.filter { it.policy.uid != item.uid }
            publish()
        }
    }

    private fun entry(item: Item) = entries.firstOrNull { it.packageName == item.packageName && it.policy.uid == item.uid }

    private fun publish() = _state.update { it.copy(items = snapshot()) }

    private fun snapshot() = entries.map {
        Item(
            key = "${it.policy.uid}:${it.packageName}",
            uid = it.policy.uid,
            packageName = it.packageName,
            appName = it.appName,
            isSharedUid = it.isSharedUid,
            policy = it.policy.policy,
            notification = it.policy.notification,
            logging = it.policy.logging,
            icon = it.icon,
        )
    }

    private companion object {
        /** Large enough for the 80 dp icon in the detail panel at 2x density. */
        const val ICON_PX = 160
    }
}
