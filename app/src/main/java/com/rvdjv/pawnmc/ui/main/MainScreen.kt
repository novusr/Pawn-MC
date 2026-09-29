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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.ui.PawnIcons
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserMode
import com.rvdjv.pawnmc.ui.theme.PawnMCTheme
import com.rvdjv.pawnmc.ui.theme.status_error
import com.rvdjv.pawnmc.ui.theme.status_error_container
import com.rvdjv.pawnmc.ui.theme.status_idle
import com.rvdjv.pawnmc.ui.theme.status_success
import com.rvdjv.pawnmc.ui.theme.status_success_container
import com.rvdjv.pawnmc.data.config.AppLocalization
import java.io.File
import java.text.DecimalFormat

private val SpaceXS = 4.dp
private val SpaceS = 8.dp
private val SpaceM = 16.dp
private val SpaceL = 18.dp
private val SpaceXL = 32.dp
private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(28.dp)
private val ActionButtonHeight = 48.dp
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
    onEditorClick: () -> Unit,
    initialUri: Uri? = null
) {
    val context = LocalContext.current
    val outputScrollState = rememberScrollState()
    val localizer = remember(context) { AppLocalization.load(context) }
    val appLanguage = viewModel.n_app_language

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
            title = { Text(localizer.get("main.perm.title", appLanguage, "Storage Permission Required!")) },
            text = {
                Text(localizer.get("main.perm.desc", appLanguage, "This app needs access to all files to compile pawn files and write amx output to any location."))
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
                    Text(localizer.get("main.perm.grant", appLanguage, "Grant"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(localizer.get("main.perm.later", appLanguage, "Cancel"))
                }
            }
        )
    }

    var showSelfTestDialog by remember { mutableStateOf(false) }
    var selfTestResults by remember { mutableStateOf<List<AppSelfTestResult>>(emptyList()) }

    fun showCompilingBlockedToast() {
        Toast.makeText(
            context,
            localizer.get("main.busy.toast", appLanguage, "Settings, editor, and self-test are unavailable while compiling"),
            Toast.LENGTH_SHORT
        ).show()
    }

    if (showSelfTestDialog) {
        AlertDialog(
            onDismissRequest = {
                showSelfTestDialog = false
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(localizer.get("main.selftest.title", appLanguage, "Self Test Diagnostics"))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = localizer.get("main.selftest.desc", appLanguage, "Run application diagnostics to verify internal compilers, auto-detection, and include directories. Scroll up/down to view all test modules."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(SpaceM))

                    // Horizontally scrollable action row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                selfTestResults = AppSelfTestCatalog.runAll()
                                Toast.makeText(context, localizer.get("main.selftest.completed", appLanguage, "All tests completed"), Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(localizer.get("main.selftest.run_all", appLanguage, "Run All"))
                        }

                        if (selfTestResults.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    selfTestResults = emptyList()
                                }
                            ) {
                                Text(localizer.get("main.selftest.clear", appLanguage, "Clear"))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(SpaceM))

                    if (selfTestResults.isNotEmpty()) {
                        val passedCount = selfTestResults.count { it.passed }
                        val totalCount = selfTestResults.size
                        val passedWord = localizer.get("main.selftest.passed", appLanguage, "Passed")
                        val resultsTitle = localizer.get("main.selftest.results", appLanguage, "Test Results")
                        Text(
                            text = "$resultsTitle ($passedCount/$totalCount $passedWord):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(SpaceXS))
                        selfTestResults.forEach { result ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (result.passed)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(SpaceS)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = result.name,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (result.passed) "PASS" else "FAIL",
                                            color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = result.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(SpaceM))
                    }

                    Text(
                        text = localizer.get("main.selftest.catalog", appLanguage, "Test Module Catalog (scroll down):"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(SpaceXS))

                    AppSelfTestCatalog.list().forEach { test ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(SpaceS),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = test.title,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = test.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val singleRes = AppSelfTestCatalog.run(test.id)
                                        selfTestResults = (selfTestResults.filterNot { it.name == singleRes.name } + singleRes)
                                        val statusLabel = if (singleRes.passed) localizer.get("main.selftest.passed", appLanguage, "PASSED") else "FAILED"
                                        Toast.makeText(context, "${test.title}: $statusLabel", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(localizer.get("main.selftest.run", appLanguage, "Run"))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSelfTestDialog = false
                    }
                ) {
                    Text(localizer.get("main.selftest.close", appLanguage, "Close"))
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

            ScreenHeader(
                isCompiling = viewModel.isCompiling,
                onEditorClick = {
                    if (viewModel.isCompiling) {
                        showCompilingBlockedToast()
                        return@ScreenHeader
                    }
                    val path = viewModel.selectedFilePath
                    if (path.isNullOrBlank() || !File(path).exists()) {
                        viewModel.ensureTemporaryFileSelected()
                        Toast.makeText(
                            context,
                            viewModel.temporaryFileNotice ?: "This is a temporary file because you have not selected your own Pawn file yet.",
                            Toast.LENGTH_LONG
                        ).show()
                        onEditorClick()
                    } else if (!isStoragePermissionGranted && !hasStoragePermission(context)) {
                        showPermissionDialog = true
                    } else {
                        onEditorClick()
                    }
                },
                onSettingsClick = {
                    if (viewModel.isCompiling) {
                        showCompilingBlockedToast()
                    } else {
                        onSettingsClick()
                    }
                },
                onSelfTestClick = {
                    if (viewModel.isCompiling) {
                        showCompilingBlockedToast()
                    } else {
                        showSelfTestDialog = true
                    }
                },
                localizer = localizer,
                appLanguage = appLanguage
            )

            if (viewModel.isCompiling) {
                Spacer(modifier = Modifier.height(SpaceS))
                CompilingBlockedNotice(
                    localizer = localizer,
                    appLanguage = appLanguage
                )
            }

            if (selfTestResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(SpaceS))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(SpaceS)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cardTitle = localizer.get("main.selftest.card_title", appLanguage, "Self-Test Results")
                            val passedWord = localizer.get("main.selftest.passed", appLanguage, "Passed")
                            Text(
                                text = "$cardTitle (${selfTestResults.count { it.passed }}/${selfTestResults.size} $passedWord)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row {
                                TextButton(onClick = { showSelfTestDialog = true }) {
                                    Text(localizer.get("main.selftest.open", appLanguage, "Open Tests"))
                                }
                                TextButton(onClick = { selfTestResults = emptyList() }) {
                                    Text(localizer.get("main.selftest.dismiss", appLanguage, "Dismiss"))
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selfTestResults.forEach { res ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (res.passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = "${res.name.removePrefix("Self test: ")}: ${if (res.passed) "PASS" else "FAIL"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = if (res.passed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                },
                localizer = localizer,
                appLanguage = appLanguage
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

            viewModel.temporaryFileNotice?.let { notice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SpaceS, start = SpaceXS)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS + 2.dp))
                    Text(
                        text = notice,
                        color = MaterialTheme.colorScheme.primary,
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
                },
                localizer = localizer,
                appLanguage = appLanguage
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
private fun ScreenHeader(
    onEditorClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelfTestClick: () -> Unit,
    isCompiling: Boolean = false,
    localizer: AppLocalization? = null,
    appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
) {
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

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSelfTestClick,
                enabled = !isCompiling,
                modifier = Modifier.testTag("self_test_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = localizer?.get("main.header.selftest", appLanguage, "Self tests") ?: "Self tests",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onEditorClick,
                enabled = !isCompiling,
                modifier = Modifier.testTag("xed_button")
            ) {
                Icon(
                    imageVector = PawnIcons.CodeEdit,
                    contentDescription = localizer?.get("main.header.editor", appLanguage, "Editor") ?: "Editor",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onSettingsClick,
                enabled = !isCompiling,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = localizer?.get("main.header.settings", appLanguage, "Settings") ?: "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
private fun CompilingBlockedNotice(
    localizer: AppLocalization,
    appLanguage: CompilerConfig.AppLanguage
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpaceXS)
            .testTag("compiling_blocked_notice"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(SpaceXS + 2.dp))
        Text(
            text = localizer.get(
                "main.busy.notice",
                appLanguage,
                "Settings, Xed Editor, and Self-Test are temporarily unavailable until compilation is complete."
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
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
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (selectedFileName != null) {
                Text(
                    text = selectedFileName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedFileSize ?: "Unknown size",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No file selected",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(SpaceL))

            FilledTonalButton(
                onClick = onChangeFileClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ActionButtonHeight),
                shape = PillShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
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
                    text = "Compiler Output",
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
            MaterialTheme.colorScheme.surfaceContainerHighest,
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
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
            ScreenHeader(
                onEditorClick = {},
                onSettingsClick = {},
                onSelfTestClick = {}
            )
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
