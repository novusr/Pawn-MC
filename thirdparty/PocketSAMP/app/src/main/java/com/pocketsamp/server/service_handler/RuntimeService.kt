package com.pocketsamp.server.service_handler

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pocketsamp.server.Locales
import com.pocketsamp.server.MainActivity
import com.pocketsamp.server.R
import com.pocketsamp.server.configuration.DirectoryManager
import com.pocketsamp.server.crash_handler.Handler
import com.pocketsamp.server.crash_handler.Manager
import com.pocketsamp.server.crash_handler.Report
import com.pocketsamp.server.engine_handler.Server
import com.pocketsamp.server.engine_handler.ThirdExtractor
import com.pocketsamp.server.engine_handler.ThirdMariaDb
import com.pocketsamp.server.engine_handler.ThirdRuntime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

enum class Status {
    STOPPED,
    INITIALIZING,
    STARTING,
    RUNNING,
    STOPPING
}

class RuntimeService : Service() {

    companion object {
        const val CHANNEL_ID = "pocketsamp_active_service_v2"
        const val NOTIFICATION_ID = 7777

        const val ACTION_START = "com.pocketsamp.server.ACTION_START"
        const val ACTION_STOP = "com.pocketsamp.server.ACTION_STOP"
        @Volatile private var consoleLogSink: ((String) -> Unit)? = null
        /** Sets the PocketSAMP console persistence callback provided by the host app. */
        fun setConsoleLogSink(sink: (String) -> Unit) {
            consoleLogSink = sink
        }

        /** True while the service (and therefore the engine) is alive. */
        @Volatile
        var isServiceRunning: Boolean = false
            private set
        private val _serverStatus = MutableStateFlow(Status.STOPPED)
        val serverStatus: StateFlow<Status> = _serverStatus.asStateFlow()

        private val _isDbRunning = MutableStateFlow(false)
        val isDbRunning: StateFlow<Boolean> = _isDbRunning.asStateFlow()

        private val _logFeed = MutableStateFlow<List<String>>(emptyList())
        val logFeed: StateFlow<List<String>> = _logFeed.asStateFlow()

        private val _lanIp = MutableStateFlow("127.0.0.1")
        val lanIp: StateFlow<String> = _lanIp.asStateFlow()

        private val logLock = Any()

        fun appendLog(line: String) {
            runCatching { consoleLogSink?.invoke(line) }
            synchronized(logLock) {
                val current = _logFeed.value.toMutableList()
                if (current.size > 500) {
                    current.removeAt(0)
                }
                current.add(line)
                _logFeed.value = current
            }
        }

        fun clearLogs() {
            synchronized(logLock) {
                _logFeed.value = emptyList()
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private lateinit var assetExtractor: ThirdExtractor
    private lateinit var serverEngine: Server
    private lateinit var mariaDbEngine: ThirdMariaDb

    inner class LocalBinder : Binder() {
        fun getService(): Service = this@RuntimeService
    }

    private val binder = LocalBinder()

    /** Whether [startForeground] already ran; guards the one-shot call Android requires. */
    @Volatile
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        assetExtractor = ThirdExtractor(this)
        serverEngine = Server(this)
        mariaDbEngine = ThirdMariaDb(this)

        /*
         * Handle process termination (e.g. Android Phantom Process Killer / OOM)
         */
        serverEngine.onExit = { exitCode ->
            val prefs = getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
            val shouldBeRunning = prefs.getBoolean("server_running_state", false)
            fun t(key: String): String = Locales.text(Locales.current, key)

            if (exitCode == 137) {
                appendLog(t("notify.exit137.log"))
                appendLog(t("log.exit137.fix1"))
                appendLog(t("log.exit137.fix2"))
                appendLog(t("log.exit137.fix3"))
                updateNotification(t("notify.exit137"))
            } else {
                appendLog(t("log.exited").replace("{code}", exitCode.toString()))
                updateNotification(t("notify.stopped").replace("{code}", exitCode.toString()))
            }

            if (shouldBeRunning) {
                appendLog(t("log.restoring"))
                serviceScope.launch {
                    delay(2000)
                    if (prefs.getBoolean("server_running_state", false) && !serverEngine.isRunning) {
                        try {
                            serverEngine.start(serviceScope)
                            updateNotification(t("notify.active").replace("{ip}", _lanIp.value))
                            appendLog(t("log.restored"))
                        } catch (e: Exception) {
                            appendLog(t("log.restore.failed").replace("{error}", e.message.orEmpty()))
                            _serverStatus.value = Status.STOPPED
                        }
                    }
                }
            } else {
                _serverStatus.value = Status.STOPPED
            }
        }

        /*
         * Stream logs from engines asynchronously without blocking UI thread
         */
        serviceScope.launch(Dispatchers.Default) {
            serverEngine.serverLogs.collect { appendLog(it) }
        }
        serviceScope.launch(Dispatchers.Default) {
            mariaDbEngine.dbLogs.collect { appendLog(it) }
        }

        updateLanIp()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
        if (intent == null) {
            /*
             * Recreated by Android OS after extreme memory pressure
             */
            val shouldBeRunning = prefs.getBoolean("server_running_state", false)
            if (shouldBeRunning && _serverStatus.value != Status.RUNNING) {
                Log.i("Service", "Restoring server execution following OS process recreation.")
                startServers()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                prefs.edit().putBoolean("server_running_state", true).apply()
                startServers()
            }
            ACTION_STOP -> {
                prefs.edit().putBoolean("server_running_state", false).apply()
                stopServers()
            }
        }
        return START_STICKY
    }

    /** Initializes (first run) and starts the MariaDB daemon. */
    private suspend fun initializeDatabase() {
        fun t(key: String): String = Locales.text(Locales.current, key)
        ensureForeground(t("log.db.starting"))
        if (mariaDbEngine.isRunning) mariaDbEngine.stop()

        val databaseInitialized = mariaDbEngine.initializeIfNeeded(serviceScope)
        if (databaseInitialized) {
            mariaDbEngine.start(serviceScope)
            delay(1500)
            _isDbRunning.value = mariaDbEngine.isRunning
            if (!mariaDbEngine.isRunning) {
                appendLog(t("log.db.exited"))
            }
        } else {
            _isDbRunning.value = false
            appendLog(t("log.db.unavailable"))
        }
    }

    /** Starts the foreground notification once, or updates it if already shown. */
    private fun ensureForeground(text: String) {
        if (isForeground) {
            updateNotification(text)
            return
        }
        val notif = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notif, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        isForeground = true
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i("Service", "App task closed; keeping PocketSAMP server active in foreground.")
    }

    private fun startServers() {
        if (_serverStatus.value == Status.RUNNING || _serverStatus.value == Status.STARTING) return

        fun t(key: String): String = Locales.text(Locales.current, key)
        clearLogs()
        _serverStatus.value = Status.STARTING
        acquireLocks()

        val notif = buildNotification(t("notify.starting"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notif, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        isForeground = true

        serviceScope.launch {
            try {
                // Download the runtime archives, then unpack each one.
                if (!assetExtractor.isExtracted()) {
                    _serverStatus.value = Status.INITIALIZING
                    appendLog(t("log.archive.prepare"))

                    val runtime = ThirdRuntime(applicationContext)
                    if (!ThirdRuntime.isComplete(applicationContext)) {
                        if (!DirectoryManager.hasStoragePermission(applicationContext)) {
                            appendLog(t("log.archive.permission"))
                            _serverStatus.value = Status.STOPPED
                            return@launch
                        }
                        runtime.sync { line -> appendLog("[Setup] $line") }
                    }

                    if (!ThirdRuntime.isComplete(applicationContext)) {
                        appendLog(t("log.archive.failed"))
                        _serverStatus.value = Status.STOPPED
                        return@launch
                    }

                    appendLog(t("log.archive.extract"))
                    assetExtractor.extractAll { progress ->
                        appendLog("[Setup] $progress")
                        updateNotification(t("notify.setup").replace("{progress}", progress))
                    }
                }

                initializeDatabase()

                updateNotification(t("log.launching"))
                serverEngine.start(serviceScope)
                delay(1000)

                _serverStatus.value = Status.RUNNING
                updateLanIp()
                updateNotification(t("notify.active").replace("{ip}", _lanIp.value))
                appendLog(t("log.active").replace("{ip}", _lanIp.value))

            } catch (e: Exception) {
                val errorDetails = e.message ?: e.javaClass.simpleName
                appendLog(t("log.start.failed").replace("{error}", errorDetails))
                Log.e("Service", "Startup error", e)

                try {
                    val timestamp = System.currentTimeMillis()
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    Manager.saveCrash(
                        this@RuntimeService,
                        Report(
                            id = UUID.randomUUID().toString().substring(0, 8),
                            timestamp = timestamp,
                            formattedDate = dateFormat.format(Date(timestamp)),
                            exceptionClass = e.javaClass.name,
                            message = e.message,
                            threadName = Thread.currentThread().name,
                            deviceInfo = Handler.getDeviceInfo(this@RuntimeService),
                            stackTrace = sw.toString()
                        )
                    )
                } catch (ignored: Exception) {
                }

                _serverStatus.value = Status.STOPPED
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    private fun stopServers() {
        val prefs = getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("server_running_state", false).apply()
        _serverStatus.value = Status.STOPPING
        serviceScope.launch {
            fun t(key: String): String = Locales.text(Locales.current, key)
            appendLog(t("log.stopping.server"))
            serverEngine.stop()
            appendLog(t("log.stopping.db"))
            mariaDbEngine.stop()
            _isDbRunning.value = false
            _serverStatus.value = Status.STOPPED
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            appendLog(t("log.stopped"))
        }
    }

    private fun updateLanIp() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        _lanIp.value = address.hostAddress ?: "127.0.0.1"
                        return
                    }
                }
            }
            _lanIp.value = "127.0.0.1"
        } catch (e: Exception) {
            _lanIp.value = "127.0.0.1"
        }
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketSAMP::CpuWakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) /* 12 hours max safety timeout */
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        /*
         * 1. Wi-Fi Lock with High Performance (keeps Wi-Fi active in background; LOW_LATENCY is deactivated by Android when app leaves foreground)
         */
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketSAMP::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        /*
         * 2. Multicast Lock - Prevents Android Wi-Fi chipset from dropping incoming UDP query packets
         */
        try {
            multicastLock = wifiManager.createMulticastLock("PocketSAMP::MulticastLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w("Service", "MulticastLock warning: ${e.message}")
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null

        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = Locales.text(Locales.current, "notify.channel.desc")
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RuntimeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(Locales.text(Locales.current, "notify.title"))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                Locales.text(Locales.current, "notify.stop"),
                stopIntent
            )
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        isServiceRunning = false
        isForeground = false
        serviceScope.cancel()
        releaseLocks()
        super.onDestroy()
    }
}