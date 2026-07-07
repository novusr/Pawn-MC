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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserMode
import com.rvdjv.pawnmc.ui.theme.PawnMCTheme
import com.rvdjv.pawnmc.ui.theme.status_error
import com.rvdjv.pawnmc.ui.theme.status_error_container
import com.rvdjv.pawnmc.ui.theme.status_idle
import com.rvdjv.pawnmc.ui.theme.status_success
import com.rvdjv.pawnmc.ui.theme.status_success_container
import java.io.File
import java.text.DecimalFormat

private val SpaceXS = 4.dp
private val SpaceS = 8.dp
private val SpaceM = 16.dp
private val SpaceL = 20.dp
private val SpaceXL = 32.dp
private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(28.dp)
private val ActionButtonHeight = 56.dp
private const val OUTPUT_PLACEHOLDER = "Ready to compile...\n"
private val OutputPanelHeight = 240.dp

private enum class CompileStatus(val label: String) {
    IDLE("Idle"),
    COMPILING("Compiling"),
    SUCCESS("Success"),
    ERROR("Error")
}

private fun deriveStatus(isCompiling: Boolean, lastExitCode: Int?): CompileStatus = when {
    isCompiling -> CompileStatus.COMPILING
    lastExitCode == null -> CompileStatus.IDLE
    lastExitCode == 0 -> CompileStatus.SUCCESS
    else -> CompileStatus.ERROR
}

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SpaceL)
        ) {
            Spacer(modifier = Modifier.height(SpaceM))

            ScreenHeader(onSettingsClick = onSettingsClick)

            Spacer(modifier = Modifier.height(SpaceL))

            CompileActionCard(
                selectedFileName = viewModel.selectedFilePath?.let { File(it).name },
                selectedFileSize = viewModel.selectedFilePath?.let { formatFileSize(File(it).length()) },
                isCompiling = viewModel.isCompiling,
                onChangeFileClick = {
                    if (!isStoragePermissionGranted) {
                        showPermissionDialog = true
                    } else {
                        showFileBrowser = true
                    }
                },
                onCompileClick = {
                    viewModel.selectedFilePath?.let { path ->
                        viewModel.compileFile(
                            path = path,
                            isStoragePermissionGranted = isStoragePermissionGranted,
                            onPermissionRequired = { showPermissionDialog = true }
                        )
                    }
                }
            )

            viewModel.selectionError?.let { error ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SpaceS, start = SpaceXS)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS + 2.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(SpaceXL))

            CompilerLogsSection(
                outputText = viewModel.outputText,
                status = deriveStatus(viewModel.isCompiling, viewModel.lastExitCode),
                scrollState = outputScrollState,
                onCopyClick = {
                    val isEmpty = viewModel.outputText.isEmpty() ||
                        viewModel.outputText == OUTPUT_PLACEHOLDER
                    if (isEmpty) {
                        Toast.makeText(context, "Output is empty", Toast.LENGTH_SHORT).show()
                    } else {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Compilation Result", viewModel.outputText)
                        )
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(SpaceL))
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

@Composable
private fun ScreenHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Pawn MC",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Text(
                text = "Compiling ideas on the go.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun CompileActionCard(
    selectedFileName: String?,
    selectedFileSize: String?,
    isCompiling: Boolean,
    onChangeFileClick: () -> Unit,
    onCompileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(SpaceL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(SpaceM))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOURCE FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedFileName ?: "No file selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedFileSize ?: "Tap Change File to begin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(SpaceL))

            FilledTonalButton(
                onClick = onChangeFileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActionButtonHeight),
                shape = PillShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(SpaceS))
                Text("Browse File")
            }

            Spacer(modifier = Modifier.height(SpaceS))

            Button(
                onClick = onCompileClick,
                enabled = selectedFileName != null && !isCompiling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActionButtonHeight),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AnimatedContent(targetState = isCompiling, label = "compile_button_state") { compiling ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (compiling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(SpaceS))
                        Text(
                            text = if (compiling) "Compiling.." else "Compile",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompilerLogsSection(
    outputText: String,
    status: CompileStatus,
    scrollState: androidx.compose.foundation.ScrollState,
    onCopyClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(SpaceXS + 2.dp))
                Text(
                    text = "COMPILER LOGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopyClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy output",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(SpaceXS))
                StatusChip(status = status)
            }
        }

        Spacer(modifier = Modifier.height(SpaceS))

        OutputPanel(
            outputText = outputText,
            isCompiling = status == CompileStatus.COMPILING,
            scrollState = scrollState
        )
    }
}

@Composable
private fun StatusChip(status: CompileStatus) {
    val (dotColor, containerColor, contentColor) = when (status) {
        CompileStatus.IDLE -> Triple(
            status_idle,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        CompileStatus.COMPILING -> Triple(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        CompileStatus.SUCCESS -> Triple(
            status_success,
            status_success_container,
            status_success
        )
        CompileStatus.ERROR -> Triple(
            status_error,
            status_error_container,
            status_error
        )
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            AnimatedContent(targetState = status.label, label = "status_label") { label ->
                Text(text = label, style = MaterialTheme.typography.labelSmall)
            }
        },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor,
            disabledContainerColor = containerColor,
            disabledLabelColor = contentColor
        ),
        border = null
    )
}

@Composable
private fun OutputPanel(
    outputText: String,
    isCompiling: Boolean,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OutputPanelHeight, max = OutputPanelHeight),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = isCompiling) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Text(
                text = outputText,
                fontFamily = FontFamily.Monospace,
                fontStyle = if (outputText == OUTPUT_PLACEHOLDER) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(SpaceM)
            )
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val idx = digitGroups.coerceAtMost(units.size - 1)
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, idx.toDouble())) + " " + units[idx]
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

@Preview(showBackground = true)
@Composable
private fun CompileActionCardPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = "main.p",
            selectedFileSize = "12.4 KB",
            isCompiling = false,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompileActionCardEmptyPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = null,
            selectedFileSize = null,
            isCompiling = false,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompileActionCardCompilingPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = "gamemode.pwn",
            selectedFileSize = "48.2 KB",
            isCompiling = true,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 340)
@Composable
private fun CompilerLogsSectionPreview() {
    PawnMCTheme {
        CompilerLogsSection(
            outputText = "// System ready. Upload a .p file to begin.",
            status = CompileStatus.IDLE,
            scrollState = rememberScrollState(),
            onCopyClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MainScreenFullPreview() {
    PawnMCTheme {
        Column(modifier = Modifier.padding(SpaceL)) {
            ScreenHeader(onSettingsClick = {})
            Spacer(modifier = Modifier.height(SpaceL))
            CompileActionCard(
                selectedFileName = "main.p",
                selectedFileSize = "12.4 KB",
                isCompiling = false,
                onChangeFileClick = {},
                onCompileClick = {}
            )
            Spacer(modifier = Modifier.height(SpaceXL))
            CompilerLogsSection(
                outputText = "// System ready. Upload a .p file to begin.",
                status = CompileStatus.IDLE,
                scrollState = rememberScrollState(),
                onCopyClick = {}
            )
        }
    }
}