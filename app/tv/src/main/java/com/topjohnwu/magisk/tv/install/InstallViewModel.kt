package com.topjohnwu.magisk.tv.install

import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.AppContext
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.base.ContentResultCallback
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File
import java.io.IOException
import com.topjohnwu.magisk.core.R as CoreR

/** Release notes of the version being installed, cached like the phone UI does. */
class InstallViewModel : ViewModel() {

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val noteFile = File(AppContext.cacheDir, "${BuildConfig.APP_VERSION_CODE}.md")
                val text = if (noteFile.exists()) {
                    noteFile.readText()
                } else {
                    val note = ServiceLocator.networkService.fetchUpdate(BuildConfig.APP_VERSION_CODE)?.note.orEmpty()
                    if (note.isEmpty()) return@launch
                    noteFile.writeText(note)
                    note
                }
                _notes.value = text.toPlainText()
            } catch (e: IOException) {
                Timber.e(e)
            }
        }
    }

    // The notes are Markdown; the TV shows them as plain text
    private fun String.toPlainText() = lineSequence()
        .map { it.trimStart('#', ' ').replace("**", "").replace("`", "") }
        .joinToString("\n")
        .trim()
}

/** Hands the file chosen in the system picker back to the install screen. */
object PatchPicker {
    val result = MutableStateFlow<Uri?>(null)
}

@Parcelize
class PatchUriCallback : ContentResultCallback {
    override fun onActivityLaunch() {
        AppContext.toast(CoreR.string.patch_file_msg, Toast.LENGTH_LONG)
    }

    override fun onActivityResult(result: Uri) {
        PatchPicker.result.value = result
    }
}
