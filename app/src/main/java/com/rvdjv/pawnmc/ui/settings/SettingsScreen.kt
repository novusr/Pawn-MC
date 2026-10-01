package com.rvdjv.pawnmc.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toastimport androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.rvdjv.pawnmc.data.config.AppLocalization
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.update.UpdateManager
import com.rvdjv.pawnmc.data.update.UpdateStatus
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserDialog
import com.rvdjv.pawnmc.ui.filebrowser.FileBrowserMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val THANK_YOU_ID_LINES = listOf(
    "Selamat datang di repositori resmi PawnMC.",
    "Di sini adalah tempat di mana PawnMC beroperasi, dan kami sangat menyambut kedatangan Anda dengan penuh rasa syukur.",
    "PawnMC adalah aplikasi Android yang secara khusus dibuat untuk mempermudah dan mempercepat proses pengembangan Anda sebagai developer Android.",
    "Kami juga ingin mengucapkan terima kasih yang sebesar-besarnya kepada para tester yang telah meluangkan waktu, tenaga, dan masukan berharga untuk membantu meningkatkan kualitas aplikasi ini.",
    "Apresiasi kami juga ditujukan kepada seluruh pengguna PawnMC yang telah mempercayai, menggunakan, dan mendukung perkembangan aplikasi ini dari waktu ke waktu.",
    "Semoga PawnMC terus bermanfaat, berkembang, dan menjadi tools yang membantu Anda dalam membuat proyek Pawn dengan lebih efisien.",
    "Discord Kami: https://discord.gg/2YqkmDvTch"
)

