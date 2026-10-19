package com.rvdjv.pawnmc.`interface`.main

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pinch
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.pocketsamp.server.PocketSamp
import com.pocketsamp.server.PocketSampLocales
import com.rvdjv.pawnmc.`interface`._Icons
import com.rvdjv.pawnmc.`interface`.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.`interface`.filebrowser.FileBrowserMode
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme
import com.rvdjv.pawnmc.`interface`.theme.status_error
import com.rvdjv.pawnmc.`interface`.theme.status_error_container
import com.rvdjv.pawnmc.`interface`.theme.status_idle
import com.rvdjv.pawnmc.`interface`.theme.status_success
import com.rvdjv.pawnmc.`interface`.theme.status_success_container
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.delay
import java.text.DecimalFormat

internal val SpaceXS = 4.dp
internal val SpaceS = 8.dp
internal val SpaceM = 16.dp
internal val SpaceL = 18.dp
internal val SpaceXL = 32.dp
internal val CardShape = RoundedCornerShape(20.dp)
internal val PillShape = RoundedCornerShape(28.dp)
internal val ActionButtonHeight = 48.dp
internal const val OUTPUT_PLACEHOLDER = "Ah, another project — a breath of fresh air everyone will inhale whether they asked for it or not.\n"
internal val OutputPanelHeight = 240.dp
internal fun shouldRestoreLastSelectedFile(workspaceAvailable: Boolean): Boolean = !workspaceAvailable

internal enum class CompileStatus(val label: String) {
    IDLE("IDLE :|"),
    COMPILING("COMPILING :?"),
    SUCCESS("SUCCESS :)"),
    ERROR("FAILED :(")
}

internal fun deriveStatus(isCompiling: Boolean, lastExitCode: Int?): CompileStatus = when {
    isCompiling -> CompileStatus.COMPILING
    lastExitCode == null -> CompileStatus.IDLE
    lastExitCode == 0 -> CompileStatus.SUCCESS
    else -> CompileStatus.ERROR
}

/** Localisation keys of the rotating taglines, in the order the table lists them. */
internal val TAGLINE_KEYS = listOf(
    "app.tagline",
    "app.tagline.2",
    "app.tagline.3",
    "app.tagline.4",
)

/**
 * Picks one tagline for this app run.
 *
 * `remember { }` in the caller makes this once per process, not once per recomposition:
 * the phrase stays on screen all session and only changes on the next launch. The seed is
 * wall-clock milliseconds so launches differ, while a fixed tagline table keeps the
 * phrasing predictable for localisation.
 */
