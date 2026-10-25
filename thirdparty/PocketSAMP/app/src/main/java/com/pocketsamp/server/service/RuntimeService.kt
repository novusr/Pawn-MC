package com.pocketsamp.server.service

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
import com.pocketsamp.server.MainActivity
import com.pocketsamp.server.R
import com.pocketsamp.server.engine.Extractor
import com.pocketsamp.server.engine.Downloader
import com.pocketsamp.server.config.DirectoryManager
import com.pocketsamp.server.engine.MariaDb
import com.pocketsamp.server.engine.Samp
import com.pocketsamp.server.crash.Handler
import com.pocketsamp.server.crash.Manager
import com.pocketsamp.server.crash.Report
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

    private lateinit var assetExtractor: Extractor
    private lateinit var sampEngine: Samp
    private lateinit var mariaDbEngine: MariaDb

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

        assetExtractor = Extractor(this)
        sampEngine = Samp(this)
        mariaDbEngine = MariaDb(this)

        // Handle process termination (e.g. Android Phantom Process Killer / OOM)
        sampEngine.onExit = { exitCode ->
            val prefs = getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
            val shouldBeRunning = prefs.getBoolean("server_running_state", false)

            if (exitCode == 137) {
                appendLog("[Android Alert] Proses SA-MP dihentikan oleh Android Phantom Process Killer / OOM (Exit 137).")
                appendLog("[Solusi 1] Gembok/Kunci aplikasi PocketSAMP di Recent Apps agar tidak di-freeze oleh sistem.")
                appendLog("[Solusi 2] Aktifkan 'Disable child process restrictions' di Pengaturan Developer Options.")
                appendLog("[Solusi 3] Di launcher SA-MP, hubungkan ke IP 127.0.0.1:7777 (Localhost).")
                updateNotification("Server terhenti oleh Android (Exit 137)")
            } else {
                appendLog("[PocketSAMP] Proses server SA-MP keluar dengan kode: $exitCode")
                updateNotification("SA-MP Server stopped (Code: $exitCode)")
            }

            if (shouldBeRunning) {
                appendLog("[PocketSAMP] Mencoba memulihkan server SA-MP secara otomatis dalam 2 detik...")
                serviceScope.launch {
                    delay(2000)
                    if (prefs.getBoolean("server_running_state", false) && !sampEngine.isRunning) {
                        try {
                            sampEngine.start(serviceScope)
                            updateNotification("SA-MP Server active on ${_lanIp.value}:7777")
                            appendLog("[PocketSAMP] Server SA-MP berhasil dipulihkan secara otomatis!")
                        } catch (e: Exception) {
                            appendLog("[PocketSAMP Error] Gagal memulihkan server: ${e.message}")
                            _serverStatus.value = Status.STOPPED
                        }
                    }
                }
            } else {
                _serverStatus.value = Status.STOPPED
            }
        }

        // Stream logs from engines asynchronously without blocking UI thread
        serviceScope.launch(Dispatchers.Default) {
            sampEngine.serverLogs.collect { appendLog(it) }
        }
        serviceScope.launch(Dispatchers.Default) {
            mariaDbEngine.dbLogs.collect { appendLog(it) }
        }

        updateLanIp()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
        if (intent == null) {
            // Recreated by Android OS after extreme memory pressure
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

    /**
     * Initializes (first run) and starts the MariaDB daemon.
     *
     * The only path that starts MariaDB: called once from [startServers].
     */
    private suspend fun initializeDatabase() {
        // A re-init arrives through startForegroundService(); Android requires the
        // service to call startForeground() within 5 s or it throws
        // ForegroundServiceDidNotStartInTimeException. Calling it twice on an
        // already-foregrounded service is equally illegal, so this branches.
        ensureForeground("Starting MariaDB (MySQL)...")
        // A previous daemon may still hold the socket/port after a re-init.
        if (mariaDbEngine.isRunning) mariaDbEngine.stop()

        val databaseInitialized = mariaDbEngine.initializeIfNeeded(serviceScope)
        if (databaseInitialized) {
            mariaDbEngine.start(serviceScope)
            delay(1500)
            _isDbRunning.value = mariaDbEngine.isRunning
            if (!mariaDbEngine.isRunning) {
                appendLog("[MariaDB Error] Process exited before opening the localhost SQL port.")
            }
        } else {
            _isDbRunning.value = false
            appendLog("[MariaDB Info] Local database not running; SQL access is unavailable.")
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

        clearLogs()
        _serverStatus.value = Status.STARTING
        acquireLocks()

        val notif = buildNotification("Starting servers...")
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
                // 1. Download the runtime archives, then unpack each one.
                //
                // The archives are no longer bundled in the APK: they live in the
                // repository under thirdparty/PocketSAMP/runtime and are fetched from
                // raw GitHub into Documents/PawnMC/storage. Extraction runs after the
                // download and is skipped entirely when every tree is already in place.
                if (!assetExtractor.isExtracted()) {
                    _serverStatus.value = Status.INITIALIZING
                    appendLog("[PocketSAMP] Preparing runtime archives...")

                    val downloader = Downloader(applicationContext)
                    if (!Downloader.isComplete(applicationContext)) {
                        if (!DirectoryManager.hasStoragePermission(applicationContext)) {
                            // The gate in MainActivity normally prevents this; reaching
                            // here means the permission was revoked while running.
                            appendLog("[Setup] Storage access is required to download the runtime files.")
                            _serverStatus.value = Status.STOPPED
                            return@launch
                        }
                        downloader.sync { line -> appendLog("[Setup] $line") }
                    }

                    if (!Downloader.isComplete(applicationContext)) {
                        appendLog("[Setup] Runtime archives could not be downloaded; check the connection and retry.")
                        _serverStatus.value = Status.STOPPED
                        return@launch
                    }

                    appendLog("[PocketSAMP] Extracting runtime archives...")
                    assetExtractor.extractAll { progress ->
                        appendLog("[Setup] $progress")
                        updateNotification("Setup: $progress")
                    }
                }

                // 2. Initialize and start MariaDB
                initializeDatabase()

                // 3. Start SA-MP server via QEMU
                updateNotification("Launching SA-MP Server...")
                sampEngine.start(serviceScope)
                delay(1000)

                _serverStatus.value = Status.RUNNING
                updateLanIp()
                updateNotification("SA-MP Server active on ${_lanIp.value}:7777")
                appendLog("[PocketSAMP] All services active! Connect to ${_lanIp.value}:7777")

            } catch (e: Exception) {
                val errorDetails = e.message ?: e.javaClass.simpleName
                appendLog("[PocketSAMP Error] Failed to start: $errorDetails")
                Log.e("Service", "Startup error", e)

                // Record to Manager for in-app tracking
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
            appendLog("[PocketSAMP] Shutting down SA-MP server...")
            sampEngine.stop()
            appendLog("[PocketSAMP] Shutting down MariaDB...")
            mariaDbEngine.stop()
            _isDbRunning.value = false
            _serverStatus.value = Status.STOPPED
            releaseLocks()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            appendLog("[PocketSAMP] Server successfully stopped.")
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
            acquire(12 * 60 * 60 * 1000L) // 12 hours max safety timeout
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // 1. Wi-Fi Lock with High Performance (keeps Wi-Fi active in background; LOW_LATENCY is deactivated by Android when app leaves foreground)
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PocketSAMP::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        // 2. Multicast Lock - Prevents Android Wi-Fi chipset from dropping incoming UDP query packets
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
            description = "Maintains SA-MP local server background process"
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
            .setContentTitle("PocketSAMP Server (Aktif)")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
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