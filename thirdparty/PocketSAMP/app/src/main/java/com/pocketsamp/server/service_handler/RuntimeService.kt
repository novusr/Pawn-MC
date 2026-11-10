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
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration._DirectoryManager
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
import java.io.File
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

        /**
         * Captures the app context before the service starts.
         *
         * Preparation logs (`[Prepare] ...`) are appended from the hosting
         * activity while the runtime archives are still downloading — at that
         * point `onCreate` has never run and `appContext` is still null. Calling
         * this from `ServerApp.install` makes those early lines hit the journal
         * instead of silently disappearing.
         */
        fun attachEarlyContext(context: android.content.Context) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
        }

        /** True while the service (and therefore the engine) is alive. */
        @Volatile
        var isServiceRunning: Boolean = false
            private set

        /** App context captured at [onCreate]; the JSONL console journal lives in its files dir. */
        @Volatile
        private var appContext: android.content.Context? = null

        /** Own journal of console lines; trimmed at startup, written per line. */
        @Volatile
        private var journalFile: java.io.File? = null

        /**
         * Journal location resolvable before the service exists.
         *
         * `MainActivity` logs `[Prepare]` lines while the runtime is still being
         * downloaded — before `RuntimeService.onCreate` has ever run — so the
         * journal path is computed straight from a context instead of relying on
         * the service having started. Without this the same preparation either
         * showed up in the console or vanished depending on whether the service
         * happened to be alive at that moment.
         */
        fun journalFileFor(context: android.content.Context): java.io.File {
            val dir = java.io.File(context.filesDir, "console_logs").apply { mkdirs() }
            return java.io.File(dir, "console.jsonl")
        }
        private val _serverStatus = MutableStateFlow(Status.STOPPED)
        val serverStatus: StateFlow<Status> = _serverStatus.asStateFlow()

        private val _isDbRunning = MutableStateFlow(false)
        val isDbRunning: StateFlow<Boolean> = _isDbRunning.asStateFlow()

        private val _logFeed = MutableStateFlow<List<String>>(emptyList())
        val logFeed: StateFlow<List<String>> = _logFeed.asStateFlow()

        private const val FALLBACK_LAN_IP = "192.168.1.186"

        /** Consecutive auto-recoveries after an OS kill; reset once the server stays up. */
        private val crashRestartAttempts = java.util.concurrent.atomic.AtomicInteger(0)
        private const val MAX_CRASH_RESTARTS = 3
        private const val RESTART_BACKOFF_MS = 2000L

        private val _lanIp = MutableStateFlow(FALLBACK_LAN_IP)
        val lanIp: StateFlow<String> = _lanIp.asStateFlow()

        private val logLock = Any()

        /** Pending lines waiting for their paced screen tick. */
        private val streamingBacklog = ArrayDeque<String>()

        /**
         * Lines that belong to the samp03svr lifecycle get the
         * `[hh:mm:ss] $System [tag]` header so the console can tell them apart
         * from raw engine output. Localized strings already carrying a `$System`
         * tag just fall through to [appendLog], which grafts the timestamp on.
         */
        fun appendSystemLog(tag: String, line: String) {
            if (line.contains("\$System")) {
                appendLog(line)
                return
            }
            appendLog("[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] \$System [$tag] $line")
        }

        fun appendLog(rawLine: String) {
            // Every $System line carries an [hh:mm:ss] stamp up front; localized
            // strings arrive without one, so graft it here before anything reads it.
            val line = if (rawLine.contains("\$System") && !rawLine.startsWith("[")) {
                val marker = rawLine.indexOf("\$System")
                "[${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}] " + rawLine.substring(marker)
            } else rawLine
            runCatching { consoleLogSink?.invoke(line) }
            synchronized(logLock) {
                // The host (PawnMC) mirrors every line into its SQLite log via the
                // consoleLogSink installed at launch, so this module only keeps its
                // own lightweight JSONL journal — the module does not link PawnMC
                // code and cannot call the host store directly. When the service
                // has not started yet (preparation from the dashboard), resolve the
                // journal from the captured app context so early lines persist too.
                val journal = journalFile ?: appContext?.let { context ->
                    runCatching { journalFileFor(context) }.getOrNull()
                }
                journal?.let { file ->
                    runCatching { file.appendText(line.replace('\n', ' ') + "\n") }
                }
                val current = _logFeed.value.toMutableList()
                if (current.size > 500) {
                    current.removeAt(0)
                }
                current.add(line)
                _logFeed.value = current
                // Pace the on-screen stream: batch arrivals go through a backlog
                // released a few lines per tick, so a burst (bootstrap SQL output,
                // a gamemode printing at startup) scrolls readably instead of
                // flashing by in one frame.
                streamingBacklog.addLast(line)
                if (streamingBacklog.size > 200) streamingBacklog.removeFirst()
            }
        }

        /**
         * Drains [streamingBacklog] into the UI feed at a readable cadence.
         * Started once from [onCreate]; runs only while lines are pending.
         */
        fun startLogStreamer(scope: kotlinx.coroutines.CoroutineScope) {
            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    kotlinx.coroutines.delay(150)
                    var released: List<String> = emptyList()
                    synchronized(logLock) {
                        if (streamingBacklog.isNotEmpty()) {
                            val count = minOf(3, streamingBacklog.size)
                            released = List(count) { streamingBacklog.removeFirst() }
                        }
                    }
                    if (released.isNotEmpty()) {
                        _streamedFeed.value = _streamedFeed.value + released
                        if (_streamedFeed.value.size > 500) {
                            _streamedFeed.value = _streamedFeed.value.takeLast(500)
                        }
                    }
                }
            }
        }

        /** Pace-limited feed the console screen subscribes to. */
        private val _streamedFeed = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
        val streamedFeed: kotlinx.coroutines.flow.StateFlow<List<String>> = _streamedFeed.asStateFlow()

        fun clearLogs() {
            synchronized(logLock) {
                _logFeed.value = emptyList()
                _streamedFeed.value = emptyList()
                streamingBacklog.clear()
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
        appContext = applicationContext
        journalFile = runCatching {
            journalFileFor(applicationContext).also { file ->
                // Keep the journal bounded: older lines are the host's SQLite store's
                // job, this file only has to survive a session.
                if (file.isFile && file.length() > 512 * 1024) file.delete()
            }
        }.getOrNull()
        isServiceRunning = true
        createNotificationChannel()
        startLogStreamer(serviceScope)

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

            // 137 = SIGKILL (Phantom Process Killer / OOM); 139 = SIGSEGV, which
            // on emulated QEMU-i386 runs is nearly always the same OS-side kill
            // surfacing as a faulted exit — both carry the recovery advice.
            if (exitCode == 137 || exitCode == 139) {
                appendLog(t("notify.exit137.log"))
                appendLog(t("log.exit137.fix1"))
                appendLog(t("log.exit137.fix2"))
                appendLog(t("log.exit137.fix3"))
                if (exitCode == 139) appendLog(t("log.exit139.note"))
                updateNotification(t("notify.exit137"))
            } else {
                appendLog(t("log.exited").replace("{code}", exitCode.toString()))
                // Surface the tail of server_log.txt right after the exit line:
                // an SA-MP exit code 1 always has a reason in the log (a missing
                // gamemode, a plugin that failed to load) and showing only the
                // code left the user guessing why the server bailed out.
                val logTail = runCatching {
                    val logFile = File(_DirectoryManager.getServerDirectory(applicationContext), "server_log.txt")
                    if (logFile.isFile) {
                        logFile.readLines().takeLast(12).joinToString("\n")
                    } else {
                        ""
                    }
                }.getOrDefault("")
                if (logTail.isNotBlank()) {
                    appendLog(t("log.exit.tail"))
                    logTail.lines().forEach { appendLog(it) }
                }
                updateNotification(t("notify.stopped").replace("{code}", exitCode.toString()))
            }

            // Only the OS killing the process (SIGKILL -> 137, or SIGSEGV -> 139
            // which on the QEMU run usually traces back to the same OS-side kill)
            // is auto-recovered. A server that exits cleanly on its own
            // (code 1 and friends) must stay down: blindly restarting turned every
            // crash into an endless restart loop.
            val osKilled = exitCode == 137 || exitCode == 139
            if (shouldBeRunning && osKilled) {
                // Bound the recovery: each consecutive kill backs off longer and
                // after several in a row the server is left stopped instead of
                // relaunching forever.
                val attempts = crashRestartAttempts.incrementAndGet()
                if (attempts > MAX_CRASH_RESTARTS) {
                    appendLog(t("log.restore.gaveup"))
                    prefs.edit().putBoolean("server_running_state", false).apply()
                    _serverStatus.value = Status.STOPPED
                } else {
                    appendLog(t("log.restoring"))
                    serviceScope.launch {
                        delay(RESTART_BACKOFF_MS * attempts)
                        if (prefs.getBoolean("server_running_state", false) && !serverEngine.isRunning) {
                            try {
                                if (serverEngine.start(serviceScope)) {
                                    crashRestartAttempts.set(0)
                                    updateNotification(t("notify.active").replace("{ip}", _lanIp.value))
                                    appendLog(t("log.restored"))
                                } else {
                                    prefs.edit().putBoolean("server_running_state", false).apply()
                                    _serverStatus.value = Status.STOPPED
                                }
                            } catch (e: Exception) {
                                appendLog(t("log.restore.failed").replace("{error}", e.message.orEmpty()))
                                _serverStatus.value = Status.STOPPED
                            }
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
        if (_serverStatus.value != Status.STOPPED) {
            appendSystemLog("Runtime", "Start request ignored; service status is ${_serverStatus.value}.")
            return
        }

        fun t(key: String): String = Locales.text(Locales.current, key)
        val serverDirectory = _DirectoryManager.getServerDirectory(applicationContext)
        // No server.cfg yet: the runner writes the shipped defaults once, so the first
        // start of a fresh folder is not refused.
        if (!File(serverDirectory, "server.cfg").isFile) {
            runCatching { Reader.ensureDefaultConfig(serverDirectory) }
                .onSuccess { appendLog(t("log.cfg.defaults.created")) }
                .onFailure {
                    appendLog(t("log.cfg.missing").replace("{dir}", serverDirectory.absolutePath))
                    appendLog(t("log.cfg.hint"))
                    getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("server_running_state", false).apply()
                    _serverStatus.value = Status.STOPPED
                    stopSelf()
                    return
                }
        }
        clearLogs()
        // Fresh console: drop the previous run's replayed lines so stale "Stream
        // closed"/pipe noise from the last session never leads the new log.
        serverEngine.resetLogHistory()
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
                        if (!_DirectoryManager.hasStoragePermission(applicationContext)) {
                            appendLog(t("log.archive.permission"))
                            _serverStatus.value = Status.STOPPED
                            return@launch
                        }
                        runtime.sync { line -> appendSystemLog("Prepare", line) }
                    }

                    if (!ThirdRuntime.isComplete(applicationContext)) {
                        appendLog(t("log.archive.failed"))
                        _serverStatus.value = Status.STOPPED
                        return@launch
                    }

                    appendLog(t("log.archive.extract"))
                    assetExtractor.extractAll { progress ->
                        appendSystemLog("Prepare", progress)
                        updateNotification(t("notify.setup").replace("{progress}", progress))
                    }
                }

                initializeDatabase()

                updateNotification(t("log.launching"))
                if (!serverEngine.start(serviceScope)) {
                    appendLog(t("log.server.start.refused"))
                    mariaDbEngine.stop()
                    _isDbRunning.value = false
                    getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("server_running_state", false).apply()
                    _serverStatus.value = Status.STOPPED
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                delay(1000)

                if (!serverEngine.isRunning) {
                    getSharedPreferences("pocketsamp_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("server_running_state", false).apply()
                    mariaDbEngine.stop()
                    _isDbRunning.value = false
                    _serverStatus.value = Status.STOPPED
                    releaseLocks()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
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
        var detected: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        detected = address.hostAddress
                        break
                    }
                }
                if (!detected.isNullOrBlank()) break
            }
        } catch (e: Exception) {
            detected = null
        }
        _lanIp.value = sanitizeLanIp(detected)
    }

    /**
     * Keeps the LAN card distinct from the local card on the dashboard: a loopback, localhost,
     * wildcard or empty detection is never shown as the LAN address.
     */
    private fun sanitizeLanIp(candidate: String?): String {
        val value = candidate?.trim().orEmpty()
        val isLocalOnly = value.isEmpty() ||
            value.equals("localhost", ignoreCase = true) ||
            value == "127.0.0.1" ||
            value == "0.0.0.0" ||
            value == "::1" ||
            value == "::" ||
            value.startsWith("127.", ignoreCase = true)
        return if (isLocalOnly) FALLBACK_LAN_IP else value
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
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = Locales.text(Locales.current, "notify.channel.desc")
            setShowBadge(true)
            setSound(null, null)
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