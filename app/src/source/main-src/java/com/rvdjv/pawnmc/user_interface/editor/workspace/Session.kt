package com.rvdjv.pawnmc.user_interface.editor.workspace

import android.content.Context
import android.os.Build
import android.os.FileObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.apps_data.data_config.PathStore
import com.rvdjv.pawnmc.user_interface.editor.xedapi.EditorScreen
import com.rvdjv.pawnmc.user_interface.editor.xedapi.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Hard cap on how many entries a workspace scan materialises. */
private const val MAX_WORKSPACE_ENTRIES = 4000
private class WorkspaceScanBudget(var remaining: Int = MAX_WORKSPACE_ENTRIES) {
    var truncated: Boolean = false
}

/** Hard cap on how deep a workspace scan descends. */
private const val MAX_WORKSPACE_DEPTH = 8

/** Hard cap on how many hits a workspace search reports. */
private const val MAX_SEARCH_HITS = 300
private const val MAX_WATCHED_DIRECTORIES = 512
private const val FILE_CHANGE_DEBOUNCE_MS = 250L

/** How many folders may be open at the same time. */
const val MAX_OPEN_WORKSPACES = 3

/** Upper bound of tabs reopened from a previous session, per workspace. */
internal const val MAX_RESTORED_TABS = 20

/** Delay between two sequential tab restores, so a big session opens gradually. */
internal const val TAB_RESTORE_DELAY_MS = 120L

internal fun normalizeNewPawnFileName(requestedName: String): String? {
    val name = requestedName.trim()
    if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
        return null
    }

    val extension = name.substringAfterLast('.', "").lowercase()
    val fileName = if (extension.isBlank()) "$name.pwn" else name
    val finalExtension = fileName.substringAfterLast('.', "").lowercase()
    val baseName = fileName.substringBeforeLast('.', "")
    return fileName.takeIf {
        baseName.isNotBlank() && finalExtension in setOf("pawn", "pwn", "p", "inc")
    }
}

internal fun normalizeNewFolderName(requestedName: String): String? {
    val name = requestedName.trim()
    return name.takeIf {
        it.isNotBlank() &&
            it != "." &&
            it != ".." &&
            !it.contains('/') &&
            !it.contains('\\') &&
            it.none { character -> character.isISOControl() }
    }
}

/** A file or folder of an opened workspace, with its children already scanned. */
data class WorkspaceNode(
    val file: File,
    val isDirectory: Boolean,
    val children: List<WorkspaceNode> = emptyList()
) {
    /** Path used as the collapse key, so two folders never share an identity. */
    val key: String get() = file.absolutePath
}

/**
 * A folder opened in the PawnMC editor, with its own tree, editors and search.
 *
 * [onDocumentsChanged] is the session's observation hook: it is called whenever the
 * open-editor list of this folder changes, so the host can invalidate whatever it derived
 * from "does this workspace own the active file?".
 */
class Workspace(
    val root: File,
    private val onDocumentsChanged: () -> Unit = {}
) {

    val name: String = root.name

    /** Scanned folder tree, folders first then files, both sorted case-insensitively. */
    var tree by mutableStateOf<List<WorkspaceNode>>(emptyList())
        private set
    var isScanTruncated by mutableStateOf(false)
        private set

    /** Folders whose children are hidden. */
    var collapsedPaths by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Files opened in editors, most recently opened last. */
    val documents = mutableStateListOf<OpenDocument>()

    /** Absolute path of the file shown in the editor for this workspace. */
    var activePath by mutableStateOf<String?>(null)
        private set

    val activeDocument: OpenDocument?
        get() = documents.firstOrNull { it.file.absolutePath == activePath }

    val hasDirtyDocuments: Boolean get() = documents.any { it.isDirty }

    fun isExpanded(node: WorkspaceNode): Boolean = node.key !in collapsedPaths

    fun toggleFolder(node: WorkspaceNode) {
        if (!node.isDirectory) return
        collapsedPaths = if (isExpanded(node)) collapsedPaths + node.key else collapsedPaths - node.key
    }

    fun applyTree(scanned: List<WorkspaceNode>, truncated: Boolean = false) {
        tree = scanned
        isScanTruncated = truncated
    }

    fun addDocument(document: OpenDocument) {
        documents.add(document)
        // The editor decides whether the active file is a standalone buffer by looking
        // whether any workspace owns it; nothing reached the snapshot system from this
        // list before, so the canvas kept showing the previous file.
        onDocumentsChanged()
    }

    fun removeDocument(path: String) {
        val index = documents.indexOfFirst { it.file.absolutePath == path }
        if (index >= 0) documents.removeAt(index)
        if (activePath == path) {
            activePath = documents.getOrNull(documents.lastIndex)?.file?.absolutePath
        }
        if (index >= 0) onDocumentsChanged()
    }

    fun activate(path: String?) {
        if (path == null || documents.any { it.file.absolutePath == path }) {
            activePath = path
        }
    }

    fun documentFor(file: File): OpenDocument? =
        documents.firstOrNull { it.file.absolutePath == file.absolutePath }

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        isSearching = false
    }

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<WorkspaceSearchHit>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    /** Marks a new query as running; the results arrive through [applySearch]. */
    fun beginSearch(query: String) {
        searchQuery = query
        isSearching = true
    }

    /** Stores the hits of a finished search, unless a newer query replaced it. */
    fun applySearch(query: String, hits: List<WorkspaceSearchHit>) {
        if (searchQuery == query) {
            searchResults = hits
            isSearching = false
        }
    }
}

