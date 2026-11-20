package com.topjohnwu.magisk.tv.flash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.tasks.FlashZip
import com.topjohnwu.magisk.core.tasks.MagiskInstaller
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import com.topjohnwu.magisk.tv.FlashMode
import com.topjohnwu.magisk.tv.TvRoute
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.Collections

/**
 * Runs the operation of a [TvRoute.Flash] exactly once and exposes its console output. Ported from
 * the phone UI's FlashViewModel and ActionViewModel.
 */
class FlashViewModel : ViewModel() {

    enum class State { FLASHING, SUCCESS, FAILED }

    private val _state = MutableStateFlow(State.FLASHING)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Console lines in arrival order. Only touched on the main thread. */
    val lines = mutableStateListOf<String>()

    /** Whether a successful run should offer a reboot. */
    var showReboot by mutableStateOf(Info.isRooted)
        private set

    private var started = false
    private var route: TvRoute.Flash? = null

    private val logItems: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // CallbackList delivers on the main thread by default
    private val outItems = object : CallbackList<String>() {
        override fun onAddElement(e: String?) {
            e ?: return
            lines.add(e)
            logItems.add(e)
        }
    }

    /** Starts the operation; later calls (recomposition, rotation) do nothing. */
    fun start(route: TvRoute.Flash) {
        if (started) return
        started = true
        this.route = route

        viewModelScope.launch {
            val success = when (route.mode) {
                FlashMode.PatchFile -> {
                    showReboot = false
                    val uri = route.uri?.toUri()
                    if (uri == null) invalid() else MagiskInstaller.Patch(uri, outItems, logItems).exec()
                }

                FlashMode.DirectInstall ->
                    if (Info.isEmulator) {
                        MagiskInstaller.Emulator(outItems, logItems).exec()
                    } else {
                        MagiskInstaller.Direct(outItems, logItems).exec()
                    }

                FlashMode.InactiveSlot -> {
                    showReboot = false
                    MagiskInstaller.SecondSlot(outItems, logItems).exec()
                }

                FlashMode.Uninstall -> {
                    showReboot = false
                    MagiskInstaller.Uninstall(outItems, logItems).exec()
                }

                FlashMode.ModuleZip -> {
                    val uri = route.uri?.toUri()
                    if (uri == null) invalid() else FlashZip(uri, outItems, logItems).exec()
                }

                FlashMode.ModuleAction -> {
                    showReboot = false
                    val id = route.id
                    if (id == null) invalid() else runAction(id)
                }
            }
            _state.value = if (success) State.SUCCESS else State.FAILED
        }
    }

    private fun invalid(): Boolean {
        outItems.add("! Invalid request")
        return false
    }

    private suspend fun runAction(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Shell.cmd("run_action '$id'").to(outItems, logItems).exec().isSuccess
        } catch (e: IOException) {
            Timber.e(e)
            false
        }
    }

    /** Saves the console output to Downloads and returns the saved path, or null on failure. */
    suspend fun saveLog(): String? = withContext(Dispatchers.IO) {
        try {
            val stamp = System.currentTimeMillis().toTime(timeFormatStandard)
            val current = route
            val name = if (current?.mode == FlashMode.ModuleAction) {
                "%s_action_log_%s.log".format(current.label ?: current.id.orEmpty(), stamp)
            } else {
                "magisk_install_log_%s.log".format(stamp)
            }
            val file = MediaStoreUtils.getFile(name)
            file.uri.outputStream().bufferedWriter().use { writer ->
                synchronized(logItems) {
                    logItems.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
            }
            file.toString()
        } catch (e: IOException) {
            Timber.e(e)
            null
        }
    }
}
