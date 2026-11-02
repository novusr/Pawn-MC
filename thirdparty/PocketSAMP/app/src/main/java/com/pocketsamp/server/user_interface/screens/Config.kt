package com.pocketsamp.server.user_interface.screens

import android.widget.Toast
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
import com.pocketsamp.server.configuration.Configuration
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration.Writer
import com.pocketsamp.server.configuration._DirectoryManager
import com.pocketsamp.server.user_interface.theme.SampThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun _Config() {
    val context = LocalContext.current
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)
    val configFile = remember { File(_DirectoryManager.getServerDirectory(context), "server.cfg") }
    var config by remember(configFile) { mutableStateOf<Configuration?>(null) }
    var readError by remember(configFile) { mutableStateOf<String?>(null) }
    var reloadToken by remember(configFile) { mutableIntStateOf(0) }
    var saveError by remember(configFile) { mutableStateOf<String?>(null) }
    var hostname by remember(config) { mutableStateOf(config?.hostname.orEmpty()) }
    var port by remember(config) { mutableStateOf(config?.port?.toString().orEmpty()) }
    var maxPlayers by remember(config) { mutableStateOf(config?.maxPlayers?.toString().orEmpty()) }
    var rconPassword by remember(config) { mutableStateOf(config?.rconPassword.orEmpty()) }
    var gamemode by remember(config) { mutableStateOf(config?.gamemode.orEmpty()) }
    var filterscripts by remember(config) { mutableStateOf(config?.filterscripts.orEmpty()) }
    var plugins by remember(config) { mutableStateOf(config?.plugins.orEmpty()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(configFile, reloadToken) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                // A missing server.cfg is not an error: the runner still needs the
                // shipped defaults visible and editable before anything is written.
                Reader.readOrDefault(configFile.parentFile ?: configFile)
            }
        }
        result.onSuccess {
            if (config == null || it != config) {
                // Reset only when the file actually changed, so typing while the screen
                // reloads on resume does not wipe the field being edited.
                hostname = it.hostname
                port = it.port.toString()
                maxPlayers = it.maxPlayers.toString()
                rconPassword = it.rconPassword
                gamemode = it.gamemode
                filterscripts = it.filterscripts
                plugins = it.plugins
            }
            config = it
            readError = null
            saveError = null
        }.onFailure {
            config = Configuration()
            readError = it.message ?: t("config.read.failed")
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner, configFile) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) reloadToken++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(t("config.title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SampThemeColors.textPrimary, maxLines = 1)
                Text(t("config.subtitle"), fontSize = 12.sp, color = SampThemeColors.textSecondary, maxLines = 1)
            }
            Button(
                enabled = config != null,
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    val parsedMaxPlayers = maxPlayers.toIntOrNull()
                    if (parsedPort == null || parsedPort !in 1..65535 ||
                        parsedMaxPlayers == null || parsedMaxPlayers !in 1..1000
                    ) {
                        saveError = t("config.validation")
                        return@Button
                    }
                    val updated = config!!.copy(
                        hostname = hostname,
                        port = parsedPort,
                        maxPlayers = parsedMaxPlayers,
                        rconPassword = rconPassword,
                        gamemode = gamemode,
                        filterscripts = filterscripts,
                        plugins = plugins
                    )
                    // The file write runs off the main thread; the scope here is the
                    // composable's lifecycle, which is what the previous inline call lacked.
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                configFile.parentFile?.mkdirs()
                                Writer.write(updated, configFile)
                            }
                        }
                        result.onSuccess {
                            config = updated
                            saveError = null
                            Toast.makeText(context, t("config.saved"), Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            saveError = it.message ?: t("config.save.failed")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SampThemeColors.accent,
                    contentColor = SampThemeColors.bg
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(t("config.save"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!configFile.isFile) {
            ConfigNotice(t("config.defaults"))
        }
        readError?.let { error -> ConfigNotice("${t("config.read.failed")}: $error") }
        saveError?.let { ConfigNotice(it) }

        ConfigField(label = t("config.field.hostname"), value = hostname, onValueChange = { hostname = it }, enabled = config != null)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConfigField(label = t("config.field.port"), value = port, onValueChange = { port = it }, modifier = Modifier.weight(1f), enabled = config != null)
            ConfigField(label = t("config.field.maxplayers"), value = maxPlayers, onValueChange = { maxPlayers = it }, modifier = Modifier.weight(1f), enabled = config != null)
        }
        ConfigField(label = t("config.field.rcon"), value = rconPassword, onValueChange = { rconPassword = it }, enabled = config != null)
        ConfigField(label = t("config.field.gamemode"), value = gamemode, onValueChange = { gamemode = it }, hint = t("config.hint.gamemode"), enabled = config != null)
        ConfigField(label = t("config.field.filterscripts"), value = filterscripts, onValueChange = { filterscripts = it }, hint = t("config.hint.filterscripts"), enabled = config != null)
        ConfigField(label = t("config.field.plugins"), value = plugins, onValueChange = { plugins = it }, hint = t("config.hint.plugins"), enabled = config != null)
        Spacer(modifier = Modifier.height(52.dp))
    }
}

@Composable
private fun ConfigNotice(text: String) {
    Text(text = text, color = SampThemeColors.warn, fontSize = 12.sp)
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SampThemeColors.textSecondary, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = SampThemeColors.textPrimary
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SampThemeColors.accent,
                unfocusedBorderColor = SampThemeColors.cardBorder,
                focusedContainerColor = SampThemeColors.card,
                unfocusedContainerColor = SampThemeColors.card
            ),
            shape = RoundedCornerShape(10.dp)
        )
        if (hint != null) Text(hint, fontSize = 11.sp, color = SampThemeColors.textMuted)
    }
}
