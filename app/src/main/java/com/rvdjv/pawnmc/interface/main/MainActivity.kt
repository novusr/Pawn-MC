package com.rvdjv.pawnmc.`interface`.main

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.data.compiler.Explanations
import com.rvdjv.pawnmc.data.compiler.Compiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.config.AppStorage
import com.rvdjv.pawnmc.data.library.LibraryStore
import com.rvdjv.pawnmc.data.runtime.PocketSampGateway
import com.rvdjv.pawnmc.data.syntax.InternDat
import com.rvdjv.pawnmc.data.update.UpdateManager
import com.rvdjv.pawnmc.`interface`.editor.workspace.buildFolderPickerIntent
import com.rvdjv.pawnmc.`interface`.editor.workspace.buildFilePickerIntent
import com.rvdjv.pawnmc.`interface`.editor.workspace.resolveDocumentToFile
import com.rvdjv.pawnmc.`interface`.editor.workspace.resolveTreeToFile
import com.rvdjv.pawnmc.`interface`.editor.workspace.WorkspaceSession
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorScreen
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorViewModel
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorViewModelFactoryForActivity
import com.rvdjv.pawnmc.`interface`.settings.SettingsActivity
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme
import com.rvdjv.pawnmc.`interface`.theme.resolveDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private var initialUri by mutableStateOf<Uri?>(null)
    private var pendingXedRequest by mutableStateOf<XedRequest?>(null)

    /**
     * True once this activity was opened by PocketSAMP to show Xed.
     *
     * Set during construction, before the first frame: the main screen must never be
     * composed for a shortcut, otherwise it flashes for a fraction of a second before the
     * editor replaces it (that is exactly what tapping the entry by hand used to do).
     */
    private var launchedForXed = false

    init {
        launchedForXed = xedRequest(intent) != null
    }

    private data class XedRequest(
        val path: String,
        val workspace: Boolean,
        val requestId: Long
    )

    private fun xedRequest(intent: Intent): XedRequest? {
        if (intent.action != ACTION_OPEN_XED_PATH) return null
        val path = intent.getStringExtra(EXTRA_XED_PATH)?.takeIf(String::isNotBlank) ?: return null
        return XedRequest(
            path = path,
            workspace = intent.getBooleanExtra(EXTRA_XED_WORKSPACE, false),
            requestId = System.nanoTime()
        )
    }

    companion object {
        private const val ACTION_OPEN_XED_PATH = "com.rvdjv.pawnmc.action.OPEN_XED_PATH"
        private const val EXTRA_XED_PATH = "com.rvdjv.pawnmc.extra.XED_PATH"
        private const val EXTRA_XED_WORKSPACE = "com.rvdjv.pawnmc.extra.XED_WORKSPACE"
        private const val EXTRA_RETURN_TO_POCKETSAMP = "com.pocketsamp.server.extra.RETURN_TO_FILES"
        private const val EXTRA_XED_OPEN_FAILED = "com.pocketsamp.server.extra.XED_OPEN_FAILED"
    }

    private fun navigateBackToPocketSamp(openFailed: Boolean = false) {
        startActivity(
            Intent().apply {
                setClassName(packageName, "com.pocketsamp.server.MainActivity")
                putExtra(EXTRA_RETURN_TO_POCKETSAMP, true)
                putExtra(EXTRA_XED_OPEN_FAILED, openFailed)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
    }

    override fun attachBaseContext(newBase: Context) {
        // On a fresh install the device locale decides the starting language, so
        // even the very first frame is already localised. A set marker (or any
        // later launch) leaves the Settings language untouched.
        val config = CompilerConfig.getInstance(newBase)
        config.applyDeviceLocaleOnFirstLaunch(newBase)
        val language = config.n_app_language
        super.attachBaseContext(AppLocalization.localizedContext(newBase, language))
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    /**
     * Editor view model scoped to the activity, not to the editor screen.
     *
     * Keeping one instance alive means the opened workspaces and their recent
     * files survive leaving the editor, for example after compiling from it.
     */
    private val editorViewModel: EditorViewModel by viewModels {
        EditorViewModelFactoryForActivity(applicationContext)
    }

    /**
     * Scope for the work needed to bring the app's data tables up.
     *
     * It outlives [onCreate] on purpose: the tables are no longer read on the launch
     * path (that is what made the first frame wait seconds on a low-RAM device), so the
     * load has to keep running after the method that started it returns.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUri = intent.data
        pendingXedRequest = xedRequest(intent)
        launchedForXed = pendingXedRequest != null

    // The compiler libraries now ship as one archive per ABI inside the APK and live
    // in Documents/PawnMC/library/<abi>/ afterwards. Point the compiler at that folder
    // first, so nothing below has to know about the layout. Host context only:
    // listing a directory here would be IO on the first frame.
    Compiler.attachHost(applicationContext)

        // The first frame only needs the theme, which comes from SharedPreferences. Every
        // other startup task reads and parses a multi-hundred-kilobyte asset or walks the
        // file system, so it is handed to the IO dispatcher and the UI is drawn immediately
        // instead of after all of it. The UI is expected to appear first; nothing below is
        // required for it to render, and the loaders fall back to loading on demand if a
        // screen asks for a table before this finishes.
        startupScope.launch {
            val config = CompilerConfig.getInstance(applicationContext)
            // Include paths can point at folders the user deleted or an unmounted SD card.
            // Drop them once on startup so the list never shows dead entries and the
            // compiler is not handed stale -i values.
            config.pruneMissingIncludePaths()
            Compiler.resetSessionState()
            // The local compiler message explanations live in `data/_data_2026_exp.toml`; load
            // the table once so `explainCompilerOutput` never has to touch the disk per line.
            Explanations.load(applicationContext)
            // The localisation table and the Pawn symbol table are the two largest assets;
            // warming them here is what keeps the first screen from paying for them.
            AppLocalization.load(applicationContext)
            InternDat.load(applicationContext)
            // Everything user-owned now lives in Documents/PawnMC; copy the old
            // Android/data tree over once so an upgrade never strands the user's
            // scripts, logs or settings in the location an uninstall wipes.
            AppStorage.migrateLegacyDataRoot(applicationContext)
            // Unpack `lib/<abi>/<abi>.zip` into `library/<abi>/`. Idempotent: a folder
            // that already holds every library is left untouched, so this only costs a
            // directory listing after the first launch.
            LibraryStore.ensureExtracted(applicationContext)
            UpdateManager(applicationContext).ensureVersionFileWritten()
        }

        val config = CompilerConfig.getInstance(applicationContext)
        enableEdgeToEdge()
        // PocketSAMP is embedded in this process on the modern flavor; its global crash
        // handler is installed before the runtime UI can be opened, and its console is
        // mirrored into this app's own log store. Idempotent, so calling it on every
        // launch is safe. The gateway resolves the flavor implementation, so this source
        // set never references the runtime module directly (and no-ops on the Nougat
        // flavor, which has no runtime).
        PocketSampGateway.prepare(applicationContext) { line ->
            com.rvdjv.pawnmc.data.log._pocket_store.record(applicationContext, line)
        }
        // The ongoing session notification is a nicety, not a requirement, so the prompt
        // is fired once and a refusal only means the shade stays empty.
        requestNotificationPermissionIfNeeded()
        setContent {
            PawnMCTheme(darkTheme = resolveDarkTheme(viewModel.n_app_theme)) {
                // Covers SettingsActivity, which is a separate entry point.
                SideEffect {
                    config.pruneMissingIncludePaths()
                }

                var isEditorOpen by remember { mutableStateOf(launchedForXed) }
                // Null until the PocketSAMP shortcut finishes; null means "not a shortcut".
                // While it is false nothing is composed, which is what removes the main screen
                // flash between the tap and the editor.
                var xedShortcutOpened by remember { mutableStateOf(if (launchedForXed) null else false) }
                var returnToPocketSamp by remember { mutableStateOf(false) }
                val currentFilePath = viewModel.selectedFilePath

                LaunchedEffect(pendingXedRequest?.requestId) {
                    val request = pendingXedRequest ?: return@LaunchedEffect
                    val target = File(request.path)
                    val opened = if (request.workspace) {
                        if (!target.isDirectory || !target.canRead()) {
                            false
                        } else {
                            editorViewModel.workspace.openWorkspace(target)
                            editorViewModel.workspace.activeWorkspace?.root?.absolutePath == target.absolutePath
                        }
                    } else {
                        target.isFile && target.canRead() &&
                            editorViewModel.workspace.registerExternalFile(target)
                    }
                    // Opening the file is what makes the editor renderable, so the outcome is
                    // published before the editor flag: no frame draws the main screen between
                    // the shortcut and the editor, and a failure never leaves a blank surface.
                    xedShortcutOpened = opened
                    if (opened) isEditorOpen = true
                    returnToPocketSamp = opened
                    if (!opened) navigateBackToPocketSamp(openFailed = true)
                    pendingXedRequest = null
                }

                // The editor follows the file chosen on the main screen, without
                // recreating its view model so the workspaces stay intact.
                LaunchedEffect(currentFilePath) {
                    if (!currentFilePath.isNullOrBlank()) {
                        editorViewModel.attachFile(currentFilePath)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // A shortcut straight into Xed skips the main screen entirely: it is
                    // neither composed behind the editor nor shown for a frame before the
                    // editor appears, so the shortcut behaves like a direct transition
                    // instead of replaying a manual tap. While the pending request resolves,
                    // [xedShortcutOpened] is null and nothing is drawn at all.
                    val xedReady = editorViewModel.workspace.isWorkspaceOpen || !currentFilePath.isNullOrBlank()
                    val editorVisible = isEditorOpen && xedReady
                    // While a shortcut is still resolving, draw nothing rather than the main
                    // screen; once it fails, the main screen comes back with the error.
                    if (!editorVisible && xedShortcutOpened != null) {
                        MainScreen(
                            viewModel = viewModel,
                            editorOpen = isEditorOpen,
                            onEditorClick = {
                                isEditorOpen = true
                            },

                            onSettingsClick = {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            },
                            onOpenSettingsTarget = { target ->
                                startActivity(
                                    Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                        putExtra(SettingsActivity.EXTRA_FOCUS_TARGET, target.name)
                                    }
                                )
                            },
                            initialUri = initialUri,
                            workspaceAvailable = editorViewModel.workspace.isWorkspaceOpen,
                        )
                    }
                    if (editorVisible) {
                        EditorScreen(
                            viewModel = editorViewModel,
                            onNavigateBack = {
                                isEditorOpen = false
                                if (returnToPocketSamp) {
                                    returnToPocketSamp = false
                                    navigateBackToPocketSamp()
                                } else {
                                    viewModel.loadLastSelectedFile()
                                }
                            },
                            editorBackgroundColor = viewModel.n_editor_background_color,
                            compileOutput = when {
                                viewModel.isCompiling && viewModel.outputText.isBlank() -> "Compiling...\n"
                                viewModel.hasCompilerOutput -> viewModel.outputText
                                else -> ""
                            },
                            isCompiling = viewModel.isCompiling,
                            onCompileRequest = { path ->
                                viewModel.requestCompileFromEditor(path)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialUri = intent.data
        pendingXedRequest = xedRequest(intent)
        if (pendingXedRequest != null) launchedForXed = true
        if (intent.data != null) {
            viewModel.handleInitialUri(intent.data)
        }
    }

    override fun onResume() {
        super.onResume()
        CompilerConfig.reloadFromFile(applicationContext)
        viewModel.refreshTheme()
        viewModel.refreshLanguage()
        viewModel.refreshEditorBackgroundColor()
    }

    /**
     * Asks for `POST_NOTIFICATIONS` once on API 33+.
     *
     * The result is deliberately not observed: [MainNotification] re-checks the grant on
     * every update, so a later grant (from system settings) starts working without this
     * activity having to remember anything, and a refusal costs nothing but the shade.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
