package com.pocketsamp.server.user_interface.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration._DirectoryManager
import com.pocketsamp.server.service_handler.Status
import com.pocketsamp.server.user_interface.theme.SampThemeColors
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun _Dashboard(
    status: Status,
    isDbRunning: Boolean,
    lanIp: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    lang: Locales.Lang = Locales.Lang.EN,
    onOpenDatabase: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    fun t(key: String): String = Locales.text(lang, key)

    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    var currentServerDir by remember { mutableStateOf(_DirectoryManager.getServerDirectory(context)) }
    var hasStoragePermission by remember { mutableStateOf(_DirectoryManager.hasStoragePermission(context)) }
    var configReloadToken by remember { mutableIntStateOf(0) }
    var showphpMyAdmin by remember { mutableStateOf(false) }
    var showAntiDcGuide by remember { mutableStateOf(false) }
    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    }
    var isBatteryOptIgnored by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }

    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasStoragePermission = _DirectoryManager.hasStoragePermission(context)
                    currentServerDir = _DirectoryManager.getServerDirectory(context)
                    configReloadToken++
                    isBatteryOptIgnored =
                        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M ||
                            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val directory = _DirectoryManager.parseTreeUriToFile(uri)
        if (directory != null && directory.exists() && directory.isDirectory && directory.canRead()) {
            if (status != Status.STOPPED) {
                Toast.makeText(context, t("folder.running"), Toast.LENGTH_LONG).show()
            } else if (_DirectoryManager.hasStoragePermission(context) && _DirectoryManager.setServerDirectory(context, directory)) {
                currentServerDir = directory
                Toast.makeText(context, "${t("folder.changed")}: ${directory.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, t("folder.pick.failed"), Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, t("folder.pick.failed"), Toast.LENGTH_LONG).show()
        }
    }

    if (showphpMyAdmin) {
        _phpMyAdmin(
            isDbRunning = isDbRunning,
            onClose = { showphpMyAdmin = false },
            lang = lang
        )
        return
    }

    val isBusy = status == Status.STARTING ||
        status == Status.INITIALIZING ||
        status == Status.STOPPING
    val isRunning = status == Status.RUNNING
    val hasCfg = File(currentServerDir, "server.cfg").isFile
    val serverConfig = remember(currentServerDir, hasCfg, configReloadToken) {
        // Without a file the screen still shows the runner defaults rather than an
        // empty card, and the Start button below stays enabled.
        runCatching { Reader.readOrDefault(currentServerDir) }
    }
    val serverPort = serverConfig.getOrNull()?.port?.toString() ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /* Back button sized in raw physical pixels instead of density-independent
             * dp: 40 px reads compact on high-density screens where the same dp
             * balloons, and stays comfortably tappable on low-density ones where a
             * fixed dp shrinks to a dot. Font scale is irrelevant to a 20 px glyph. */
            val backDp = with(LocalDensity.current) {
                (40f / density.density).dp
            }
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(backDp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = t("back.to.pawnmc"),
                    tint = SampThemeColors.textPrimary,
                    modifier = Modifier.size((backDp.value * 0.5f).dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PocketSAMP",
                    style = MaterialTheme.typography.titleLarge,
                    color = SampThemeColors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = t("tagline"),
                    style = MaterialTheme.typography.bodySmall,
                    color = SampThemeColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ServerStatus(status = status, label = statusLabel(status) { key -> t(key) }, isBusy = isBusy)
        }

        SectionLabel(t("section.server.connection"))
        CardSurface {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ConnectionAddress(
                    title = t("dash.local.title"),
                    address = "127.0.0.1:$serverPort",
                    note = t("dash.local.note"),
                    copyLabel = t("dash.copy.local"),
                    context = context,
                    copiedMessage = t("copy.done")
                )
                SurfaceDivider()
                ConnectionAddress(
                    title = t("dash.lan.title"),
                    address = "$lanIp:$serverPort",
                    note = t("dash.lan.note"),
                    copyLabel = t("dash.copy.lan"),
                    context = context,
                    copiedMessage = t("copy.done")
                )
            }
        }

        SectionLabel(t("db.engine"))
        CardSurface {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDbRunning) "127.0.0.1:3306" else t("db.stopped"),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = SampThemeColors.textPrimary
                        )
                    }
                    Text(
                        text = if (isDbRunning) t("db.running") else t("status.offline"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isDbRunning) SampThemeColors.success else SampThemeColors.textMuted
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onOpenDatabase,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(t("db.manage"), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showphpMyAdmin = true },
                        enabled = isDbRunning,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(t("db.phpmyadmin"), maxLines = 1)
                    }
                }
            }
        }

        SectionLabel(t("section.runtime"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuntimeMetric(
                title = t("spec.arch.title"),
                value = "QEMU-i386",
                detail = t("spec.arch.note"),
                modifier = Modifier.weight(1f)
            )
            RamProfileMetric(
                isServerRunning = isRunning,
                lang = lang,
                modifier = Modifier.weight(1f)
            )
        }

        if (!hasStoragePermission) {
            NoticeSurface(
                title = t("perm.file.title"),
                message = t("perm.file.desc"),
                color = SampThemeColors.warn,
                action = t("perm.file.button"),
                onAction = {
                    try {
                        context.startActivity(_DirectoryManager.getManageStorageIntent(context))
                    } catch (error: Exception) {
                        Toast.makeText(context, t("perm.file.settings"), Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        if (!isBatteryOptIgnored) {
            NoticeSurface(
                title = t("perm.battery.title"),
                message = t("perm.battery.desc"),
                color = SampThemeColors.accent,
                action = t("perm.battery.button"),
                onAction = {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    } catch (error: Exception) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (fallbackError: Exception) {
                            Toast.makeText(context, t("perm.battery.settings"), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        CardSurface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAntiDcGuide = !showAntiDcGuide }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("guide.antidc.title"),
                            style = MaterialTheme.typography.titleSmall,
                            color = SampThemeColors.textPrimary
                        )
                        if (!showAntiDcGuide) {
                            Text(
                                text = t("guide.antidc.short"),
                                style = MaterialTheme.typography.bodySmall,
                                color = SampThemeColors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(onClick = { showAntiDcGuide = !showAntiDcGuide }) {
                        Text(if (showAntiDcGuide) t("perm.close") else t("perm.guide"))
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = SampThemeColors.accent
                    )
                }
                if (showAntiDcGuide) {
                    listOf(
                        t("guide.antidc.step1") to t("guide.antidc.step1.desc"),
                        t("guide.antidc.step2").replace("7777", serverPort) to
                            t("guide.antidc.step2.desc").replace("7777", serverPort),
                        t("guide.antidc.step3") to t("guide.antidc.step3.desc")
                    ).forEach { (title, description) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = SampThemeColors.accent
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = SampThemeColors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        SectionLabel(t("section.directory"))
        CardSurface {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentServerDir.absolutePath,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = SampThemeColors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            copyToClipboard(
                                context = context,
                                label = "Server Path",
                                value = currentServerDir.absolutePath,
                                message = t("copy.path.done")
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = t("copy.path.description"),
                            tint = SampThemeColors.accent
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            (if (hasCfg) SampThemeColors.success else SampThemeColors.warn)
                                .copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (hasCfg) SampThemeColors.success else SampThemeColors.warn)
                    )
                    Text(
                        text = if (hasCfg) t("folder.has.cfg") else t("folder.no.cfg.defaults"),
                        style = MaterialTheme.typography.bodySmall,
                        color = SampThemeColors.textPrimary
                    )
                }
                serverConfig.exceptionOrNull()?.let { error ->
                    Text(
                        text = "${t("dash.cfg.read.failed")}: ${error.message.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SampThemeColors.danger
                    )
                }
                // Folder selection moved out of the dashboard: the File Manager owns
                // picking (its reset button lives beside the run control), so the
                // home page only shows the active folder as information.
                Text(
                    text = t("folder.current").replace("{dir}", currentServerDir.absolutePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = SampThemeColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        // Simple notice right above the start button: when a start is refused
        // (busy, missing runtime, no folder) the user sees a short reason instead
        // of tapping a button that silently does nothing.
        if (!isBusy && !isRunning) {
            val startBlocker = when {
                !hasStoragePermission -> t("start.blocked.storage")
                !hasCfg -> t("start.blocked.cfg")
                else -> null
            }
            if (startBlocker != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SampThemeColors.warn.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SampThemeColors.warn)
                    )
                    Text(
                        text = startBlocker,
                        style = MaterialTheme.typography.bodySmall,
                        color = SampThemeColors.textPrimary
                    )
                }
            }
        }
        Button(
            onClick = { if (isRunning) onStopServer() else onStartServer() },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) SampThemeColors.danger else SampThemeColors.accent,
                contentColor = if (isRunning) Color.White else SampThemeColors.accentOn
            )
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SampThemeColors.textPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = if (isRunning) t("action.stop") else t("action.start"),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun ServerStatus(status: Status, label: String, isBusy: Boolean) {
    val color = when {
        status == Status.RUNNING -> SampThemeColors.success
        isBusy -> SampThemeColors.warn
        else -> SampThemeColors.textMuted
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = SampThemeColors.textPrimary
    )
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SampThemeColors.cardBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun ConnectionAddress(
    title: String,
    address: String,
    note: String,
    copyLabel: String,
    context: Context,
    copiedMessage: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SampThemeColors.textSecondary
            )
            IconButton(
                onClick = {
                    copyToClipboard(context, "SA-MP Server IP", address, "$copiedMessage $address")
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = copyLabel,
                    tint = SampThemeColors.accent
                )
            }
        }
        Text(
            text = address,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            color = SampThemeColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = SampThemeColors.textSecondary
        )
    }
}

/**
 * Live RAM readout of the device.
 *
 * Refreshes every 11 seconds while the server is running and once on entry
 * otherwise, so the numbers track what the OS actually reports rather than a
 * hardcoded estimate.
 */
@Composable
private fun RamProfileMetric(isServerRunning: Boolean, lang: Locales.Lang, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var profile by remember { mutableStateOf(readRamProfile(context, lang)) }

    androidx.compose.runtime.LaunchedEffect(isServerRunning) {
        while (true) {
            profile = readRamProfile(context, lang)
            // A running server churns memory (QEMU translation cache, MariaDB
            // buffers), so it is the only state worth polling continuously.
            delay(if (isServerRunning) 11_000L else Long.MAX_VALUE)
        }
    }

    RuntimeMetric(
        title = Locales.text(lang, "spec.ram.title"),
        value = profile.headline,
        detail = profile.detail,
        modifier = modifier
    )
}

private data class RamProfile(val headline: String, val detail: String)

private fun readRamProfile(context: android.content.Context, lang: Locales.Lang): RamProfile {
    fun t(key: String): String = Locales.text(lang, key)
    val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
        as? android.app.ActivityManager
        ?: return RamProfile("~150 MB", t("spec.ram.note"))
    val memoryInfo = android.app.ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalMb = memoryInfo.totalMem / (1024L * 1024L)
    val availableMb = memoryInfo.availMem / (1024L * 1024L)
    val usedMb = (totalMb - availableMb).coerceAtLeast(0L)
    val percent = if (totalMb > 0) (usedMb * 100 / totalMb).coerceIn(0, 100) else 0L
    return RamProfile(
        headline = "$availableMb MB ${t("spec.ram.free")}",
        detail = "$usedMb / ${totalMb} MB (${percent}%) " + t("spec.ram.note")
    )
}

@Composable
private fun RuntimeMetric(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, SampThemeColors.cardBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SampThemeColors.textSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = SampThemeColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = SampThemeColors.textSecondary
            )
        }
    }
}

@Composable
private fun NoticeSurface(
    title: String,
    message: String,
    color: Color,
    action: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = color
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = SampThemeColors.textPrimary
            )
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SurfaceDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SampThemeColors.cardBorder)
    )
}

private fun statusLabel(status: Status, t: (String) -> String): String = when (status) {
    Status.RUNNING -> t("status.online")
    Status.STARTING, Status.INITIALIZING -> t("status.starting")
    Status.STOPPING -> t("status.stopping")
    Status.STOPPED -> t("status.offline")
}

private fun copyToClipboard(context: Context, label: String, value: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun pocketsampFolderPickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
    addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    )
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        putExtra(
            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
        )
    }
}
