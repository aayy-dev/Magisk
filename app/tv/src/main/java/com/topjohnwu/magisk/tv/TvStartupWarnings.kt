package com.topjohnwu.magisk.tv

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.topjohnwu.magisk.core.Const
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.tv.ui.TvAction
import com.topjohnwu.magisk.tv.ui.TvDialog
import java.io.File
import com.topjohnwu.magisk.core.R as CoreR

private enum class Warning { UnsupportedMagisk, OtherSu, SystemApp, ExternalStorage }

/**
 * The abnormal-state warnings the phone UI shows at startup (unsupported Magisk, a competing `su`,
 * running as a system app, installed on external storage), one after the other. A `su` that is not
 * Magisk's hands out root without Magisk's prompt, so these must not be skipped on TV.
 */
@Composable
fun TvStartupWarnings() {
    val context = LocalContext.current
    val warnings = remember {
        buildList {
            if (Info.isRooted && Info.env.isUnsupported) add(Warning.UnsupportedMagisk)
            if (!Info.isEmulator && Info.env.isActive && System.getenv("PATH")
                    ?.split(':')
                    ?.filterNot { File("$it/magisk").exists() }
                    ?.any { File("$it/su").exists() } == true
            ) {
                add(Warning.OtherSu)
            }
            val flags = context.applicationInfo.flags
            if (flags and ApplicationInfo.FLAG_SYSTEM != 0) add(Warning.SystemApp)
            if (flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) add(Warning.ExternalStorage)
        }
    }
    // Remembered across recreation, so a warning that was acknowledged does not come back
    var shown by rememberSaveable { mutableIntStateOf(0) }
    val warning = warnings.getOrNull(shown) ?: return

    TvDialog(
        title = stringResource(
            if (warning == Warning.UnsupportedMagisk) CoreR.string.unsupport_magisk_title else CoreR.string.unsupport_general_title,
        ),
        message = when (warning) {
            Warning.UnsupportedMagisk -> stringResource(CoreR.string.unsupport_magisk_msg, Const.Version.MIN_VERSION)
            Warning.OtherSu -> stringResource(CoreR.string.unsupport_other_su_msg)
            Warning.SystemApp -> stringResource(CoreR.string.unsupport_system_app_msg)
            Warning.ExternalStorage -> stringResource(CoreR.string.unsupport_external_storage_msg)
        },
        // Like the stock dialog, this is only left through OK
        onDismiss = {},
        actions = listOf(TvAction(stringResource(android.R.string.ok), isDefault = true, onClick = { shown++ })),
    )
}
