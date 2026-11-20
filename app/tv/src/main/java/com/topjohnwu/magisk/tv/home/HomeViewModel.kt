package com.topjohnwu.magisk.tv.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.core.BuildConfig
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.di.ServiceLocator
import com.topjohnwu.magisk.core.ktx.await
import com.topjohnwu.magisk.core.model.module.LocalModule
import com.topjohnwu.magisk.core.model.su.SuPolicy
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the Home dashboard, built from the same core sources as the phone UI. */
class HomeViewModel : ViewModel() {

    enum class MagiskState { INVALID, UNSUPPORTED, OUTDATED, UP_TO_DATE }

    data class AppInfo(
        val loading: Boolean = true,
        /** "version (code)" of the latest release, or null when it could not be fetched. */
        val latest: String? = null,
        val updateAvailable: Boolean = false,
        val changelog: String = "",
    )

    data class State(
        val app: AppInfo = AppInfo(),
        /** Apps that currently have root access. */
        val suGranted: Int = 0,
        val suTotal: Int = 0,
        val modulesEnabled: Int = 0,
        val modulesTotal: Int = 0,
        val modulesAvailable: Boolean = false,
        /** Non-zero when `env_check` found a problem Magisk can repair. */
        val envProblem: Int = 0,
        val noticeVisible: Boolean = Config.safetyNotice,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val magiskState: MagiskState
        get() = when {
            Info.isRooted && Info.env.isUnsupported -> MagiskState.UNSUPPORTED
            !Info.env.isActive -> MagiskState.INVALID
            Info.env.versionCode < BuildConfig.APP_VERSION_CODE -> MagiskState.OUTDATED
            else -> MagiskState.UP_TO_DATE
        }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            launch { loadApp() }
            launch { loadCounts() }
            launch { checkEnv() }
        }
    }

    private suspend fun loadApp() {
        _state.update { it.copy(app = it.app.copy(loading = true)) }
        val info = runCatching { Info.fetchUpdate(ServiceLocator.networkService) }.getOrNull()
        _state.update {
            it.copy(
                app = if (info == null) {
                    AppInfo(loading = false)
                } else {
                    val debug = Config.updateChannel == Config.Value.DEBUG_CHANNEL
                    AppInfo(
                        loading = false,
                        latest = "${info.version} (${info.versionCode})" + if (debug) " (D)" else "",
                        updateAvailable = BuildConfig.APP_VERSION_CODE < info.versionCode,
                        changelog = info.note,
                    )
                },
            )
        }
    }

    private suspend fun loadCounts() {
        if (Info.showSuperUser) {
            val policies: List<SuPolicy> = withContext(Dispatchers.IO) {
                runCatching { ServiceLocator.policyDB.fetchAll() }.getOrDefault(emptyList())
            }
            _state.update {
                it.copy(
                    suGranted = policies.count { p -> p.policy >= SuPolicy.ALLOW },
                    suTotal = policies.size,
                )
            }
        }
        if (Info.env.isActive && withContext(Dispatchers.IO) { LocalModule.loaded() }) {
            // enable/remove are file checks through the root service: keep them off the main thread
            val (total, enabled) = withContext(Dispatchers.IO) {
                val modules = runCatching { LocalModule.installed() }.getOrDefault(emptyList())
                modules.size to modules.count { m -> m.enable && !m.remove }
            }
            _state.update { it.copy(modulesAvailable = true, modulesTotal = total, modulesEnabled = enabled) }
        }
    }

    private suspend fun checkEnv() {
        if (checkedEnv || magiskState == MagiskState.INVALID || magiskState == MagiskState.UNSUPPORTED) return
        val code = Shell.cmd("env_check ${Info.env.versionString} ${Info.env.versionCode}").await().code
        checkedEnv = true
        _state.update { it.copy(envProblem = code) }
    }

    fun hideNotice() {
        Config.safetyNotice = false
        _state.update { it.copy(noticeVisible = false) }
    }

    fun clearEnvProblem() {
        _state.update { it.copy(envProblem = 0) }
    }

    private companion object {
        var checkedEnv = false
    }
}
