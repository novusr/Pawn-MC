package com.rvdjv.pawnmc.ui.main

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    initialUri: Uri? = null
) {
    val context = LocalContext.current
    val outputScrollState = rememberScrollState()

    // state dialog
    var showFileBrowser by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var isStoragePermissionGranted by remember {
        mutableStateOf(hasStoragePermission(context))
    }

    // permission launchers
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isStoragePermissionGranted = hasStoragePermission(context)
        if (!isStoragePermissionGranted) {
            showPermissionDialog = true
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isStoragePermissionGranted = permissions.entries.all { it.value }
        if (!isStoragePermissionGranted) {
            showPermissionDialog = true
        }
    }

    // load last selected file
    LaunchedEffect(Unit) {
        viewModel.loadLastSelectedFile()

        if (!hasStoragePermission(context)) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(initialUri) {
        viewModel.handleInitialUri(initialUri)
    }

    // autoscroll output
    LaunchedEffect(viewModel.outputText) {
        outputScrollState.animateScrollTo(outputScrollState.maxValue)
    }

    // permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Storage Permission Required") },
            text = {
                Text(
                    "This app needs access to all files to compile pawn files " +
                    "and write amx output to any location."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        requestStoragePermission(
                            context = context,
                            manageStorageLauncher = manageStorageLauncher,
                            legacyPermissionLauncher = legacyPermissionLauncher
                        )
                    }
                ) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // main scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pawn MC") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Text(
                text = viewModel.selectedFilePath?.let { File(it).name } ?: "No file selected",
                modifier = Modifier.padding(16.dp)
            )

            viewModel.selectionError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Button(
                onClick = {
                    if (!isStoragePermissionGranted) {
                        showPermissionDialog = true
                    } else {
                        showFileBrowser = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Select File")
            }

            Button(
                onClick = { 
                    viewModel.selectedFilePath?.let { path ->
                        viewModel.compileFile(
                            path = path,
                            isStoragePermissionGranted = isStoragePermissionGranted,
                            onPermissionRequired = { showPermissionDialog = true }
                        )
                    }
                },
                enabled = viewModel.selectedFilePath != null && !viewModel.isCompiling && isStoragePermissionGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Compile")
            }

            if (viewModel.isCompiling) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Output", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = {
                    val isEmpty = viewModel.outputText.isEmpty() || viewModel.outputText == "Ready to compile...\n"
                    if (isEmpty) {
                        Toast.makeText(context, "Output is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Compilation Result", viewModel.outputText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy output")
                }
            }

            Text(
                text = viewModel.outputText,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(outputScrollState)
                    .padding(horizontal = 16.dp)
            )
        }
    }

    // dialog file browser
    if (showFileBrowser) {
        FileBrowserDialog(
            mode = FileBrowserMode.FILE,
            onFileSelected = { path ->
                viewModel.selectFile(path)
                showFileBrowser = false
            },
            onDismiss = { showFileBrowser = false }
        )
    }
}

// helper functions
fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

fun requestStoragePermission(
    context: Context,
    manageStorageLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    legacyPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        manageStorageLauncher.launch(intent)
    } else {
        legacyPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }
}
