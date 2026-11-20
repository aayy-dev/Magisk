package com.topjohnwu.magisk.tv.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.topjohnwu.magisk.tv.TvActivity

/** The hosting [TvActivity], needed for permission requests and file pickers. */
@Composable
fun rememberTvActivity(): TvActivity {
    val context = LocalContext.current
    return remember(context) { checkNotNull(context.findTvActivity()) { "Not hosted by TvActivity" } }
}

private tailrec fun Context.findTvActivity(): TvActivity? = when (this) {
    is TvActivity -> this
    is ContextWrapper -> baseContext.findTvActivity()
    else -> null
}
