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
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration.Writer
import com.pocketsamp.server.configuration.DirectoryManager
import com.pocketsamp.server.user_interface.theme.*
import java.io.File

@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)
    val serverDir = remember { DirectoryManager.getServerDirectory(context) }
    val configFile = remember(serverDir) { File(serverDir, "server.cfg") }

    var config by remember(configFile) { mutableStateOf(Reader.read(configFile)) }

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
                    config = Reader.read(configFile)
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
                Text(
                    text = t("config.title"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SampThemeColors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = t("config.subtitle"),
                    fontSize = 12.sp,
                    color = SampThemeColors.textSecondary,
                    maxLines = 1
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
                    Writer.write(config, configFile)
                    Toast.makeText(context, "server.cfg saved successfully!", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.bg),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text = t("config.save"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        ConfigField(label = t("config.field.hostname"), value = hostname, onValueChange = { hostname = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConfigField(label = t("config.field.port"), value = port, onValueChange = { port = it }, modifier = Modifier.weight(1f))
            ConfigField(label = t("config.field.maxplayers"), value = maxPlayers, onValueChange = { maxPlayers = it }, modifier = Modifier.weight(1f))
        }
        ConfigField(label = t("config.field.rcon"), value = rconPassword, onValueChange = { rconPassword = it })
        ConfigField(label = t("config.field.gamemode"), value = gamemode, onValueChange = { gamemode = it }, hint = t("config.hint.gamemode"))
        ConfigField(label = t("config.field.filterscripts"), value = filterscripts, onValueChange = { filterscripts = it }, hint = t("config.hint.filterscripts"))
        ConfigField(label = t("config.field.plugins"), value = plugins, onValueChange = { plugins = it }, hint = t("config.hint.plugins"))
        Spacer(modifier = Modifier.height(52.dp))
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SampThemeColors.textSecondary, maxLines = 1)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
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
        if (hint != null) {
            Text(text = hint, fontSize = 11.sp, color = SampThemeColors.textMuted)
        }
    }
}
