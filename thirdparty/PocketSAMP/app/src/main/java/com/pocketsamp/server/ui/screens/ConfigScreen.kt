package com.pocketsamp.server.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
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

    var hostname by remember { mutableStateOf(config.hostname) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var maxPlayers by remember { mutableStateOf(config.maxPlayers.toString()) }
    var rconPassword by remember { mutableStateOf(config.rconPassword) }
    var gamemode by remember { mutableStateOf(config.gamemode) }
    var filterscripts by remember { mutableStateOf(config.filterscripts) }
    var plugins by remember { mutableStateOf(config.plugins) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    color = TextPrimary
                )
                Text(
                    text = "server.cfg Editor",
                    fontSize = 12.sp,
                    color = TextSecondary
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Save", fontWeight = FontWeight.Bold)
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
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = SurfaceCardBorder,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            ),
            shape = RoundedCornerShape(10.dp)
        )
        if (hint != null) {
            Text(text = hint, fontSize = 10.sp, color = TextMuted)
        }
    }
}
