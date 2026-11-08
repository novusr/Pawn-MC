package com.rvdjv.pawnmc.apps_data.data_config

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * One-shot system notifications for compiler-layer events.
 *
 * The compiler layer cannot import the UI package, so this helper lives next to
 * [ContentConfig] and reuses the same icon. It posts:
 *
 *  - `restart_needed` - the stored compiler version changed on its own (the
 *    `__addressof` auto-recovery, for example) and the loaded engine still
 *    matches the old version until the app restarts.
 */
object SystemNotification {

    const val CHANNEL_ID = "pawnmc_compiler_events"
    const val RESTART_NEEDED_ID = 1002

    @Volatile
    private var hostContext: Context? = null

    /** Called from the app entry point so the data layer has a context to notify with. */
    fun attachHost(context: Context?) {
        hostContext = context?.applicationContext
    }

    /** Context handed to [restartNeeded]; null when the app has not attached one yet. */
    fun contextOrNull(): Context? = hostContext

    private fun notificationsAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
                "PawnMC compiler events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Tells you when a compiler change needs an app restart"
                setShowBadge(false)
            }
        )
    }

    /**
     * Posts the "compiler changed, restart to apply" notification.
     *
     * A no-op when there is no host context yet or notifications are not granted -
     * the switch itself is already written, the notification is only the heads-up.
     */
    fun restartNeeded(context: Context?, fromVersion: String, toVersion: String) {
        val target = context ?: return
        if (!notificationsAllowed(target)) return
        ensureChannel(target)

        val launchIntent = target.packageManager.getLaunchIntentForPackage(target.packageName)
        val contentIntent = android.app.PendingIntent.getActivity(
            target,
            0,
            launchIntent ?: Intent(),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(target, CHANNEL_ID)
            // Notification small icons must be a flat alpha-mask drawable; the
            // adaptive `mipmap/ic_launcher` (a white-on-grey square) renders as
            // the grey box the user reported. `ic_launcher_foreground` is the
            // vector mask the launcher itself composes.
            .setSmallIcon(com.rvdjv.pawnmc.R.drawable.ic_launcher_foreground)
            .setContentTitle("Restart to apply the compiler switch")
            .setContentText("$fromVersion became $toVersion automatically. Restart PawnMC to load it.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$fromVersion became $toVersion automatically. Restart PawnMC so the new " +
                        "compiler is loaded - the current session is still running the old one.")
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        @Suppress("MissingPermission")
        runCatching {
            NotificationManagerCompat.from(target).notify(RESTART_NEEDED_ID, notification)
        }
    }
}
