package com.topjohnwu.magisk.tv.logs

import android.system.Os
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.timeFormatStandard
import com.topjohnwu.magisk.core.ktx.toTime
import com.topjohnwu.magisk.core.ktx.toast
import com.topjohnwu.magisk.core.model.su.SuLog
import com.topjohnwu.magisk.core.utils.MediaStoreUtils
import com.topjohnwu.magisk.core.utils.MediaStoreUtils.outputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileInputStream
import java.io.IOException
import com.topjohnwu.magisk.core.R as CoreR

/** Superuser and Magisk logs, ported from the phone UI's LogViewModel. */
class LogsViewModel : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val suLogs: List<SuLog> = emptyList(),
        /** The last [MAGISK_LOG_TAIL] lines of the Magisk log. */
        val magiskLines: List<String> = emptyList(),
        val magiskTotalLines: Int = 0,
    ) {
        val magiskTruncated get() = magiskTotalLines > magiskLines.size
    }

    private val repo = ServiceLocator.logRepo

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The whole Magisk log, kept for saving. */
    private var magiskRaw = ""

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (raw, su) = withContext(Dispatchers.IO) {
                repo.fetchMagiskLogs() to repo.fetchSuLogs().toList()
            }
            magiskRaw = raw
            // Splitting a big log is the slow part, keep it off the main thread
            val lines = withContext(Dispatchers.Default) { raw.trimEnd('\n').split('\n') }
            val tail = if (raw.isBlank()) emptyList() else lines.takeLast(MAGISK_LOG_TAIL)
            _state.value = State(
                loading = false,
                suLogs = su,
                magiskLines = tail,
                magiskTotalLines = if (raw.isBlank()) 0 else lines.size,
            )
        }
    }

    fun clearSuLog() {
        viewModelScope.launch {
            repo.clearLogs()
            AppContext.toast(CoreR.string.logs_cleared, Toast.LENGTH_SHORT)
            load()
        }
    }

    fun clearMagiskLog() {
        repo.clearMagiskLogs {
            AppContext.toast(CoreR.string.logs_cleared, Toast.LENGTH_SHORT)
            load()
        }
    }

    /** Saves a device-info + Magisk log bundle to Downloads; returns the saved path or null. */
    suspend fun saveMagiskLog(): String? = withContext(Dispatchers.IO) {
        try {
            val filename = "magisk_log_%s.log".format(System.currentTimeMillis().toTime(timeFormatStandard))
            val logFile = MediaStoreUtils.getFile(filename)
            logFile.uri.outputStream().bufferedWriter().use { file ->
                file.write("---Detected Device Info---\n\n")
                file.write("isAB=${Info.isAB}\n")
                file.write("isSAR=${Info.isSAR}\n")
                file.write("ramdisk=${Info.ramdisk}\n")
                val uname = Os.uname()
                file.write("kernel=${uname.sysname} ${uname.machine} ${uname.release} ${uname.version}\n")

                file.write("\n\n---System Properties---\n\n")
                ProcessBuilder("getprop").start().inputStream.reader().use { it.copyTo(file) }

                file.write("\n\n---Environment Variables---\n\n")
                System.getenv().forEach { (key, value) -> file.write("$key=$value\n") }

                file.write("\n\n---System MountInfo---\n\n")
                FileInputStream("/proc/self/mountinfo").reader().use { it.copyTo(file) }

                file.write("\n---Magisk Logs---\n")
                file.write("${Info.env.versionString} (${Info.env.versionCode})\n\n")
                if (Info.env.isActive) file.write(magiskRaw)

                file.write("\n---Manager Logs---\n")
                file.write("${BuildConfig.APP_VERSION_NAME} (${BuildConfig.APP_VERSION_CODE})\n\n")
                ProcessBuilder("logcat", "-d").start().inputStream.reader().use { it.copyTo(file) }
            }
            logFile.toString()
        } catch (e: IOException) {
            Timber.e(e)
            null
        }
    }

    companion object {
        const val MAGISK_LOG_TAIL = 800
    }
}
