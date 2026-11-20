package com.topjohnwu.magisk.tv.denylist

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_ACTIVITIES
import android.content.pm.PackageManager.GET_PROVIDERS
import android.content.pm.PackageManager.GET_RECEIVERS
import android.content.pm.PackageManager.GET_SERVICES
import android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.ProcessCompat
import com.topjohnwu.superuser.Shell
import java.util.TreeSet

/** Package name `magisk --denylist` uses for isolated processes, which belong to no single app. */
internal const val ISOLATED_MAGIC = "isolated"

/** One line of `magisk --denylist ls`: `package|process`. */
internal class CmdlineListItem(line: String) {
    val packageName: String
    val process: String

    init {
        val split = line.split(Regex("\\|"), 2)
        packageName = split[0]
        process = split.getOrElse(1) { packageName }
    }
}

/** A process of an app, and whether it is on the DenyList. Toggling runs the same command as the phone UI. */
internal class DenyProcess(
    val name: String,
    val packageName: String,
    enabled: Boolean,
) {
    var enabled by mutableStateOf(enabled)
        private set

    val isIsolated = packageName == ISOLATED_MAGIC
    val isAppZygote = name.endsWith("_zygote")

    /** Processes that are selected when the whole app is switched on. */
    val isDefault get() = isIsolated || isAppZygote || name == packageName

    val displayName get() = if (isIsolated) "(isolated) $name" else name

    fun set(on: Boolean) {
        if (enabled == on) return
        enabled = on
        val arg = if (on) "add" else "rm"
        Shell.cmd("magisk --denylist $arg $packageName '$name'").submit()
    }

    /** Clears the flag only; the caller already removed the whole package from the list. */
    fun clearLocally() {
        enabled = false
    }
}

internal class DenyApp(
    val info: ApplicationInfo,
    val label: String,
    val processes: List<DenyProcess>,
) {
    val packageName: String get() = info.packageName
    val isSystemApp get() = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    val isApp get() = ProcessCompat.isApplicationUid(info.uid)

    val checkedCount get() = processes.count { it.enabled }
    val isChecked get() = processes.any { it.enabled }

    /** True when every default process is on the list: the state of the app-level switch. */
    val defaultsOn: Boolean
        get() {
            val defaults = processes.filter { it.isDefault }
            return defaults.isNotEmpty() && defaults.all { it.enabled }
        }

    fun setDefaults(on: Boolean) {
        if (on) {
            processes.filter { it.isDefault && !it.enabled }.forEach { it.set(true) }
        } else {
            // One command drops every process of the package; isolated ones are listed under another name
            Shell.cmd("magisk --denylist rm $packageName").submit()
            processes.filter { it.enabled }.forEach {
                if (it.isIsolated) it.set(false) else it.clearLocally()
            }
        }
    }
}

/** Builds the app and its processes from the package manager, marking those already on the list. */
@SuppressLint("InlinedApi")
internal fun buildDenyApp(
    info: ApplicationInfo,
    label: String,
    pm: PackageManager,
    denyList: List<CmdlineListItem>,
): DenyApp {
    val relevant = denyList.filter { it.packageName == info.packageName || it.packageName == ISOLATED_MAGIC }

    fun process(name: String, pkg: String = info.packageName) =
        DenyProcess(name, pkg, relevant.any { it.process == name && it.packageName == pkg })

    fun ComponentInfo.procName(): String = processName
        ?: applicationInfo.processName
        ?: applicationInfo.packageName

    fun Array<out ComponentInfo>?.toProcesses() = orEmpty().map { process(it.procName()) }

    fun Array<ServiceInfo>?.toServiceProcesses() = orEmpty().map {
        val isolated = (it.flags and ServiceInfo.FLAG_ISOLATED_PROCESS) != 0
        val appZygote = (it.flags and ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0
        when {
            isolated && appZygote -> process("${info.processName ?: info.packageName}_zygote")
            isolated -> {
                val name = if (SDK_INT >= Build.VERSION_CODES.Q) "${it.procName()}:${it.name}" else it.procName()
                process(name, ISOLATED_MAGIC)
            }
            else -> process(it.procName())
        }
    }

    val flags = MATCH_DISABLED_COMPONENTS or MATCH_UNINSTALLED_PACKAGES or
        GET_ACTIVITIES or GET_SERVICES or GET_RECEIVERS or GET_PROVIDERS
    val packageInfo = try {
        pm.getPackageInfo(info.packageName, flags)
    } catch (e: Exception) {
        // Over the binder transfer limit: parse the package file locally
        pm.getPackageArchiveInfo(info.sourceDir, flags)
    }

    val set = TreeSet<DenyProcess>(compareBy({ it.name }, { it.isIsolated }))
    if (packageInfo != null) {
        set += packageInfo.activities.toProcesses()
        set += packageInfo.services.toServiceProcesses()
        set += packageInfo.receivers.toProcesses()
        set += packageInfo.providers.toProcesses()
    }
    return DenyApp(info, label, set.toList())
}
