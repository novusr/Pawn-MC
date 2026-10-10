package com.rvdjv.pawnmc.`interface`.main

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * The single ongoing notification that keeps the current PawnMC session alive.
 *
 * It mirrors the compile state of the mounted screen (`Idle`, `Success`, `Failed`,
 * `Compiling`) so the user can watch a build from the notification shade, and it is
 * dismissed as soon as the session ends. The channel is created with a low importance
 * and no sound or vibration: this is a status readout, not an alert, and the user can
 * switch the whole channel off from Android's notification settings.
 */
object MainNotification {

    const val CHANNEL_ID = "pawnmc_session"
    const val NOTIFICATION_ID = 1001

    fun update(
        context: Context,
        status: String,
        isCompiling: Boolean,
        outputText: String
    ) {
        if (!notificationsAllowed(context)) return

        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lastLine = outputText
            .lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.trim()
            ?.take(120)
            ?: ""

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.rvdjv.pawnmc.R.mipmap.ic_launcher)
            .setContentTitle("PawnMC \u2014 $status")
            .setContentText(lastLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lastLine))
            .setContentIntent(contentIntent)
            .setOngoing(isCompiling)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun notificationsAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "PawnMC session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the active PawnMC session and its compile status visible"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }
}