/**
 * A file opened in an editor, with its buffer and save state.
 *
 * [displayName] is set when the buffer edits a staged copy of a document opened from
 * outside PawnMC: the tab then reads like the document the user tapped instead of the
 * `pawnmc-open-<nano>.<ext>` staging file in the cache.
 */
class OpenDocument(val file: File, displayName: String? = null) {
    var content by mutableStateOf("")
        private set

    private var savedContent by mutableStateOf("")

    /** False while the first read from disk is still in flight. */
    var isLoaded by mutableStateOf(false)
        private set

    /**
     * Name shown in the tab. Snapshot state, so a name that arrives after the document
     * was registered (the workspace also restores its own tabs, and only one of the two
     * knows the original document name) renames the open tab instead of being dropped.
     */
    var displayName by mutableStateOf(displayName)
        private set

    val isDirty: Boolean get() = content != savedContent

    val name: String get() = displayName ?: file.name

    fun updateDisplayName(name: String?) {
        val clean = name?.takeIf { it.isNotBlank() }
        if (clean != null && clean != displayName) displayName = clean
    }

    fun updateContent(text: String) {
        content = text
    }

    fun markSaved(contentWritten: String = content) {
        savedContent = contentWritten
    }

    suspend fun loadFromDisk() {
        content = withContext(Dispatchers.IO) { file.readText() }
        markSaved()
        isLoaded = true
    }
}

/** One match of the workspace-wide search. */
data class WorkspaceSearchHit(
    val file: File,
    val line: Int,
    val preview: String
)

/**
 * Holds the workspaces opened in the PawnMC editor and the search across them.
 */
class WorkspaceSession(context: Context? = null) : ViewModel() {

    private val config: ContentConfig? =
        context?.let { ContentConfig.getInstanceOrNull() ?: ContentConfig.getInstance(it) }

    private val workspaces = mutableStateListOf<Workspace>()
    private val scanning = mutableStateListOf<Workspace>()
    private val fileObservers = mutableMapOf<Workspace, MutableMap<String, FileObserver>>()
    private val refreshJobs = mutableMapOf<Workspace, Job>()
    private val documentLoads = mutableMapOf<String, Job>()

    /** Workspace shown in the panel and used by search/replace. */
    var activeWorkspace by mutableStateOf<Workspace?>(null)
        private set

    val openWorkspaces: List<Workspace> get() = workspaces

    val isWorkspaceOpen: Boolean get() = workspaces.isNotEmpty()

    val isAtWorkspaceLimit: Boolean get() = workspaces.size >= MAX_OPEN_WORKSPACES

    /** Files opened in the active workspace, kept for convenience. */
    val documents: List<OpenDocument> get() = activeWorkspace?.documents.orEmpty()

    val activeDocument: OpenDocument? get() = activeWorkspace?.activeDocument

    val hasDirtyDocuments: Boolean get() = workspaces.any { it.hasDirtyDocuments }

    /** Every dirty document across all open workspaces (for save-all prompts). */
    val allDirtyDocuments: List<OpenDocument>
        get() = workspaces.flatMap { it.documents.filter { it.isDirty } }

    /** Dirty documents belonging to a single workspace. */
    fun dirtyDocumentsOf(workspace: Workspace): List<OpenDocument> =
        workspace.documents.filter { it.isDirty }

    val activePath: String? get() = activeWorkspace?.activePath

    val searchQuery: String get() = activeWorkspace?.searchQuery.orEmpty()

    val searchResults: List<WorkspaceSearchHit> get() = activeWorkspace?.searchResults.orEmpty()

    val isSearching: Boolean get() = activeWorkspace?.isSearching == true

    var statusMessage by mutableStateOf<String?>(null)
        private set

    /** Tab-restore queue, drained one document at a time.
     *
     * Declared before the init block below: the block runs in declaration
     * order, and `restoreRememberedTabs` reads this queue on the very first
     * launch that has remembered tabs. Declaring it after `init` left the
     * property uninitialised at that point and crashed with a
     * NullPointerException on reopen.
     */
    private val pendingTabLoads = ArrayDeque<Pair<Workspace, File>>()

