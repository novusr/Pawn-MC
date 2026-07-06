package com.rvdjv.pawnmc.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onRestartRequested: () -> Unit
) {
    val context = LocalContext.current

    var showIncludePathDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var pendingVersion by remember { mutableStateOf<CompilerConfig.CompilerVersion?>(null) }

    var appVersion by remember { mutableStateOf("") }
    var buildNumber by remember { mutableStateOf("") }

    // loadinfo versi
    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = "v${packageInfo.versionName}"
            buildNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            appVersion = "v1.0.0"
            buildNumber = "0"
        }
    }

    // scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // compiler
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Compiler Version", style = MaterialTheme.typography.titleMedium)
                    CompilerConfig.CompilerVersion.entries.forEach { version ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = viewModel.compilerVersion == version,
                                onClick = {
                                    viewModel.updateCompilerVersion(version)
                                    if (viewModel.isRestartRequired(version)) {
                                        pendingVersion = version
                                        showRestartDialog = true
                                    }
                                }
                            )
                            Text(version.label)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // debug
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Debug Level", style = MaterialTheme.typography.titleMedium)
                    CompilerConfig.DebugLevel.entries.forEach { level ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = viewModel.debugLevel == level,
                                onClick = { viewModel.updateDebugLevel(level) }
                            )
                            Text("${level.label} - ${level.description}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // parameter lain
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Code Style", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = viewModel.mandatorySemicolons,
                            onCheckedChange = { viewModel.updateMandatorySemicolons(it) }
                        )
                        Text("Mandatory Semicolons")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = viewModel.mandatoryParentheses,
                            onCheckedChange = { viewModel.updateMandatoryParentheses(it) }
                        )
                        Text("Mandatory Parentheses")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // parameter lain
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Custom Flags", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = viewModel.customFlags,
                        onValueChange = { viewModel.updateCustomFlags(it) },
                        label = { Text("Flags (space separated)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // include path
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Include Paths", style = MaterialTheme.typography.titleMedium)

                    if (viewModel.includePaths.isEmpty()) {
                        Text("No include paths added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        viewModel.includePaths.forEachIndexed { index, path ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = path,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        viewModel.removeIncludePathAt(index)
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { showIncludePathDialog = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Add Include Path")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            //about
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Text("App Version: $appVersion")
                    Text("Build Number: $buildNumber")
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/novusr/Pawn-MC"))
                            ContextCompat.startActivity(context, intent, null)
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("View on GitHub")
                    }
                }
            }
        }
    }

    // dialog include path folder picker
    if (showIncludePathDialog) {
        FileBrowserDialog(
            mode = FileBrowserMode.FOLDER,
            onFileSelected = {}, 
            onFolderSelected = { path ->
                viewModel.addIncludePath(path)
                showIncludePathDialog = false
            },
            onDismiss = { showIncludePathDialog = false }
        )
    }

    // dialog restart
    if (showRestartDialog && pendingVersion != null) {
        val currentVersion = viewModel.getLoadedVersion()
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Required") },
            text = {
                Text(
                    "Compiler version has been changed from ${currentVersion?.label ?: "unknown"} " +
                    "to ${pendingVersion?.label}.\n\n" +
                    "Due to Android limitations, the change will take effect after restarting the app.\n\n" +
                    "Would you like to restart now?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        onRestartRequested()
                        // Restart app
                        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        onNavigateBack()
                    }
                ) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}
