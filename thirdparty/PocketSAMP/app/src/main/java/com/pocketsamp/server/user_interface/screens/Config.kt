package com.pocketsamp.server.user_interface.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration.ConfigParameters
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration._DirectoryManager
import com.pocketsamp.server.user_interface.theme.SampThemeColors
import com.pocketsamp.server.user_interface.widget.SimpleScrollSidebar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Configuration information screen (read-only).
 *
 * Formerly an editor: the fields could be typed and a Save button rewrote
 * `server.cfg`. Editing the config from the app proved more hazard than help —
 * a mistyped port or rcon password broke the next launch silently — so the
 * screen is now purely informative: every `server.cfg` parameter this runner
 * understands, with its accepted value shape and (when the file exists) the
 * value currently set. Changes are made by editing `server.cfg` itself in the
 * Xed Editor.
 */
@Composable
fun _Config() {
    val context = LocalContext.current
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)

    // Re-derive the config file whenever the selected folder changes: the File
    // Manager may re-point the server directory while this screen stays open.
    var folderToken by remember { mutableIntStateOf(0) }
    val configFile = remember(folderToken) {
        File(_DirectoryManager.getServerDirectory(context), "server.cfg")
    }
    var rawValues by remember(configFile) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var fileExists by remember(configFile) { mutableStateOf(configFile.isFile) }
    var readError by remember(configFile) { mutableStateOf<String?>(null) }

    /*
     * Fully lazy: nothing is read while the user is on the dashboard, console or
     * files tab. The read fires (a) the first time this screen is visited and
     * (b) on every re-entry into the screen, but a re-read only publishes new
     * state when the file content actually changed — a metadata fingerprint
     * (size + lastModified + first read's content hash) guards the assignment,
     * so hovering over the tab costs one stat call, not a parse.
     */
    var fingerprint by remember(configFile) { mutableStateOf<Int?>(null) }

    suspend fun reloadIfChanged() {
        val stat = withContext(Dispatchers.IO) {
            runCatching { Triple(configFile.isFile, configFile.length(), configFile.lastModified()) }
        }.getOrNull() ?: Triple(false, 0L, 0L)
        val candidate = stat.hashCode()
        if (candidate == fingerprint) return
        val result = withContext(Dispatchers.IO) {
            runCatching { Reader.readRawValues(configFile) }
        }
        result.onSuccess {
            rawValues = it
            fileExists = stat.first
            readError = null
            fingerprint = candidate
        }.onFailure {
            rawValues = emptyMap()
            readError = it.message ?: Locales.text(lang, "config.read.failed")
            fingerprint = candidate
        }
    }

    LaunchedEffect(configFile) {
        reloadIfChanged()
    }

    val lifecycleOwner = context as? LifecycleOwner
    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner, configFile) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // The selected folder can change while the app was away (the
                    // File Manager picker); refresh the file handle.
                    val current = File(_DirectoryManager.getServerDirectory(context), "server.cfg")
                    if (current.absolutePath != configFile.absolutePath) folderToken++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val scrollState = rememberScrollState()
    Row(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(SampThemeColors.bg)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(t("config.title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SampThemeColors.textPrimary, maxLines = 1)
            Text(t("config.subtitle.info"), fontSize = 12.sp, color = SampThemeColors.textSecondary)
        }

        if (!fileExists) {
            ConfigNotice(t("config.defaults"))
        }
        readError?.let { error -> ConfigNotice("${t("config.read.failed")}: $error") }

        // One row per known parameter: name, accepted value shape, and the value
        // currently written in this folder's server.cfg (dash when unset).
        ConfigParameters.INFO.forEach { entry ->
            ConfigInfoRow(
                key = entry.key,
                accepted = entry.accepted,
                current = rawValues[entry.key]
            )
        }
        Spacer(modifier = Modifier.height(52.dp))
    }

    // Right-edge sidebar of the parameter list; it disappears when every parameter
    // already fits, so a short server.cfg keeps the full width.
    SimpleScrollSidebar(scrollState = scrollState)
    }
}

@Composable
private fun ConfigNotice(text: String) {
    Text(text = text, color = SampThemeColors.warn, fontSize = 12.sp)
}

@Composable
private fun ConfigInfoRow(key: String, accepted: String, current: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SampThemeColors.card, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                key,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = SampThemeColors.textPrimary
            )
            Text(
                accepted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = SampThemeColors.textMuted
            )
        }
        Text(
            current?.takeIf { it.isNotBlank() } ?: "-",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (current.isNullOrBlank()) SampThemeColors.textMuted else SampThemeColors.accent,
            maxLines = 1
        )
    }
}