    init {
        // Both restores are synchronous in what they touch: the workspace list is filled
        // here (only the folder scans are handed to the IO dispatcher) and the tabs are
        // queued, so `isWorkspaceOpen` is already correct for the host's very first read.
        //
        // Defensive: `config` is nullable (built without a context). Both restore
        // methods guard with `config ?: return` before touching it - never call
        // methods on `config` or on any interface-typed field directly in this init
        // block without a null check, as an NPE during construction crashes the first
        // frame of composition.
        restoreRememberedWorkspaces()
        restoreRememberedTabs()
    }

    /**
     * Re-opens the documents each remembered workspace had open, one at a time.
     *
     * The queue is drained sequentially: opening every tab at once fires several
     * concurrent disk reads and file watchers, which on slow storage raced with
     * each other and occasionally left a tab blank. A serial queue keeps the same
     * result without the burst.
     */
    private fun restoreRememberedTabs() {
        val store = config ?: return
        // After a reinstall the folder list itself only lives in the JSON mirror,
        // and restoreRememberedWorkspaces refills `n_workspace_folders` from it;
        // read the store here too so the tabs are not skipped when the prefs copy
        // arrived later or through a different path.
        val folders = store.n_workspace_folders.ifEmpty {
            PathStore.readPaths(store.applicationContextHolder(), "workspace")
                .let(PathStore::restoreExisting)
        }
        if (folders.isEmpty()) return
        val tabsByFolder = store.n_workspace_open_tabs.associateBy { group -> group.first() }
        val activeByFolder = store.n_workspace_active_tabs
            .associate { group -> group[0] to group[1] }

        workspaces.forEach { workspace ->
            val folderKey = workspace.root.absolutePath
            val tabs = tabsByFolder[folderKey]
                ?.drop(1)
                ?.filter { File(it).isFile }
                .orEmpty()
            tabs.take(MAX_RESTORED_TABS).forEach { path ->
                pendingTabLoads.add(workspace to File(path))
            }
            activeByFolder[folderKey]?.let { path ->
                if (File(path).isFile) workspace.activate(path)
            }
        }
        drainDocumentLoadQueue()
    }