internal fun randomTagline(
    localizer: AppLocalization?,
    appLanguage: CompilerConfig.AppLanguage,
    seed: Long = System.currentTimeMillis(),
): String {
    if (localizer == null) return "Compiling ideas on the go."
    val index = (seed % TAGLINE_KEYS.size.toLong()).toInt()
    return localizer.get(TAGLINE_KEYS[index], appLanguage, "Compiling ideas on the go.")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit,
    onEditorClick: () -> Unit,
    initialUri: Uri? = null,
    workspaceAvailable: Boolean = false,
    /**
     * Opens Settings focused on one section, used by the settings guide card.
     *
     * Kept separate from [onSettingsClick] so the caller can pass a target without the
     * screen having to know how Settings is launched. The default keeps the screen
     * previewable and testable on its own.
     */
    onOpenSettingsTarget: (SettingsTarget) -> Unit = { onSettingsClick() },
) {
    val context = LocalContext.current
    val outputScrollState = rememberScrollState()
    // Shared across screens so the localisation asset is read and parsed only once
    // per process; the main screen also warms it up in the background.
    val localizer = remember(context) { AppLocalization.shared(context) }
    val appLanguage = viewModel.n_app_language

    // state dialog
    var showFileBrowser by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    // Header actions start collapsed, so the corner only shows the three-dot toggle until
    // the user asks for them.
    var headerActionsVisible by remember { mutableStateOf(false) }
    val onToggleActions = { headerActionsVisible = !headerActionsVisible }
    var isStoragePermissionGranted by remember {
        mutableStateOf(hasStoragePermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val permissionGranted = hasStoragePermission(context)
                isStoragePermissionGranted = permissionGranted
                showPermissionDialog = !permissionGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    var pendingLogText by remember { mutableStateOf("") }
    val downloadLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(pendingLogText)
                } ?: error("Could not open the selected destination")
            }.isSuccess
            val message = if (saved) {
                localizer.get("main.output.download.success", appLanguage, "Log downloaded successfully")
            } else {
                localizer.get("main.output.download.failed", appLanguage, "Failed to download log")
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // A remembered Xed workspace owns startup selection. Do not revive the main
    // screen's stale single-file path (often TMP/unit.pwn) over an empty workspace.
    LaunchedEffect(Unit) {
        if (shouldRestoreLastSelectedFile(workspaceAvailable)) viewModel.loadLastSelectedFile()

        if (!hasStoragePermission(context)) {
            showPermissionDialog = true
        }
    }

    // A file opened from the system ("Open with" / tapping a .pwn) is only consumed once
    // the permission dialog is out of the way. It is deliberately not part of the startup
    // path: resolving the intent used to scan the file system before the first frame and
    // pushed the window past the touch-feedback timeout, so the launch felt stuck.
    LaunchedEffect(initialUri, isStoragePermissionGranted) {
        if (isStoragePermissionGranted) {
            viewModel.handleInitialUri(initialUri)
        }
    }

    // Xed stays open while this mounted screen handles its compile request.
    LaunchedEffect(viewModel.compileRequestId) {
        viewModel.consumePendingCompile()?.let { pendingPath ->
            if (!hasStoragePermission(context)) {
                showPermissionDialog = true
            } else {
                // The main screen also re-registers the last selected file, which
                // may still be running the ignore-case conversion for that folder.
                var n_waited = 0
                while (viewModel.isPreparingFilesystem && n_waited < 100) {
                    delay(200)
                    n_waited++
                }
                viewModel.compileFile(
                    path = pendingPath,
                    isStoragePermissionGranted = true,
                    onPermissionRequired = { showPermissionDialog = true }
                )
            }
        }
    }

    // autoscroll output
    LaunchedEffect(viewModel.outputText) {
        outputScrollState.animateScrollTo(outputScrollState.maxValue)
    }

    // permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(localizer.get("main.perm.grand.title", appLanguage, "Grand Permissions Required!")) },
            text = {
                Text(localizer.get("main.perm.grand.desc", appLanguage, "PawnMC needs Grand Permissions (All files access) so it can compile pawn files and write the amx output anywhere."))
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
                    Text(localizer.get("main.perm.grand.grant", appLanguage, "Grant"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(localizer.get("main.perm.grand.later", appLanguage, "Not now"))
                }
            }
        )
    }

    // The former self-test button now opens PocketSAMP, the embedded SA-MP server
    // runtime adopted from thirdparty/PocketSAMP. It runs as a separate activity on
    // top of this screen and returns here through its back handling, the same way
    // the Sora-based editor screen is hosted.
    fun openPocketSamp() {
        // The embedded runtime speaks the language the PawnMC settings hold and
        // follows the same theme (light/dark/system); the dark look is the same
        // semi-violet ramp as this interface.
        PocketSampLocales.setLanguage(
            when (appLanguage) {
                CompilerConfig.AppLanguage.ID -> PocketSampLocales.Lang.ID
                CompilerConfig.AppLanguage.ES -> PocketSampLocales.Lang.ES
                CompilerConfig.AppLanguage.RU -> PocketSampLocales.Lang.RU
                else -> PocketSampLocales.Lang.EN
            }
        )
        PocketSampLocales.setTheme(
            when (viewModel.n_app_theme) {
                CompilerConfig.AppTheme.LIGHT -> PocketSampLocales.Theme.LIGHT
                CompilerConfig.AppTheme.DARK -> PocketSampLocales.Theme.DARK
                else -> PocketSampLocales.Theme.SYSTEM
            }
        )
        PocketSamp.launch(context)
    }
    var showCompilingNotice by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.isCompiling) {
        if (viewModel.isCompiling) showCompilingNotice = true
    }

    fun showCompilingBlockedToast() {
        Toast.makeText(
            context,
            localizer.get("main.busy.toast", appLanguage, "Settings, editor, and self-test are unavailable while compiling"),
            Toast.LENGTH_SHORT
        ).show()
    }


    val listState = rememberLazyListState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // The cursor: a two-way jump affordance. At the top it sends the reader to
            // the settings guide at the bottom; once the bottom is reached it turns
            // around and carries them back to the top, exactly like the settings screen.
            val fabScope = rememberCoroutineScope()
            val atBottom = listState.isScrollInProgress.not() &&
                listState.canScrollForward.not()
            SmallFloatingActionButton(
                onClick = {
                    fabScope.launch {
                        if (atBottom) {
                            listState.animateScrollToItem(0)
                        } else {
                            listState.animateScrollToItem(Int.MAX_VALUE)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = if (atBottom) Icons.Filled.ArrowUpward else Icons.Filled.Webhook,
                    contentDescription = if (atBottom) {
                        localizer?.get("main.fab.top", appLanguage, "Back to the top") ?: "Back to the top"
                    } else {
                        localizer?.get("main.fab.portal", appLanguage, "Open the settings guide") ?: "Open the settings guide"
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = SpaceL)
        ) {
            item { Spacer(modifier = Modifier.height(SpaceM)) }

            item { ScreenHeader(
                isCompiling = viewModel.isCompiling,
                actionsVisible = headerActionsVisible,
                onToggleActions = onToggleActions,
                onEditorClick = {
                    if (viewModel.isCompiling) {
                        showCompilingBlockedToast()
                        return@ScreenHeader
                    }
                    if (!hasStoragePermission(context)) {
                        isStoragePermissionGranted = false
                        showPermissionDialog = true
                        return@ScreenHeader
                    }
                    val path = viewModel.selectedFilePath
                    when {
                        workspaceAvailable -> onEditorClick()
                        path.isNullOrBlank() -> {
                            viewModel.ensureTemporaryFileSelected()
                            Toast.makeText(
                                context,
                                viewModel.temporaryFileNotice ?: "This is a temporary file because you have not selected your own Pawn file yet.",
                                Toast.LENGTH_LONG
                            ).show()
                            onEditorClick()
                        }
                        !File(path).isFile -> {
                            Toast.makeText(
                                context,
                                "The selected Pawn file no longer exists. Browse and select it again.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        else -> onEditorClick()
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
                        openPocketSamp()
                    }
                },
                localizer = localizer,
                appLanguage = appLanguage
            ) }

            if (showCompilingNotice) {
                item {
                    Spacer(modifier = Modifier.height(SpaceS))
                    CompilingBlockedNotice(
                        localizer = localizer,
                        appLanguage = appLanguage,
                        isCompiling = viewModel.isCompiling,
                        onDismiss = { showCompilingNotice = false }
                    )
                }
            }


            item { Spacer(modifier = Modifier.height(SpaceL)) }

            item { CompileActionCard(
                selectedFileName = viewModel.selectedFilePath?.let { File(it).name },
                selectedFileSize = viewModel.selectedFilePath?.let { formatFileSize(File(it).length()) },
                isCompiling = viewModel.isCompiling || viewModel.isPreparingFilesystem,
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
            ) }

            viewModel.selectionError?.let { error -> item {
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
            } }

            viewModel.temporaryFileNotice?.let { notice -> item {
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
            } }

            viewModel.filesystemNotice?.let { notice -> item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = SpaceS, start = SpaceXS)
                ) {
                    Icon(
                        imageVector = if (viewModel.isPreparingFilesystem) {
                            Icons.Filled.Info
                        } else {
                            Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (viewModel.isPreparingFilesystem) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(SpaceXS + 2.dp))
                    Text(
                        text = notice,
                        color = if (viewModel.isPreparingFilesystem) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } }

            item { Spacer(modifier = Modifier.height(SpaceXL)) }

            item { CompilerLogsSection(
                outputText = viewModel.outputText,
                status = deriveStatus(viewModel.isCompiling, viewModel.lastExitCode),
                scrollState = outputScrollState,
                canDownload = !viewModel.isCompiling && viewModel.lastExitCode != null,
                onDownloadClick = {
                    pendingLogText = viewModel.outputText
                    val baseName = viewModel.selectedFilePath?.let { File(it).nameWithoutExtension }
                        ?.takeIf { it.isNotBlank() } ?: "pawnmc"
                    downloadLogLauncher.launch("$baseName.log")
                },
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
            ) }

            item { Spacer(modifier = Modifier.height(SpaceL)) }

            // The empty space under the compiler output used to be dead room. It now
            // carries the settings guide: one row per thing PawnMC can be configured to
            // do, each one opening Settings *at that section* rather than at the top, so
            // the card is a way in rather than an advertisement.
            item { DeveloperPortalPromoCard(
                onEntryClick = { target ->
                    if (viewModel.isCompiling) {
                        showCompilingBlockedToast()
                    } else {
                        onOpenSettingsTarget(target)
                    }
                },
                localizer = localizer,
                appLanguage = appLanguage
            ) }

            item { Spacer(modifier = Modifier.height(SpaceL)) }
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

/**
 * Gray used for the temporary drop shadow of the header action buttons.
 *
 * The same value is applied in the light and the dark theme, so the revealed menu reads
 * the same way no matter which theme is active.
 */
private val HeaderActionShadowColor = Color(0xFF9E9E9E)

/** Elevation of the header action shadow while the temporary menu is revealed. */
private val HeaderActionShadowElevation = 4.dp
private val HeaderActionButtonSize = 48.dp
private val HeaderActionIconSize = 24.dp

@Composable
internal fun ScreenHeader(
    onEditorClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelfTestClick: () -> Unit,
    actionsVisible: Boolean = false,
    onToggleActions: () -> Unit = {},
    isCompiling: Boolean = false,
    localizer: AppLocalization? = null,
    appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizer?.get("app.name", appLanguage, "PawnMC") ?: "PawnMC",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                // Picked once per process launch with a stable seed, not on every
                // recomposition: the tagline stays the same all session and changes
                // only on the next app start.
                text = remember { randomTagline(localizer, appLanguage) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderActionsToggleButton(
                expanded = actionsVisible,
                localizer = localizer,
                appLanguage = appLanguage,
                onToggle = onToggleActions
            )

            if (actionsVisible) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderActionButton(
                        onClick = onSelfTestClick,
                        enabled = !isCompiling,
                        testTag = "self_test_button",
                        icon = {
                            // PocketSAMP runtime: the SA-MP server console adopted from
                            // thirdparty/PocketSAMP now lives behind this button. The
                            // satellite reads as "launch a server".
                            Icon(
                                imageVector = Icons.Filled.SatelliteAlt,
                                contentDescription = localizer?.get("main.header.pocketsamp", appLanguage, "PocketSAMP server") ?: "PocketSAMP server",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HeaderActionButton(
                        onClick = onEditorClick,
                        enabled = !isCompiling,
                        testTag = "xed_button",
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.ContentCut,
                                contentDescription = localizer?.get("main.header.editor", appLanguage, "Editor") ?: "Editor",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )

                    HeaderActionButton(
                        onClick = onSettingsClick,
                        enabled = !isCompiling,
                        testTag = "settings_button",
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = localizer?.get("main.header.settings", appLanguage, "Settings") ?: "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * One entry of the temporary header menu.
 *
 * The button is a plain rounded-rectangle surface, and while the menu is open a gray drop
 * shadow is cast to the left of it so the three buttons read as one band. The shadow is
 * layered in front of an opaque surface, because a transparent background casts nothing.
 */
@Composable
private fun HeaderActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            // Extra room on the left so the gray shadow has somewhere to fall:
            // the band therefore extends from right to left across the three buttons.
            .padding(start = 6.dp)
            .shadow(
                elevation = HeaderActionShadowElevation,
                shape = RoundedCornerShape(14.dp),
                ambientColor = HeaderActionShadowColor,
                spotColor = HeaderActionShadowColor
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(HeaderActionButtonSize)
                .testTag(testTag)
        ) {
            Box(
                modifier = Modifier.size(HeaderActionIconSize),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
    }
}

/**
 * The three-dot control that shows and hides the header actions.
 *
 * Deliberately rendered without any shadow, so only the revealed menu carries the
 * temporary-state effect.
 */
@Composable
private fun HeaderActionsToggleButton(
    expanded: Boolean,
    localizer: AppLocalization?,
    appLanguage: CompilerConfig.AppLanguage,
    onToggle: () -> Unit
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .size(HeaderActionButtonSize)
            .testTag("header_actions_toggle_button")
    ) {
        Icon(
            imageVector = Icons.Filled.MoreHoriz,
            contentDescription = if (expanded) {
                localizer?.get("main.header.collapse", appLanguage, "Hide menu") ?: "Hide menu"
            } else {
                localizer?.get("main.header.expand", appLanguage, "Show menu") ?: "Show menu"
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(HeaderActionIconSize)
        )
    }
}
@Composable
internal fun CompilingBlockedNotice(
    localizer: AppLocalization,
    appLanguage: CompilerConfig.AppLanguage,
    isCompiling: Boolean,
    onDismiss: () -> Unit
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
                if (isCompiling) "main.busy.notice" else "main.busy.completed",
                appLanguage,
                if (isCompiling) {
                    "Settings, Xed Editor, and Self-Test are temporarily unavailable until compilation is complete."
                } else {
                    "Compilation finished. Settings, Xed Editor, and Self-Test are available again."
                }
            ),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = localizer.get(
                    "main.busy.dismiss",
                    appLanguage,
                    "Dismiss compilation notice"
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
@Composable
internal fun CompileActionCard(
    selectedFileName: String?,
    selectedFileSize: String?,
    isCompiling: Boolean,
    onChangeFileClick: () -> Unit,
    onCompileClick: () -> Unit,
    localizer: AppLocalization? = null,
    appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            selectedFileName?.let { name ->
                Text(
                    text = name,
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
                    imageVector = Icons.Filled.Pinch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(SpaceS))
                Text(localizer?.get("main.file.browse", appLanguage, "Browse File") ?: "Browse File")
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
                                imageVector = Icons.Filled.Bolt,
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
internal fun CompilerLogsSection(
    outputText: String,
    status: CompileStatus,
    scrollState: androidx.compose.foundation.ScrollState,
    canDownload: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onCopyClick: () -> Unit,
    localizer: AppLocalization? = null,
    appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
) {
    Column {
        // The header is overflow-safe on high-DPI screens: the title is capped at two
        // lines with ellipsis, so when the localized label grows too long (e.g. the
        // Russian "Вот результат компиляции:" at font scale 1.3+) it wraps or clips
        // instead of pushing the actions row and the status chip off screen.
        //
        // A second guard for the same DPI problem: the actions (download/copy chip) are
        // pinned to the end of the row with weight(1f, fill = false) on the title, which
        // means the title collapses BEFORE the actions do. Without that weight the long
        // label at font scale 1.3+ would measure its intrinsic width first and force the
        // actions off the right edge; with it the label always loses width first. The
        // `fill = false` variant keeps the label at its natural width while there is
        // room, so short strings like "Output:" stay one line wide instead of grabbing
        // the full row.
        Column(
            modifier = Modifier.fillMaxWidth()
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
                    text = localizer?.get("main.output.title", appLanguage, "Here is the compiler output:") ?: "Here is the compiler output:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(SpaceXS))
                IconButton(
                    onClick = onDownloadClick,
                    enabled = canDownload,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = localizer?.get(
                            "main.output.download.action",
                            appLanguage,
                            "Download output log"
                        ) ?: "Download output log",
                        tint = if (canDownload) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(16.dp)
                    )
                }
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
internal fun StatusChip(status: CompileStatus) {
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
                // Font-scale guard for the DPI problem: without a maxLines cap a
                // 1.3x font-scale user gets a two-line chip that pushes the header
                // row taller and squeezes the output panel below it.
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
internal fun OutputPanel(
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
                text = explainHighlightedText(outputText),
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


/**
 * Colour of the `[explain]` marker inside compiler output.
 *
 * The `0xFFF0F070` value comes straight from the palette the workspace panel and the
 * main-screen output panel both reference, so the two interfaces agree on the exact
 * shade. The leading `FF` is required: Compose packs colours as ARGB, and an
 * `0xFFF070` literal carries a zero alpha, which rendered the marker invisible.
 */
val ExplainMarkerColor = Color(0xFFF0F070)

private val EXPLAIN_MARKER_REGEX = Regex("(?i)\\[explain\\]")

/**
 * Splits [outputText] into plain runs and `[explain]` markers.
 *
 * Returned pairs carry the text and whether that text is a marker; interfaces render
 * the marker runs in [ExplainMarkerColor] (bright and important), and everything else
 * in the panel's normal colour. A blank input collapses to one non-marker run so a
 * caller never has to special-case the placeholder.
 */
internal fun splitExplainMarkers(outputText: String): List<Pair<String, Boolean>> {
    if (outputText.isBlank()) return listOf(outputText to false)
    val matches = EXPLAIN_MARKER_REGEX.findAll(outputText).toList()
    if (matches.isEmpty()) return listOf(outputText to false)
    val runs = mutableListOf<Pair<String, Boolean>>()
    var cursor = 0
    for (match in matches) {
        if (match.range.first > cursor) {
            runs += outputText.substring(cursor, match.range.first) to false
        }
        runs += outputText.substring(match.range) to true
        cursor = match.range.last + 1
    }
    if (cursor < outputText.length) {
        runs += outputText.substring(cursor) to false
    }
    return runs
}

/**
 * Builds an [AnnotatedString] of [outputText] with every `[explain]` marker painted
 * in [ExplainMarkerColor] and bold, so the hint stands out from the raw compiler
 * log around it while the rest of the text keeps the panel's normal styling.
 */
internal fun explainHighlightedText(outputText: String): AnnotatedString = buildAnnotatedString {
    for ((text, isMarker) in splitExplainMarkers(outputText)) {
        if (isMarker) {
            pushStyle(SpanStyle(color = ExplainMarkerColor, fontWeight = FontWeight.Bold))
            append(text)
            pop()
        } else {
            append(text)
        }
    }
}
