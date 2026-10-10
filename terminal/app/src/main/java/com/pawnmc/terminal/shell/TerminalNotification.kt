package com.pawnmc.terminal.shell

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
 * The session notification of the sandbox terminal.
 *
 * A shell started from the editor keeps running after the editor is closed - that is the
 * whole point of [TerminalService] outliving the composable - but until now nothing said
 * so: the only way to find the session again was to reopen the editor and the sandbox
 * button. This notification is that missing handle. It is posted while at least one
 * session is alive, names the folder the shell is running in, and tapping it reopens the
 * app so the user can return to the session.
 *
 * The channel is deliberately separate from the compile-status one in the app module so
 * the user can silence the terminal without silencing their build status, and it is
 * created at `LOW` importance with no sound: a long-lived shell is a state, not an event.
 *
 * Closing the notification does not kill the shell. It *is* the session's indicator, though,
 * so [TerminalService.terminateAll] cancels it - and stopping the service (which Android
 * may do together with the app) takes the notification with it, which is the honest
 * behaviour: nothing is running when the process is gone.
 */
internal object TerminalNotification {

    const val CHANNEL_ID = "pawnmc_terminal"

    /** Stable id so repeated updates replace the previous notification. */
    const val NOTIFICATION_ID = 2001

    /** Posts or refreshes the "1 session" notification for [workingDirectory]. */
    fun update(context: Context, workingDirectory: String?) {
        if (!notificationsAllowed(context)) return

        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        val where = workingDirectory?.takeIf { it.isNotBlank() } ?: "/home"

        // Opening the app is the only sensible destination: the terminal is an overlay
        // owned by the editor, so there is no deep link to jump straight back into the
        // shell, and pretending otherwise would land on a blank screen.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Sandbox terminal session")
            .setContentText("Running in $where")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Ubuntu shell under proot is still running, working in $where.")
            )
            .setContentIntent(contentIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** Removes the notification; called when the last session ends or the service stops. */
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
                "Sandbox terminal",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that a sandbox terminal session is still running"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }
}