    private fun drainDocumentLoadQueue() {
        if (pendingTabLoads.isEmpty()) return
        val (workspace, file) = pendingTabLoads.removeFirstOrNull() ?: return
        // Only reopen what still exists; a file deleted while the app was closed
        // must not block the rest of the queue.
        if (file.isFile) openFile(file, workspace)
        if (pendingTabLoads.isNotEmpty()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                { drainDocumentLoadQueue() },
                TAB_RESTORE_DELAY_MS
            )
        } else {
            persistOpenTabs()
        }
    }

    /** Persists the open tabs and the active document of every workspace. */
    fun persistOpenTabs() {
        val store = config ?: return
        store.n_workspace_open_tabs = workspaces.map { workspace ->
            listOf(workspace.root.absolutePath) +
                workspace.documents.map { it.file.absolutePath }
        }
        store.n_workspace_active_tabs = workspaces.mapNotNull { workspace ->
            workspace.activePath?.let { path ->
                listOf(workspace.root.absolutePath, path)
            }
        }
        // Also mirror the folder list into the reusable JSON store so a reinstall
        // can pick the same workspaces back up.
        val contextForStore = store.applicationContextHolder()
        PathStore.writePaths(contextForStore, "workspace", store.n_workspace_folders)
    }

    /**
     * Re-opens the workspace folders stored by a previous run.
     *
     * `workspace/workspace.json` is the store that survives an uninstall, so it is read
     * first and the prefs copy only fills in afterwards. When the panel is empty but the
     * JSON still carries full paths, every stored folder is opened again; the folders and
     * their tabs are restored, never the one-shot blank.pwn fallback.
     */
    private fun restoreRememberedWorkspaces() {
        val store = config ?: return
        val storeContext = store.applicationContextHolder()
        val fromJson = PathStore.readPaths(storeContext, "workspace")
            .let(PathStore::restoreExisting)
            .filterNot(::isStorageFolder)
        val stored = when {
            // No prefs copy left (a reinstall wipes SharedPreferences but not Documents):
            // the JSON is the authority and is written back into the prefs below.
            store.n_workspace_folders.isEmpty() -> fromJson
            // A folder manually removed from the panel is gone from the prefs; JSON entries
            // that still exist are only revived when the panel has nothing at all to open.
            else -> store.n_workspace_folders.filter { path ->
                runCatching { File(path).isDirectory }.getOrDefault(false)
            }.filterNot(::isStorageFolder).ifEmpty { fromJson }
        }
        if (stored.isEmpty()) {
            // Nothing left on disk and nothing remembered: drop the stale JSON too.
            return
        }

        val requested = stored.distinct()
        val surviving = requested.filter { path ->
            // canonicalPath can throw on freshly mounted folders; a plain exists
            // probe keeps a valid folder from being pruned as "missing".
            runCatching { File(path).isDirectory }.getOrDefault(false)
        }.filterNot(::isStorageFolder)
        // The JSON is rewritten either way: on a fresh install this is what promotes the
        // recovered paths into the prefs-backed list again.
        if (surviving != store.n_workspace_folders) store.n_workspace_folders = surviving
        if (surviving.size != requested.size) {
            PathStore.writePaths(storeContext, "workspace", surviving)
        }
        if (surviving.isEmpty()) return

        val restored = surviving.take(MAX_OPEN_WORKSPACES)
            .mapNotNull { path -> newWorkspaceFor(File(path), persist = false) }
        if (restored.isNotEmpty()) {
            activeWorkspace = restored.first()
        }
        // The restore is what closes the first "is a workspace open?" question, and it
        // can also revive the very file the host was showing as a standalone buffer.
        notifyObservationChanged()
    }

    /** Opens [dir] as a workspace and brings it to the front. */
    fun openWorkspace(dir: File) {
        if (!dir.isDirectory) {
            statusMessage = "Not a folder: ${dir.name}"
            return
        }
        if (isStorageFolder(dir.absolutePath)) {
            // PawnMC's own data folder (the one holding workspace.json) is not a project.
            statusMessage = "That folder is PawnMC's own data, pick a project folder instead"
            return
        }
        val existing = workspaces.firstOrNull { it.root.absolutePath == dir.absolutePath }
        if (existing != null) {
            activateWorkspace(existing)
            return
        }
        if (isAtWorkspaceLimit) {
            statusMessage = "You can open up to $MAX_OPEN_WORKSPACES workspaces, close one first"
            return
        }
        val workspace = newWorkspaceFor(dir) ?: return
        activateWorkspace(workspace)
        persistOpenTabs()
        statusMessage = "Workspace opened: ${dir.name}"
    }

    /**
     * Called whenever the set of open workspaces, the workspace in front, or the open
     * editors of any workspace may have changed.
     *
     * The host uses it to invalidate its derived "is the active file standalone?" state;
     * the workspace lists themselves are plain scans that Compose does not observe.
     */
    var onWorkspaceObservationInvalidated: () -> Unit = {}

    private fun notifyObservationChanged() {
        onWorkspaceObservationInvalidated()
    }

    /** Brings [workspace] to the front; the editor follows its active file. */
    fun activateWorkspace(workspace: Workspace) {
        if (workspaces.any { it.root.absolutePath == workspace.root.absolutePath }) {
            activeWorkspace = workspace
            notifyObservationChanged()
        }
    }

    /** Closes one workspace and its opened editors, switching to another one. */
    fun closeWorkspace(workspace: Workspace, saveDirty: Boolean = false) {
        if (saveDirty) {
            viewModelScope.launch { saveAll(workspace) }
        }
        // SnapshotStateList.remove returns a Boolean, so the removed workspace has to
        // be captured before it leaves the list.
        val closing = workspaces.firstOrNull { it.root.absolutePath == workspace.root.absolutePath }
        stopWatchingWorkspace(workspace)
        workspaces.remove(workspace)
        scanning.remove(workspace)
        persistWorkspaceFolders()
        val closingPath = closing?.root?.absolutePath
        if (activeWorkspace != null && activeWorkspace?.root?.absolutePath == closingPath) {
            activeWorkspace = workspaces.lastOrNull()
        }
        notifyObservationChanged()
    }

    /** Closes the workspace folder in front; false when none was open. */
    fun closeActiveWorkspace(saveDirty: Boolean = false): Boolean {
        val closing = activeWorkspace ?: return false
        closeWorkspace(closing, saveDirty = saveDirty)
        return true
    }

    /** Leaves every workspace; the caller decides what to show instead. */
    fun closeAllWorkspaces(saveDirty: Boolean = false) {
        if (saveDirty) {
            viewModelScope.launch { saveAll() }
        }
        workspaces.toList().forEach(::stopWatchingWorkspace)
        workspaces.clear()
        scanning.clear()
        activeWorkspace = null
        // The store is cleared too, otherwise the next launch would silently bring
        // every folder back and make this menu item look like it did nothing.
        config?.clearWorkspaceFolders()
        notifyObservationChanged()
    }

    /** Registers a file opened from outside the workspace panel as a tab. */
    fun registerExternalFile(file: File): Boolean = openExternalFile(file)

    /**
     * Creates and scans a workspace for [dir], or returns null when the limit is hit.
     *
     * [rememberDayFolder] is false for the folder an externally opened file happens to
     * live in: that folder is convenient for the current session but was never chosen by
     * the user, so it must not end up in the remembered workspace list.
     */
    private fun newWorkspaceFor(
        dir: File,
        persist: Boolean = true,
        rememberDayFolder: Boolean = true
    ): Workspace? {
        if (isAtWorkspaceLimit) return null
        // The hook is wired before the folder joins the list: the very next recomposition
        // must already see a workspace that reports its document changes.
        val created = Workspace(dir, onDocumentsChanged = ::notifyObservationChanged)
        workspaces.add(created)
        scan(created)
        if (persist && rememberDayFolder) persistWorkspaceFolders()
        notifyObservationChanged()
        return created
    }

    /** Writes the open workspace folders to the config so they survive the process. */
    private fun persistWorkspaceFolders() {
        config?.n_workspace_folders = workspaces
            .map { it.root.absolutePath }
            .filterNot(::isStorageFolder)
            .distinct()
    }

    /**
     * True for the app's own bookkeeping folders, which must never be opened as a
     * workspace.
     *
     * `Documents/PawnMC/workspace` is where `workspace.json` lives and where the bundled
     * `blank.pwn` starter is written. Remembering it - which the old restore did whenever
     * the stored path list was empty - made the panel open that folder and list
     * `workspace.json` and `blank.pwn` instead of the user's real project folders.
     *
     * The comparison is by canonical path, with a plain-name fallback for the case where a
     * path cannot be canonicalised yet (a just-mounted volume).
     */
    private fun isStorageFolder(path: String): Boolean {
        if (path.isBlank()) return false
        return runCatching {
            val store = config ?: return@runCatching false
            val root = com.rvdjv.pawnmc.apps_data.data_config.AppStorage.dataRoot(
                store.applicationContextHolder()
            )
            val target = File(path)
            val storeFolder = File(root, "workspace")
            val sameFolder = target.canonicalPath == storeFolder.canonicalPath ||
                target.absolutePath.trimEnd('/') == storeFolder.absolutePath.trimEnd('/')
            sameFolder || listOf("workspace", "filemanager", "logs").any { name ->
                runCatching { target.canonicalPath }.getOrNull() ==
                    runCatching { File(root, name).canonicalPath }.getOrNull()
            }
        }.getOrDefault(false)
    }

    /** Closes the workspace folder in front; false when none was open. */
    fun closeActiveWorkspace(): Boolean {
        val closing = activeWorkspace ?: return false
        closeWorkspace(closing)
        return true
    }

    /** Leaves every workspace; the caller decides what to show instead. */
    fun closeAllWorkspaces() {
        workspaces.toList().forEach(::stopWatchingWorkspace)
        workspaces.clear()
        scanning.clear()
        activeWorkspace = null
        // The store is cleared too, otherwise the next launch would silently bring
        // every folder back and make this menu item look like it did nothing.
        config?.clearWorkspaceFolders()
        notifyObservationChanged()
    }

    /** Re-scans a workspace folder; called after files are replaced on disk. */
    fun refreshTree(workspace: Workspace? = activeWorkspace) {
        val target = workspace ?: return
        if (target in scanning) {
            scheduleWorkspaceRefresh(target)
        } else {
            scan(target)
        }
    }

    private fun scan(workspace: Workspace) {
        if (workspace in scanning) return
        scanning.add(workspace)
        viewModelScope.launch {
            try {
                val budget = WorkspaceScanBudget()
                val scanned = withContext(Dispatchers.IO) {
                    scanDirectory(workspace.root, 0, budget)
                }
                if (workspace in workspaces) {
                    workspace.applyTree(scanned, budget.truncated)
                    updateFileObservers(workspace, scanned)
                }
            } finally {
                scanning.remove(workspace)
            }
        }
    }

    private fun updateFileObservers(workspace: Workspace, tree: List<WorkspaceNode>) {
        val directories = mutableListOf(workspace.root)
        fun collect(nodes: List<WorkspaceNode>) {
            if (directories.size >= MAX_WATCHED_DIRECTORIES) return
            nodes.forEach { node ->
                if (node.isDirectory && directories.size < MAX_WATCHED_DIRECTORIES) {
                    directories += node.file
                    collect(node.children)
                }
            }
        }
        collect(tree)

        val observers = fileObservers.getOrPut(workspace) { mutableMapOf() }
        val wantedPaths = directories.mapTo(mutableSetOf()) { it.absolutePath }
        observers.keys.filterNot { it in wantedPaths }.toList().forEach { path ->
            observers.remove(path)?.let { runCatching { it.stopWatching() } }
        }

        val watchedEvents = FileObserver.CREATE or FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or FileObserver.DELETE or
            FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
        directories.forEach { directory ->
            val path = directory.absolutePath
            if (path in observers) return@forEach

            // Watch registration can be refused (an inotify watch limit, a provider
            // that does not support watching, a unit test without the Android
            // runtime). A missing watcher must never cost the tree itself, so the
            // failure is swallowed and the folder simply stops auto-refreshing.
            val observer = runCatching {
                createObserver(directory, path, watchedEvents, workspace)
            }.getOrNull() ?: return@forEach

            observer.startWatching()
            observers[path] = observer
        }
    }

    /** Builds the watcher for one workspace folder; API 29+ takes a File. */
    @Suppress("DEPRECATION")
    private fun createObserver(
        directory: File,
        path: String,
        watchedEvents: Int,
        workspace: Workspace,
    ): FileObserver {
        val onEvent = { event: Int ->
            if (event and watchedEvents != 0) {
                viewModelScope.launch { scheduleWorkspaceRefresh(workspace) }
            }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, watchedEvents) {
                override fun onEvent(event: Int, path: String?) = onEvent(event)
            }
        } else {
            object : FileObserver(path, watchedEvents) {
                override fun onEvent(event: Int, path: String?) = onEvent(event)
            }
        }
    }

    private fun scheduleWorkspaceRefresh(workspace: Workspace) {
        if (workspace !in workspaces) return
        refreshJobs[workspace]?.cancel()
        refreshJobs[workspace] = viewModelScope.launch {
            delay(FILE_CHANGE_DEBOUNCE_MS)
            while (workspace in scanning) delay(50L)
            if (workspace in workspaces) scan(workspace)
            refreshJobs.remove(workspace)
        }
    }

    private fun stopWatchingWorkspace(workspace: Workspace) {
        refreshJobs.remove(workspace)?.cancel()
        fileObservers.remove(workspace)?.values?.forEach { runCatching { it.stopWatching() } }
    }

    override fun onCleared() {
        workspaces.toList().forEach(::stopWatchingWorkspace)
        super.onCleared()
    }

    // ------------------------------------------------------------------
    // Files
    // ------------------------------------------------------------------

    /** Registers [file] as open and makes it the active editor. */
    fun openFile(file: File, workspace: Workspace? = activeWorkspace, displayName: String? = null) {
        val target = workspace ?: return
        val existing = target.documentFor(file)
        if (existing != null) {
            // The workspace restores its own tabs without knowing the document name; the
            // host does. Renaming the open tab is enough, no second tab is created.
            existing.updateDisplayName(displayName)
            target.activate(existing.file.absolutePath)
            if (!existing.isLoaded) loadDocument(existing, target)
            return
        }
        val opened = OpenDocument(file, displayName)
        target.addDocument(opened)
        target.activate(file.absolutePath)
        loadDocument(opened, target)
    }

    /**
     * Opens a file picked outside the workspace panel.
     *
     * A plain single-file buffer never showed up under "Opened Editors", so opening a
     * `.json`/`.txt` from another app left the panel looking empty. The file is therefore
     * always given a workspace - its own folder when no workspace owns it yet - and the
     * tab carries [displayName] when the buffer edits a staged copy.
     *
     * Idempotent on purpose: the host announces its current file again whenever the main
     * screen comes back, so the same file must be brought to the front, never opened a
     * second time (that is what piled up `pawnmc-open-<nano>` tabs).
     */
    fun openExternalFile(file: File, displayName: String? = null): Boolean {
        if (!file.isFile) return false
        val dir = file.parentFile ?: return false
        val existing = workspaces.firstOrNull { it.documentFor(file) != null }
        if (existing != null) {
            if (activeWorkspace?.root?.absolutePath != existing.root.absolutePath) {
                activeWorkspace = existing
            }
            openFile(file, existing, displayName)
            notifyObservationChanged()
            return true
        }
        // Never turn PawnMC's own data folder into a workspace: doing that is what put
        // `workspace.json` and `blank.pwn` in the panel instead of the user's folders.
        if (isStorageFolder(dir.absolutePath)) return false
        // A folder inside an already open workspace (its tree is scanned, so a document
        // may not exist yet) keeps that workspace rather than spending another slot.
        val owner = workspaces.firstOrNull { it.root.absolutePath == dir.absolutePath }
            ?: workspaces.firstOrNull { isWithinWorkspace(it.root, file) }
        val target = owner ?: newWorkspaceFor(dir, rememberDayFolder = false) ?: run {
            statusMessage = "Close a workspace before opening another file"
            return false
        }
        if (activeWorkspace?.root?.absolutePath != target.root.absolutePath) {
            activeWorkspace = target
        }
        openFile(file, target, displayName)
        // Only a folder the user already owned is remembered; the ad-hoc folder created
        // for a one-off external file stays for this session and is not persisted.
        if (owner != null) persistWorkspaceFolders()
        notifyObservationChanged()
        return true
    }

    /** Creates a new Pawn source file in the active workspace and opens it. */
    suspend fun createNewPawnFile(
        requestedName: String,
        parentDirectory: File? = null
    ): File? {
        val target = activeWorkspace ?: run {
            statusMessage = "Open a workspace before creating a file"
            return null
        }

        val fileName = normalizeNewPawnFileName(requestedName)
        if (fileName == null) {
            statusMessage = "Enter a filename with .pawn, .pwn, .p, or .inc extension"
            return null
        }

        val directory = parentDirectory ?: target.root
        if (!directory.isDirectory || !isWithinWorkspace(target.root, directory)) {
            statusMessage = "Choose a folder inside ${target.name}"
            return null
        }
        val file = File(directory, fileName)
        return try {
            val created = withContext(Dispatchers.IO) { file.createNewFile() }
            if (!created) {
                statusMessage = "File already exists: $fileName"
                return null
            }

            openFile(file, target)
            refreshTree(target)
            statusMessage = "Created ${file.name}"
            file
        } catch (e: Exception) {
            statusMessage = "Could not create $fileName: ${e.localizedMessage}"
            null
        }
    }

    /** Creates a folder inside [parentDirectory] (or the workspace root). */
    suspend fun createNewFolder(
        requestedName: String,
        parentDirectory: File? = null
    ): File? {
        val target = activeWorkspace ?: run {
            statusMessage = "Open a workspace before creating a folder"
            return null
        }
        val folderName = normalizeNewFolderName(requestedName)
        if (folderName == null) {
            statusMessage = "Enter a valid folder name"
            return null
        }
        val directory = parentDirectory ?: target.root
        if (!directory.isDirectory || !isWithinWorkspace(target.root, directory)) {
            statusMessage = "Choose a folder inside ${target.name}"
            return null
        }

        val folder = File(directory, folderName)
        return try {
            val created = withContext(Dispatchers.IO) { folder.mkdir() }
            if (!created) {
                statusMessage = if (folder.exists()) {
                    "Folder already exists: $folderName"
                } else {
                    "Could not create $folderName"
                }
                return null
            }
            refreshTree(target)
            statusMessage = "Created folder ${folder.name}"
            folder
        } catch (error: Exception) {
            statusMessage = "Could not create folder $folderName: ${error.localizedMessage}"
            null
        }
    }

    private fun isWithinWorkspace(root: File, child: File): Boolean {
        val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return false
        val childPath = runCatching { child.canonicalPath }.getOrNull() ?: return false
        return childPath == rootPath || childPath.startsWith(rootPath + File.separator)
    }

    /** Reads a registered document once and drops it again when the read fails. */
    private fun loadDocument(document: OpenDocument, workspace: Workspace) {
        val path = document.file.absolutePath
        if (documentLoads[path]?.isActive == true) return
        documentLoads[path] = viewModelScope.launch {
            try {
                document.loadFromDisk()
            } catch (e: Exception) {
                workspace.removeDocument(path)
                statusMessage = "Failed to open ${document.name}: ${e.localizedMessage}"
            } finally {
                documentLoads.remove(path)
            }
        }
    }

    /** Closes the workspaces whose folder no longer exists on disk. */
    fun closeMissingWorkspaces(): Int {
        val missing = workspaces.filter { !it.root.isDirectory }
        missing.forEach { workspace ->
            stopWatchingWorkspace(workspace)
            workspaces.remove(workspace)
            scanning.remove(workspace)
        }
        if (activeWorkspace != null && activeWorkspace !in workspaces) {
            activeWorkspace = workspaces.lastOrNull()
        }
        if (missing.isNotEmpty()) notifyObservationChanged()
        persistWorkspaceFolders()
        return missing.size
    }

    fun closeDocument(file: File, workspace: Workspace? = activeWorkspace) {
        workspace?.removeDocument(file.absolutePath)
    }

    fun setActive(file: File, workspace: Workspace? = activeWorkspace) {
        workspace?.activate(file.absolutePath)
    }

    fun updateContent(file: File, text: String, workspace: Workspace? = activeWorkspace) {
        workspace?.documentFor(file)?.updateContent(text)
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    /** Writes one document back to disk. */
    suspend fun saveDocument(document: OpenDocument): Boolean {
        val contentToWrite = document.content
        return try {
            withContext(Dispatchers.IO) {
                document.file.parentFile?.mkdirs()
                document.file.writeText(contentToWrite)
            }
            document.markSaved(contentToWrite)
            true
        } catch (e: Exception) {
            statusMessage = "Save failed for ${document.name}: ${e.localizedMessage}"
            false
        }
    }

    /** Saves every modified document of [workspace]; returns how many were written. */
    suspend fun saveAll(workspace: Workspace? = activeWorkspace): Int {
        val target = workspace ?: return 0
        val dirty = target.documents.filter { it.isDirty }
        if (dirty.isEmpty()) {
            statusMessage = "Nothing to save"
            return 0
        }
        val saved = dirty.count { saveDocument(it) }
        statusMessage = if (saved == dirty.size) {
            "Saved $saved file(s) in ${target.name}"
        } else {
            "Saved $saved of ${dirty.size} file(s) in ${target.name}"
        }
        return saved
    }

    // ------------------------------------------------------------------
    // Replace / search
    // ------------------------------------------------------------------

    /** Replaces [find] with [replace] inside [file] on disk; 1 when changed. */
    suspend fun replaceInFile(
        file: File,
        find: String,
        replace: String,
        replaceAll: Boolean,
        workspace: Workspace? = activeWorkspace
    ): Int = withContext(Dispatchers.IO) {
        if (find.isEmpty() || !file.isFile) return@withContext 0
        try {
            val original = file.readText()
            val updated = if (replaceAll) {
                original.replace(find, replace)
            } else {
                val index = original.indexOf(find)
                if (index < 0) original
                else original.replaceRange(index, index + find.length, replace)
            }
            if (updated != original) {
                file.writeText(updated)
                workspace?.documentFor(file)?.let { document ->
                    document.updateContent(updated)
                    document.markSaved()
                }
                1
            } else {
                0
            }
        } catch (e: Exception) {
            statusMessage = "Replace failed in ${file.name}: ${e.localizedMessage}"
            0
        }
    }

    /** Replaces [find] with [replace] in every text file of a workspace. */
    suspend fun replaceInAllFiles(
        find: String,
        replace: String,
        workspace: Workspace? = activeWorkspace
    ): Int {
        val target = workspace ?: return 0
        if (find.isEmpty()) return 0
        var touched = 0
        withContext(Dispatchers.IO) {
            forEachWorkspaceFile(target.tree) { file ->
                try {
                    val original = file.readText()
                    val updated = original.replace(find, replace)
                    if (updated != original) {
                        file.writeText(updated)
                        touched++
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (touched > 0) {
            target.documents.forEach { document ->
                try {
                    document.updateContent(document.file.readText())
                    document.markSaved()
                } catch (_: Exception) {
                }
            }
        }
        statusMessage = if (touched > 0) "Replaced in $touched file(s)" else "No matches found"
        return touched
    }

    /** Searches [query] in every file of a workspace and stores the hits. */
    fun search(query: String, workspace: Workspace? = activeWorkspace) {
        val target = workspace ?: return
        target.beginSearch(query)
        if (query.isEmpty()) {
            target.clearSearch()
            return
        }
        viewModelScope.launch {
            val hits = withContext(Dispatchers.IO) {
                val found = mutableListOf<WorkspaceSearchHit>()
                forEachWorkspaceFile(target.tree) { file ->
                    if (found.size >= MAX_SEARCH_HITS) return@forEachWorkspaceFile
                    try {
                        file.readLines().forEachIndexed { index, line ->
                            if (found.size < MAX_SEARCH_HITS && line.contains(query)) {
                                found += WorkspaceSearchHit(file, index + 1, line.trim().take(120))
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                found
            }
            target.applySearch(query, hits)
            if (hits.isEmpty() && target.searchQuery == query) {
                statusMessage = "No matches in ${target.name}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage = null
    }

    private fun scanDirectory(
        dir: File,
        depth: Int,
        budget: WorkspaceScanBudget
    ): List<WorkspaceNode> {
        if (depth > MAX_WORKSPACE_DEPTH) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        val candidates = entries.asSequence()
            .filterNot { it.name.startsWith(".") }
            .take(budget.remaining.coerceAtLeast(0) + 1)
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .toList()
        if (candidates.size > budget.remaining) budget.truncated = true
        return candidates.mapNotNull { entry ->
            if (budget.remaining <= 0) {
                budget.truncated = true
                return@mapNotNull null
            }
            budget.remaining--
            if (entry.isDirectory) {
                WorkspaceNode(entry, true, scanDirectory(entry, depth + 1, budget))
            } else {
                if (!isProbablyText(entry)) null else WorkspaceNode(entry, false)
            }
        }
    }
}

/** Depth-first walk over the scanned workspace files. */
private fun forEachWorkspaceFile(nodes: List<WorkspaceNode>, action: (File) -> Unit) {
    for (node in nodes) {
        if (node.isDirectory) {
            forEachWorkspaceFile(node.children, action)
        } else {
            action(node.file)
        }
    }
}

/** Cheap extension/heuristic filter so binaries stay out of the tree and searches. */
private fun isProbablyText(file: File): Boolean {
    val name = file.name.lowercase()
    if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
        name.endsWith(".gif") || name.endsWith(".zip") || name.endsWith(".jar") ||
        name.endsWith(".so") || name.endsWith(".apk") || name.endsWith(".dex") ||
        name.endsWith(".bin") || name.endsWith(".aar") || name.endsWith(".pdf")
    ) {
        return false
    }
    return try {
        file.inputStream().use { input ->
            val buffer = ByteArray(512)
            var read = 0
            while (read < buffer.size) {
                val count = input.read(buffer, read, buffer.size - read)
                if (count < 0) break
                if (count == 0) continue
                read += count
            }
            (0 until read).none { buffer[it] == 0.toByte() }
        }
    } catch (_: Exception) {
        false
    }
}