private val THANK_YOU_EN_LINES = listOf(
    "Welcome to the official PawnMC repository.",
    "This is the place where PawnMC operates, and we warmly welcome you with great appreciation.",
    "PawnMC is an Android application specifically designed to make your work as an Android developer easier, faster, and more efficient.",
    "We would also like to express our sincere gratitude to all testers who have given their time, feedback, and support to help improve the quality of this app.",
    "Our appreciation also goes to all PawnMC users who have trusted, used, and supported this application throughout its development.",
    "We hope PawnMC will continue to be useful, grow further, and become a valuable tool for your Pawn development workflow.",
    "Official Community: https://discord.gg/2YqkmDvTch"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onRestartRequested: () -> Unit
) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    var showIncludePathDialog by remember { mutableStateOf(false) }
    var showAddIncludePathDialog by remember { mutableStateOf(false) }
    var newIncludePathInput by remember { mutableStateOf("") }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showEditorBackgroundDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showManualRestartDialog by remember { mutableStateOf(false) }
    var editingIncludePathIndex by remember { mutableStateOf<Int?>(null) }
    var editingIncludePathValue by remember { mutableStateOf("") }
    var pendingVersion by remember { mutableStateOf<CompilerConfig.CompilerVersion?>(null) }
    var updateStatus by remember { mutableStateOf("Checking for updates...") }
    var updateReady by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
    var lastReleaseInfo by remember { mutableStateOf<String?>(null) }
    var releaseNotesMarkdown by remember { mutableStateOf("") }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }
    var pendingReleaseVersion by remember { mutableStateOf<String?>(null) }

    var appVersion by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun refreshUpdateStatus() {
        if (isCheckingUpdates) return

        coroutineScope.launch {
            isCheckingUpdates = true
            updateStatus = "Checking for updates..."

            try {
                val result = withContext(Dispatchers.IO) { updateManager.checkForUpdate() }
                val status = result.status

                if (status == UpdateStatus.UPDATE_AVAILABLE) {
                    val latestVersion = result.latestVersion ?: "unknown"
                    val release = result.release
                    val downloaded = if (release != null) {
                        withContext(Dispatchers.IO) { updateManager.downloadLatestApk(release) }
                    } else null

                    val hasValidApk = downloaded != null && downloaded.exists()
                    updateStatus = if (hasValidApk) "Update available: $latestVersion" else "Update available, but no APK is attached"
                    lastReleaseInfo = release?.releaseUrl
                    releaseNotesMarkdown = release?.bodyMarkdown.orEmpty().ifBlank { "No release notes provided." }
                    pendingReleaseVersion = latestVersion
                    downloadedApk = downloaded
                    updateReady = hasValidApk
                } else if (status == UpdateStatus.UP_TO_DATE) {
                    updateStatus = "You're on the latest version"
                    lastReleaseInfo = result.release?.releaseUrl
                    releaseNotesMarkdown = ""
                    pendingReleaseVersion = null
                    updateReady = false
                } else {
                    updateStatus = "Unable to check for updates"
                    releaseNotesMarkdown = ""
                    pendingReleaseVersion = null
                    updateReady = false
                }
            } catch (_: Exception) {
                updateStatus = "Unable to check for updates"
                updateReady = false
            } finally {
                isCheckingUpdates = false
            }
        }
    }

    // loadinfo version
    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            appVersion = "v${packageInfo.versionName}"
        } catch (_: PackageManager.NameNotFoundException) {
            appVersion = "v1.0.0"
        }

        refreshUpdateStatus()
    }

    val localizer = remember(context) { AppLocalization.load(context) }
    val appLanguage = viewModel.n_app_language

    // Safety net for paths that vanished while the app was running (e.g. the SD card was
    // ejected). MainActivity already prunes on startup; this keeps the visible list honest
    // if a removable volume disappears later.
    LaunchedEffect(Unit) {
        val removed = viewModel.pruneMissingIncludePaths()
        if (removed > 0) {
            Toast.makeText(
                context,
                localizer.get("settings.include.pruned", appLanguage, "Include paths that no longer exist were removed"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val generalTitle = localizer.get("settings.general", appLanguage, "General")
    val languageTitle = localizer.get("settings.language", appLanguage, "Language")
    val themeTitle = localizer.get("settings.theme", appLanguage, "Theme")
    val compilerTitle = localizer.get("settings.compiler.version", appLanguage, "Compiler Version")

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // scaffold
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(localizer.get("settings.title", appLanguage, "Settings")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = localizer.get("settings.back", appLanguage, "Back")
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            SectionIntro(
                text = localizer.get(
                    "settings.general.desc",
                    appLanguage,
                    "Core preferences applied across the whole app: interface language, colour theme, and which compiler is used to build your scripts."
                )
            )

            CategoryHeader(text = generalTitle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    NavigationRow(
                        title = languageTitle,
                        subtitle = viewModel.n_app_language.label,
                        description = localizer.get(
                            "settings.language.desc",
                            appLanguage,
                            "Language used for every piece of interface text, including compiler messages and settings descriptions."
                        ),
                        onClick = { showLanguageDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    CompilerVersionRow(
                        title = compilerTitle,
                        version = viewModel.n_compiler_version.label,
                        forced = viewModel.n_forced_compiler_mode,
                        description = localizer.get(
                            "settings.compiler.version.desc",
                            appLanguage,
                            "Chooses which pawncc release performs the compilation. Switching versions reloads the compiler library, so the app has to restart once the change is applied."
                        ),
                        forcedDescription = localizer.get(
                            "settings.compiler.forced.desc",
                            appLanguage,
                            "When forced, the version picked above is always used. When automatic, a compiler sitting next to the file you are editing is preferred whenever its version matches."
                        ),
                        onToggleForced = { viewModel.updateForcedCompilerMode(it) },
                        onClick = { showVersionDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    NavigationRow(
                        title = themeTitle,
                        subtitle = viewModel.n_app_theme.label,
                        description = localizer.get(
                            "settings.theme.desc",
                            appLanguage,
                            "Controls whether the app follows the system theme, or is pinned to light or dark. This choice also drives the Xed editor background."
                        ),
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            CategoryHeader(text = localizer.get("settings.compiler.options", appLanguage, "Compiler Options"))
            SectionIntro(
                text = localizer.get(
                    "settings.compiler.options.desc",
                    appLanguage,
                    "Flags passed straight through to pawncc when a script is built. Changes here take effect on the next compilation."
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SwitchRow(
                        title = localizer.get("settings.option.semicolons", appLanguage, "Mandatory Semicolons"),
                        description = localizer.get("settings.option.semicolons.desc", appLanguage, "Require semicolons at the end of statements"),
                        checked = viewModel.n_mandatory_semicolons,
                        onCheckedChange = { viewModel.updateMandatorySemicolons(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchRow(
                        title = localizer.get("settings.option.parentheses", appLanguage, "Mandatory Parentheses"),
                        description = localizer.get("settings.option.parentheses.desc", appLanguage, "Require parentheses in control statements"),
                        checked = viewModel.n_mandatory_parentheses,
                        onCheckedChange = { viewModel.updateMandatoryParentheses(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        val entries = CompilerConfig.DebugLevel.entries
                        val maxIndex = (entries.size - 1).coerceAtLeast(1)
                        val currentIndex = entries.indexOf(viewModel.n_debug_level).coerceIn(0, maxIndex)

                        Text(
                            text = localizer.get("settings.option.debug", appLanguage, "Debug Level") + ": ${viewModel.n_debug_level.label}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = localizer.get(
                                "settings.option.debug.desc",
                                appLanguage,
                                "Controls how much debug information and runtime checking the compiler generates."
                            ) + " " + viewModel.n_debug_level.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { floatValue: Float ->
                                val selectedLevel = entries[floatValue.roundToInt()]
                                viewModel.updateDebugLevel(selectedLevel)
                            },
                            valueRange = 0f..maxIndex.toFloat(),
                            steps = if (maxIndex > 1) maxIndex - 1 else 0
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        val entries = CompilerConfig.OptimizationLevel.entries
                        val maxIndex = (entries.size - 1).coerceAtLeast(1)
                        val currentIndex = entries.indexOf(viewModel.n_optimization_level).coerceIn(0, maxIndex)

                        Text(
                            text = localizer.get("settings.option.optimization", appLanguage, "Optimization Level") + ": ${viewModel.n_optimization_level.label}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = localizer.get(
                                "settings.option.optimization.desc",
                                appLanguage,
                                "Determines how aggressively the compiler simplifies the bytecode."
                            ) + " " + viewModel.n_optimization_level.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = currentIndex.toFloat(),
                            onValueChange = { floatValue: Float ->
                                val selectedLevel = entries[floatValue.roundToInt()]
                                viewModel.updateOptimizationLevel(selectedLevel)
                            },
                            valueRange = 0f..maxIndex.toFloat(),
                            steps = if (maxIndex > 1) maxIndex - 1 else 0
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchRow(
                        title = localizer.get("settings.option.ignorecase", appLanguage, "Filesystem"),
                        description = localizer.get("settings.option.ignorecase.desc", appLanguage, "Backs the folder up as \"folder.backup\", then lowercases every file name except the one you selected and rewrites every #include reference to lowercase. It runs once per folder because Android storage is case sensitive."),
                        checked = viewModel.n_ignore_case,
                        onCheckedChange = { viewModel.updateIgnoreCase(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchRow(
                        title = localizer.get("settings.option.explain", appLanguage, "Explain Output"),
                        description = localizer.get("settings.option.explain.desc", appLanguage, "Add human-readable explanations next to warnings, errors, and fatal messages extracted from the compiler log."),
                        checked = viewModel.n_explain_output,
                        onCheckedChange = { viewModel.updateExplainOutput(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = localizer.get("settings.option.customflags", appLanguage, "Custom Flags"),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = localizer.get("settings.option.customflags.desc", appLanguage, "Extra compiler parameters, space separated"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = viewModel.n_custom_flags,
                            onValueChange = { viewModel.updateCustomFlags(it) },
                            placeholder = { Text("e.g. -C+ -v=0 -w217") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3
                        )
                    }
                }
            }

            CategoryHeader(text = localizer.get("settings.updates", appLanguage, "Updates"))
            SectionIntro(
                text = localizer.get(
                    "settings.updates.desc",
                    appLanguage,
                    "Checks GitHub for a newer release and downloads the APK when one is attached. Updates are installed through the standard Android system dialog."
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = updateStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (updateReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (lastReleaseInfo != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Release: $lastReleaseInfo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val updateButtonText = if (updateReady) {
                        localizer.get("settings.updates.ready", appLanguage, "Update PawnMC")
                    } else {
                        localizer.get("settings.updates.check", appLanguage, "Check for updates")
                    }
                    TextButton(
                        onClick = {
                            if (updateReady) {
                                if (releaseNotesMarkdown.isNotBlank()) {
                                    showReleaseNotesDialog = true
                                    return@TextButton
                                }

                                if (!updateManager.canRequestUnknownSources()) {
                                    val unknownSources = updateManager.buildUnknownSourcesIntent()
                                    ContextCompat.startActivity(context, unknownSources, null)
                                    return@TextButton
                                }

                                val apkFile = downloadedApk ?: return@TextButton
                                val installIntent = updateManager.buildInstallIntent(apkFile)
                                installLauncher.launch(installIntent)
                                return@TextButton
                            }

                            if (!isCheckingUpdates) {
                                refreshUpdateStatus()
                            }
                        },
                        enabled = !isCheckingUpdates,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCheckingUpdates) "Checking..." else updateButtonText)
                    }
                }
            }

            CategoryHeader(text = localizer.get("settings.include.paths", appLanguage, "Include Paths"))
            SectionIntro(
                text = localizer.get(
                    "settings.include.paths.desc",
                    appLanguage,
                    "Folders the compiler searches when resolving #include. Paths that no longer exist are pruned automatically on app start."
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    CompilerVersionRow(
                        title = localizer.get("settings.include.paths", appLanguage, "Include Paths"),
                        version = if (viewModel.n_forced_include_path_auto) "Forced" else "Auto",
                        forced = viewModel.n_forced_include_path_auto,
                        onToggleForced = { viewModel.updateForcedIncludePathAuto(it) },
                        onClick = { viewModel.updateForcedIncludePathAuto(!viewModel.n_forced_include_path_auto) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    if (viewModel.n_include_paths.isEmpty()) {
                        Text(
                            text = localizer.get("settings.include.empty", appLanguage, "No include paths configured"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        viewModel.n_include_paths.forEachIndexed { index, path ->
                            PathRow(
                                path = path,
                                onClick = {
                                editingIncludePathIndex = index
                                editingIncludePathValue = path
                                viewModel.clearIncludePathExtensionNotice()
                            },
                                onRemoveClick = { viewModel.removeIncludePathAt(index) }
                            )
                            if (index < viewModel.n_include_paths.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 1.2.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = localizer.get("settings.include.paths.notice.title", appLanguage, "Note"),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = localizer.get(
                                "settings.include.paths.notice.desc",
                                appLanguage,
                                "PawnMC v1.5.1 introduces a new system. " +
                                    "You no longer need to manually add an include path if you use 'pawno/include'. " +
                                    "The system will automatically apply 'pawno/include' and 'gamemodes' when you select or " +
                                    "browse a file for compilation. " +
                                    "Extensions .pawn, .pwn, .p and .inc are removed automatically from include paths. "
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    viewModel.includePathExtensionNotice?.let { strippedPath ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = localizer.get(
                                    "settings.include.extension.stripped",
                                    appLanguage,
                                    "The script extension was removed and the path was saved as: "
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = strippedPath,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ActionRow(
                        text = localizer.get("settings.include.add", appLanguage, "Add Include Path"),
                        icon = Icons.Default.Add,
                        onClick = {
                            newIncludePathInput = ""
                            viewModel.clearIncludePathExtensionNotice()
                            showAddIncludePathDialog = true
                        }
                    )
                }
            }

            CategoryHeader(text = localizer.get("settings.editor", appLanguage, "Xed Editor"))
            SectionIntro(
                text = localizer.get(
                    "settings.editor.desc",
                    appLanguage,
                    "Appearance preferences for the built-in code editor. Changes apply to the open editor immediately."
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    NavigationRow(
                        title = localizer.get("settings.editor.background", appLanguage, "Editor Background"),
                        subtitle = viewModel.n_editor_background_color
                            ?: localizer.get("settings.editor.background.default", appLanguage, "Default"),
                        description = localizer.get(
                            "settings.editor.background.desc",
                            appLanguage,
                            "Pick the editor canvas colour. The default follows the app theme, while a custom pick replaces it completely."
                        ),
                        onClick = { showEditorBackgroundDialog = true }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    ActionRow(
                        text = localizer.get("settings.editor.background.reset", appLanguage, "Reset to default"),
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.updateEditorBackgroundColor(null) }
                    )
                }
            }

            CategoryHeader(text = localizer.get("settings.about", appLanguage, "About"))
            SectionIntro(
                text = localizer.get(
                    "settings.about.desc",
                    appLanguage,
                    "Application version information and a link to the source repository."
                )
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = localizer.get("settings.about.version", appLanguage, "App Version"),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = appVersion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = localizer.get(
                                    "settings.about.version.desc",
                                    appLanguage,
                                    "The PawnMC release currently installed on this device."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = localizer.get(
                                "settings.about.github.desc",
                                appLanguage,
                                "Opens the official source repository for release history, issue reports, and building your own copy."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionRow(
                        text = localizer.get("settings.about.github", appLanguage, "View on GitHub"),
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/novusr/Pawn-MC"))
                            ContextCompat.startActivity(context, intent, null)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = localizer.get(
                                "settings.about.restart.desc",
                                appLanguage,
                                "Closes and reopens PawnMC so every setting is applied from scratch. A compilation that is currently running is cancelled."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ActionRow(
                        text = localizer.get("settings.about.restart", appLanguage, "Restart Application"),
                        icon = Icons.Default.Refresh,
                        onClick = { showManualRestartDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            CategoryHeader(text = localizer.get("settings.thanks", appLanguage, "Thank You"))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Indonesian | Notes",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    THANK_YOU_ID_LINES.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.2.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "English | Notes",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    THANK_YOU_EN_LINES.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text("Update ${pendingReleaseVersion ?: "available"}") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    MarkdownPreviewText(releaseNotesMarkdown)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReleaseNotesDialog = false
                        if (!updateManager.canRequestUnknownSources()) {
                            val unknownSources = updateManager.buildUnknownSourcesIntent()
                            ContextCompat.startActivity(context, unknownSources, null)
                            return@TextButton
                        }

                        val apkFile = downloadedApk ?: return@TextButton
                        val installIntent = updateManager.buildInstallIntent(apkFile)
                        installLauncher.launch(installIntent)
                    }
                ) {
                    Text("Install")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReleaseNotesDialog = false }) {
                    Text(localizer.get("settings.close", appLanguage, "Close"))
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(localizer.get("settings.theme.selector", appLanguage, "Select Theme")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompilerConfig.AppTheme.entries.forEachIndexed { index, theme ->
                        RadioButtonRow(
                            text = theme.label,
                            description = theme.description,
                            selected = viewModel.n_app_theme == theme,
                            onClick = {
                                viewModel.updateAppTheme(theme)
                                showThemeDialog = false
                            }
                        )

                        if (index < CompilerConfig.AppTheme.entries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(localizer.get("settings.close", appLanguage, "Close"))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(localizer.get("settings.language.selector", appLanguage, "Select Language")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompilerConfig.AppLanguage.entries.forEachIndexed { index, language ->
                        RadioButtonRow(
                            text = language.label,
                            description = language.description,
                            selected = viewModel.n_app_language == language,
                            onClick = {
                                // Persisted through CompilerConfig, so the choice
                                // immediately applies to every screen.
                                viewModel.updateAppLanguage(language)
                                showLanguageDialog = false
                            }
                        )

                        if (index < CompilerConfig.AppLanguage.entries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(localizer.get("settings.close", appLanguage, "Close"))
                }
            }
        )
    }

    if (showEditorBackgroundDialog) {
        EditorBackgroundDialog(
            initialHex = viewModel.n_editor_background_color,
            language = appLanguage,
            context = context,
            onDismiss = { showEditorBackgroundDialog = false },
            onApply = { hex ->
                viewModel.updateEditorBackgroundColor(hex)
                showEditorBackgroundDialog = false
            }
        )
    }

    if (showAddIncludePathDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddIncludePathDialog = false
                newIncludePathInput = ""
            },
            title = { Text(localizer.get("settings.include.add", appLanguage, "Add Include Path")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newIncludePathInput,
                        onValueChange = { newIncludePathInput = it },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("/storage/emulated/0/.../include/") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                                val value = newIncludePathInput.trim()
                                if (value.isNotBlank() && !viewModel.addIncludePath(value)) {
                                    Toast.makeText(
                                        context,
                                        localizer.get("settings.include.rejected", appLanguage, "Path already added or folder not found"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                showAddIncludePathDialog = false
                                newIncludePathInput = ""
                            }
                ) {
                    Text(localizer.get("settings.save", appLanguage, "Save"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddIncludePathDialog = false
                        newIncludePathInput = ""
                    }
                ) {
                    Text(localizer.get("settings.cancel", appLanguage, "Cancel"))
                }
            }
        )
    }

    if (editingIncludePathIndex != null) {
        AlertDialog(
            onDismissRequest = {
                editingIncludePathIndex = null
                editingIncludePathValue = ""
            },
            title = { Text("Edit Include Path") },
            text = {
                OutlinedTextField(
                    value = editingIncludePathValue,
                    onValueChange = { editingIncludePathValue = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("/storage/emulated/0/.../include/") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val index = editingIncludePathIndex ?: return@TextButton
                        if (!viewModel.updateIncludePathAt(index, editingIncludePathValue)) {
                            Toast.makeText(
                                context,
                                localizer.get("settings.include.rejected", appLanguage, "Path already added or folder not found"),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        editingIncludePathIndex = null
                        editingIncludePathValue = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingIncludePathIndex = null
                        editingIncludePathValue = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIncludePathDialog) {
        FileBrowserDialog(
            mode = FileBrowserMode.FOLDER,
            onFileSelected = {},
            onFolderSelected = { path ->
                if (!viewModel.addIncludePath(path)) {
                    Toast.makeText(
                        context,
                        localizer.get("settings.include.rejected", appLanguage, "Path already added or folder not found"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
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

    // dialog manual restart
    if (showManualRestartDialog) {
        AlertDialog(
            onDismissRequest = { showManualRestartDialog = false },
            title = { Text(localizer.get("settings.restart.confirm.title", appLanguage, "Restart Application")) },
            text = {
                Text(
                    localizer.get(
                        "settings.restart.confirm.text",
                        appLanguage,
                        "PawnMC will close and reopen immediately. Save any open file in the Xed Editor first, " +
                            "because a compilation that is currently running is cancelled. " +
                            "Would you like to restart now?"
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualRestartDialog = false
                        onRestartRequested()
                    }
                ) {
                    Text(localizer.get("settings.restart.confirm.button", appLanguage, "Restart Now"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualRestartDialog = false }) {
                    Text(localizer.get("settings.cancel", appLanguage, "Cancel"))
                }
            }
        )
    }

    //dialog version change
    if (showVersionDialog) {
        AlertDialog(
            onDismissRequest = { showVersionDialog = false },
            title = {
                Text(
                    text = "Select Compiler Version",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CompilerConfig.CompilerVersion.entries.forEachIndexed { index, version ->
                        RadioButtonRow(
                            text = version.label,
                            description = version.description,
                            selected = viewModel.n_compiler_version == version,
                            onClick = {
                                viewModel.updateCompilerVersion(version)
                                showVersionDialog = false
                                if (viewModel.isRestartRequired(version)) {
                                    pendingVersion = version
                                    showRestartDialog = true
                                }
                            }
                        )

                        if (index < CompilerConfig.CompilerVersion.entries.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVersionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MarkdownPreviewText(markdown: String, modifier: Modifier = Modifier) {
    val rendered = remember(markdown) {
        markdown
            .replace("\r\n", "\n")
            .split("\n")
            .joinToString("\n") { line ->
                when {
                    line.trimStart().startsWith("### ") -> line.trimStart().removePrefix("### ")
                    line.trimStart().startsWith("## ") -> line.trimStart().removePrefix("## ")
                    line.trimStart().startsWith("# ") -> line.trimStart().removePrefix("# ")
                    line.trimStart().startsWith("- ") -> "• ${line.trimStart().removePrefix("- ")}"
                    line.trimStart().startsWith("* ") -> "• ${line.trimStart().removePrefix("* ")}"
                    else -> line
                }
            }
            .replace("**", "")
            .replace("__", "")
            .replace("_", "")
    }

    Text(
        text = rendered.ifBlank { "No release notes provided." },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

@Composable
fun CompilerVersionRow(
    title: String = "Compiler Version",
    version: String,
    forced: Boolean,
    onToggleForced: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    forcedDescription: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(
            onClick = { onToggleForced(!forced) },
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(if (forced) "Set Auto" else "Set Forced")
        }
    }

    if (forcedDescription != null) {
        Text(
            text = forcedDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        )
    }
}

@Composable
fun CategoryHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            .fillMaxWidth()
    )
}

/**
 * Explanatory paragraph rendered directly under a [CategoryHeader].
 *
 * Settings used to jump from a section title straight into its card, which left
 * every entry a bare title. This gives each section a short lead-in so the group
 * reads as a labelled area rather than a list of toggles.
 */
@Composable
fun SectionIntro(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp)
            .fillMaxWidth()
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun RadioButtonRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ActionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun PathRow(
    path: String,
    onClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRemoveClick) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun NavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (description != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * Picker for the Xed editor canvas colour.
 *
 * Free-form hex entry plus a swatch grid, rather than a fixed list, so any
 * colour the user wants is reachable. The typed value is only committed once it
 * parses, so an in-progress or malformed `#` never reaches the preference; the
 * `Default` action clears the override and hands the canvas back to the theme.
 */
@Composable
private fun EditorBackgroundDialog(
    initialHex: String?,
    language: CompilerConfig.AppLanguage,
    context: Context,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit
) {
    val localizer = remember(context) { AppLocalization.load(context) }
    val presets = EditorBackgroundPresets

    // The typed text is the single source of truth for the dialog: an empty
    // field means "follow the app theme", so there is no separate selection
    // state that could drift away from what the user actually sees.
    var input by remember { mutableStateOf(initialHex?.removePrefix("#").orEmpty()) }
    val parsed = remember(input) { CompilerConfig.normalizeEditorBackgroundColor(input) }
    val invalid = input.isNotBlank() && parsed == null
    val previewArgb = remember(parsed) { parsed?.let(::hexToArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizer.get("settings.editor.background", language, "Editor Background")) },
        text = {
            // Scrollable because the swatch grid plus the hex field can exceed
            // the dialog height on a compact screen or with a large font scale.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = localizer.get(
                        "settings.editor.background.desc",
                        language,
                        "Pick the editor canvas colour. The default follows the app theme, while a custom pick replaces it completely."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live preview of the canvas behind a short sample line.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            previewArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "new Float:x = 1.0;",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = previewArgb?.let {
                            if (luminance(it) > 0.5f) Color.Black else Color.White
                        } ?: MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        input = raw.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(6)
                    },
                    label = { Text(localizer.get("settings.editor.background.hex", language, "Hex colour code")) },
                    prefix = { Text("#") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        {
                            Text(
                                localizer.get(
                                    "settings.editor.background.invalid",
                                    language,
                                    "Invalid colour code, expected the #RRGGBB format"
                                )
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Preset swatches, laid out as a fixed 5-column grid.
                presets.chunked(EDITOR_BACKGROUND_SWATCH_COLUMNS).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowColors.forEach { preset ->
                            val isSelected = previewArgb == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(preset))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        input = String.format("%06X", preset and 0xFFFFFF)
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                TextButton(onClick = { input = "" }) {
                    Text(localizer.get("settings.editor.background.default", language, "Default"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(parsed) },
                enabled = !invalid
            ) {
                Text(localizer.get("settings.save", language, "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localizer.get("settings.cancel", language, "Cancel"))
            }
        }
    )
}

/** Columns in the editor-background swatch grid. */
private const val EDITOR_BACKGROUND_SWATCH_COLUMNS = 5

/**
 * Swatches offered by [EditorBackgroundDialog].
 *
 * Light and dark variants are balanced so the grid covers both ends of the
 * brightness range, which is what a user actually needs when matching a code
 * editor to a project or a screenshot. The count is a multiple of
 * [EDITOR_BACKGROUND_SWATCH_COLUMNS] so the grid has no ragged last row.
 */
private val EditorBackgroundPresets = listOf(
    0xFFFFFFFF.toInt(), 0xFFFBF7EF.toInt(), 0xFFF6F3FF.toInt(), 0xFFEAF2FF.toInt(), 0xFFE9F7EF.toInt(),
    0xFFFFF4E5.toInt(), 0xFFFDEBF0.toInt(), 0xFFE7F6F8.toInt(), 0xFFF1F5F9.toInt(), 0xFFD6DCE3.toInt(),
    0xFFCBD5E1.toInt(), 0xFF2B2B2B.toInt(), 0xFF1E1E1E.toInt(), 0xFF1B1B2F.toInt(), 0xFF232733.toInt(),
    0xFF0D1B2A.toInt(), 0xFF111D13.toInt(), 0xFF2A1F14.toInt(), 0xFF26171D.toInt(), 0xFF12242A.toInt()
)

/** BT.601 luma of an ARGB colour, used to pick readable preview text. */
private fun luminance(argb: Int): Float {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
}

/** Converts a validated `#RRGGBB` string to an opaque ARGB int. */
private fun hexToArgb(hex: String): Int =
    0xFF000000.toInt() or (hex.removePrefix("#").toIntOrNull(16) ?: 0)
