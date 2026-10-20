package com.pocketsamp.server.ui.screens

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
import com.pocketsamp.server.config.ServerConfig
import com.pocketsamp.server.config.ServerDirectoryManager
import com.pocketsamp.server.ui.theme.*
import java.io.File

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val serverDir = remember { ServerDirectoryManager.getServerDirectory(context) }
    val configFile = remember(serverDir) { File(serverDir, "server.cfg") }

    var config by remember(configFile) { mutableStateOf(ServerConfig.loadFromFile(configFile)) }

    var hostname by remember(config) { mutableStateOf(config.hostname) }
    var port by remember(config) { mutableStateOf(config.port.toString()) }
    var maxPlayers by remember(config) { mutableStateOf(config.maxPlayers.toString()) }
    var rconPassword by remember(config) { mutableStateOf(config.rconPassword) }
    var gamemode by remember(config) { mutableStateOf(config.gamemode) }
    var filterscripts by remember(config) { mutableStateOf(config.filterscripts) }
    var plugins by remember(config) { mutableStateOf(config.plugins) }

    val scrollState = rememberScrollState()

    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner, configFile) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    config = ServerConfig.loadFromFile(configFile)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Server Configuration",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SampThemeColors.textPrimary
                )
                Text(
                    text = "server.cfg Editor",
                    fontSize = 14.sp,
                    color = SampThemeColors.textSecondary
                )
            }

            Button(
                onClick = {
                    config.hostname = hostname
                    config.port = port.toIntOrNull() ?: 7777
                    config.maxPlayers = maxPlayers.toIntOrNull() ?: 50
                    config.rconPassword = rconPassword
                    config.gamemode = gamemode
                    config.filterscripts = filterscripts
                    config.plugins = plugins
                    ServerConfig.saveToFile(config, configFile)
                    Toast.makeText(context, "server.cfg saved successfully!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.bg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Save", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        ConfigField(label = "Server Hostname", value = hostname, onValueChange = { hostname = it })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConfigField(label = "Port", value = port, onValueChange = { port = it }, modifier = Modifier.weight(1f))
            ConfigField(label = "Max Players", value = maxPlayers, onValueChange = { maxPlayers = it }, modifier = Modifier.weight(1f))
        }
        ConfigField(label = "RCON Password", value = rconPassword, onValueChange = { rconPassword = it })
        ConfigField(label = "Gamemode0", value = gamemode, onValueChange = { gamemode = it }, hint = "e.g. bare 1 or your_gamemode 1")
        ConfigField(label = "Filterscripts", value = filterscripts, onValueChange = { filterscripts = it }, hint = "Space-separated .amx names")
        ConfigField(label = "Plugins (.so)", value = plugins, onValueChange = { plugins = it }, hint = "e.g. mysql.so streamer.so sscanf.so")
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SampThemeColors.textSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
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
        if (hint != null) {
            Text(text = hint, fontSize = 12.sp, color = SampThemeColors.textMuted)
        }
    }
}
