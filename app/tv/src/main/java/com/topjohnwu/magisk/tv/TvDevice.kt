package com.topjohnwu.magisk.tv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** True on Android TV / Google TV, which is driven by a D-pad remote instead of touch. */
val Context.isTelevision: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
