package com.rvdjv.pawnmc.`interface`.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.DecimalFormat

/**
 * Storage access and file-size formatting for the main screen.
 *
 * Split out of `MainScreen.kt` because none of it is UI: the all-files permission check,
 * the two Intents that lead to the right Settings page on either side of API 30, and the
 * size label on the compile card. Keeping them here means the storage policy can be read
 * — and reasoned about — without scrolling past a thousand lines of Compose.
 *
 * `formatFileSize` is the one presentation helper: binary units with a single decimal are
 * a decision about the compile card, and the card is its only caller.
 */

internal fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val idx = digitGroups.coerceAtMost(units.size - 1)
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, idx.toDouble())) + " " + units[idx]
}

// helper functions
fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun requestStoragePermission(
    context: Context,
    manageStorageLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    legacyPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        manageStorageLauncher.launch(intent)
    } else {
        legacyPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}

