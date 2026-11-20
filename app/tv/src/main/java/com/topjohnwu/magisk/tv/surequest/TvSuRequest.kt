package com.topjohnwu.magisk.tv.surequest

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kotlinx.coroutines.flow.StateFlow

/** True on Android TV, where the superuser prompt is drawn by [TvSuRequestPrompt]. */
val Context.isTelevision: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature("android.software.leanback")

/** Live state of a request, owned by the view model and shown by the prompt. */
data class TvSuState(
    /** Seconds until the request is denied automatically; 0 once the timer was stopped or ended. */
    val seconds: Int = 0,
    /** Grant stays disabled for the first second so a stray press cannot allow root. */
    val grantEnabled: Boolean = false,
    /** Index into the grant durations (`allow_timeout`), as the phone spinner. */
    val selected: Int = 0,
)

/**
 * Replaces the phone dialog on TV: hosts the Compose prompt in [activity] as a full-screen scrim.
 * Every choice goes back through the callbacks, which are the same view model calls the phone
 * dialog makes.
 */
fun showTvSuRequest(
    activity: ComponentActivity,
    icon: Drawable,
    title: String,
    packageName: String,
    totalSeconds: Int,
    state: StateFlow<TvSuState>,
    accessibilityDelegate: View.AccessibilityDelegate?,
    onSelect: (Int) -> Unit,
    onInteract: () -> Unit,
    onGrant: () -> Unit,
    onDeny: () -> Unit,
) {
    // The activity uses a floating dialog theme; on TV the prompt covers the whole screen
    activity.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    activity.window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    activity.setContent {
        TvSuRequestPrompt(
            icon = icon,
            title = title,
            packageName = packageName,
            totalSeconds = totalSeconds,
            state = state,
            onSelect = onSelect,
            onInteract = onInteract,
            onGrant = onGrant,
            onDeny = onDeny,
        )
    }
    // Same protection as the phone dialog: keep accessibility services out of the prompt
    activity.window.decorView.accessibilityDelegate = accessibilityDelegate
}
