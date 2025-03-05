package net.rpcs3.ui.common

import android.content.Context
import android.net.Uri
import net.rpcs3.FirmwareRepository
import net.rpcs3.ProgressRepository
import net.rpcs3.RPCS3
import kotlin.concurrent.thread


/**
 * Created using Android Studio
 * User: Muhammad Ashhal
 * Date: Wed, Mar 05, 2025
 * Time: 3:36 pm
 */

fun Context.installFirmware(
    uri: Uri
) {
    val assetDescriptor = contentResolver.openAssetFileDescriptor(uri, "r")
    val fd = assetDescriptor?.parcelFileDescriptor?.fd

    if (fd != null) {
        val installProgress = ProgressRepository.create(
            context = this,
            title = "Firmware Installtion"
        ) { entry ->
            if (entry.isFinished()) {
                assetDescriptor.close()
                FirmwareRepository.progressChannel.value = null
            }
        }
        FirmwareRepository.progressChannel.value = installProgress

        thread(isDaemon = true) {
            if (!RPCS3.instance.installFw(fd, installProgress)) {
                try {
                    ProgressRepository.onProgressEvent(installProgress, -1, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                assetDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    } else {
        try {
            assetDescriptor?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}