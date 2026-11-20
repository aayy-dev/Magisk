package com.topjohnwu.magisk.ui.surequest

import com.topjohnwu.magisk.arch.ActivityExecutor
import com.topjohnwu.magisk.arch.UIActivity
import com.topjohnwu.magisk.arch.ViewEvent
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.tv.surequest.showTvSuRequest

/**
 * Android TV counterpart of [com.topjohnwu.magisk.events.ShowUIEvent]: shows the Compose prompt
 * instead of the databinding layout. Choices go to the same view model methods the phone dialog uses.
 */
class ShowTvUIEvent : ViewEvent(), ActivityExecutor {
    override fun invoke(activity: UIActivity<*>) {
        val vm = activity.viewModel as SuRequestViewModel
        showTvSuRequest(
            activity = activity,
            icon = vm.icon,
            title = vm.title,
            packageName = vm.packageName,
            totalSeconds = vm.timeoutSeconds,
            state = vm.tvState,
            accessibilityDelegate = if (Config.suTapjack) SuRequestViewModel.EmptyAccessibilityDelegate else null,
            onSelect = vm::selectDuration,
            onInteract = { vm.spinnerTouched() },
            onGrant = vm::grantPressed,
            onDeny = vm::denyPressed,
        )
    }
}
