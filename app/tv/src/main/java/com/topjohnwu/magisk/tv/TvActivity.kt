package com.topjohnwu.magisk.tv

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.topjohnwu.magisk.core.Config
import com.topjohnwu.magisk.core.base.ActivityExtension
import com.topjohnwu.magisk.core.base.SplashController
import com.topjohnwu.magisk.core.base.SplashScreenHost
import com.topjohnwu.magisk.core.wrap

/**
 * Entry point of the Android TV UI. Startup (root shell, [com.topjohnwu.magisk.core.Config],
 * app state) goes through the same [SplashController] as the phone UI.
 */
class TvActivity : ComponentActivity(), SplashScreenHost {

    override val extension = ActivityExtension(this)
    override val splashController = SplashController(this)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.wrap())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        splashController.preOnCreate()
        super.onCreate(savedInstanceState)
        extension.onCreate(savedInstanceState)
        splashController.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        splashController.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        extension.onSaveInstanceState(outState)
    }

    override fun onCreateUi(savedInstanceState: Bundle?) {
        setContent { TvApp() }
        // The background update check needs it on Android 13+; stock asks at startup too
        if (Config.checkUpdate) {
            withPermission(Manifest.permission.POST_NOTIFICATIONS) { Config.checkUpdate = it }
        }
    }

    // Only reachable when running as a stub without root, which cannot launch this activity
    override fun showInvalidStateMessage() {
        runOnUiThread {
            Toast.makeText(this, com.topjohnwu.magisk.core.R.string.unsupport_nonroot_stub_title, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
