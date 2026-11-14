package com.pocketsamp.server.crash_handler

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class Handler private constructor(private val application: Application) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "PocketSAMPHandler"
        const val EXTRA_CRASH_ID = "com.pocketsamp.server.EXTRA_CRASH_ID"

        @Volatile
        private var isInstalled = false

        /** Set once a handler in this process has started writing the report. */
        private var reportStarted = false

        /** True for the one caller that wins the race; false for every later handler. */
        @Synchronized
        fun beginReport(): Boolean {
            if (reportStarted) return false
            reportStarted = true
            return true
        }

        fun install(application: Application) {
            if (isInstalled) return
            synchronized(this) {
                if (!isInstalled) {
                    val handler = Handler(application)
                    Thread.setDefaultUncaughtExceptionHandler(handler)
                    isInstalled = true
                    Log.i(TAG, "Global Handler successfully initialized.")
                }
            }
        }

        fun getDeviceInfo(context: Context): String {
            val packageInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) {
                null
            }

            val versionName = packageInfo?.versionName ?: "1.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 1L
            }

            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val totalRamMb = memInfo.totalMem / (1024 * 1024)
            val availRamMb = memInfo.availMem / (1024 * 1024)

            return buildString {
                appendLine("App Version      : $versionName (Build $versionCode)")
                appendLine("Device Model     : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
                appendLine("Android OS       : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("Build ID         : ${Build.DISPLAY}")
                appendLine("Supported ABIs   : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
                appendLine("System Memory    : ${availRamMb}MB free of ${totalRamMb}MB")
            }
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // One report per process, however many handlers are chained.
        //
        // This handler normally runs last, after PawnMC's own: the host was installed
        // first and therefore chains into this one. Without the guard, one exception
        // would produce a host report *and* a PocketSAMP report - two dumps of the same
        // crash, written twice into the same folder.
        if (!beginReport()) return
        try {
            Log.e(TAG, "Uncaught exception intercepted in thread [${thread.name}]", throwable)

            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(timestamp))

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTrace = sw.toString()

            val crashId = UUID.randomUUID().toString().substring(0, 8)
            val exceptionClass = throwable.javaClass.name
            val message = throwable.message

            val deviceInfo = getDeviceInfo(application)

            val report = Report(
                id = crashId,
                timestamp = timestamp,
                formattedDate = formattedDate,
                exceptionClass = exceptionClass,
                message = message,
                threadName = thread.name,
                deviceInfo = deviceInfo,
                stackTrace = stackTrace
            )

            /*
             * Save crash report persistently to disk: the host's public folder with a
             * `.dmp` when PawnMC installed its reporter, the private folder otherwise.
             */
            val savedReport = Manager.saveCrash(application, report)

            /*
             * Launch dedicated CrashDialog in a clean isolated task.
             *
             * Only inside the PawnMC host: the dialog lives in PocketSAMP's own `:crash`
             * process, and starting that from a host crash screen would replace the
             * system UI for a report the host already shows. Standalone PocketSAMP keeps
             * the upstream behaviour.
             */
            if (savedReport.isFile) {
                val intent = Intent(application, CrashDialog::class.java).apply {
                    putExtra(EXTRA_CRASH_ID, crashId)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                }
                runCatching { application.startActivity(intent) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal failure inside Handler", e)
        } finally {
            /*
             * Terminate the crashing process cleanly to prevent OS "App has stopped" dialog
             */
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }
}
