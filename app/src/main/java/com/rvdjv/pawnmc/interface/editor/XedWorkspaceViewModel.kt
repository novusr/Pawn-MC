package com.rvdjv.pawnmc.`interface`.editor

import android.content.Context
import android.os.Build
import android.os.FileObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.config.CompilerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Hard cap on how many entries a workspace scan materialises. */
private const val MAX_WORKSPACE_ENTRIES = 4000

/** Hard cap on how deep a workspace scan descends. */
private const val MAX_WORKSPACE_DEPTH = 8

/** Hard cap on how many hits a workspace search reports. */
private const val MAX_SEARCH_HITS = 300
private const val MAX_WATCHED_DIRECTORIES = 512
private const val FILE_CHANGE_DEBOUNCE_MS = 250L

/** How many folders may be open at the same time. */
const val MAX_OPEN_WORKSPACES = 3

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
 * A folder opened in Xed Editor.
 *
 * Every workspace keeps its own tree, its own opened editors and its own search
 * results, so up to [MAX_OPEN_WORKSPACES] folders can stay open side by side and
 * each one behaves like an independent Visual Studio solution explorer.
 */
class Workspace(val root: File) {

    val name: String = root.name

    /** Scanned folder tree, folders first then files, both sorted case-insensitively. */
    var tree by mutableStateOf<List<WorkspaceNode>>(emptyList())
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

    fun applyTree(scanned: List<WorkspaceNode>) {
        tree = scanned
    }

    fun addDocument(document: OpenDocument) {
        documents.add(document)
    }

    fun removeDocument(path: String) {
        val index = documents.indexOfFirst { it.file.absolutePath == path }
        if (index >= 0) documents.removeAt(index)
        if (activePath == path) {
            activePath = documents.getOrNull(documents.lastIndex)?.file?.absolutePath
        }
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
 * A file opened in an editor.
 *
 * The buffer is kept here (rather than only inside the editor widget) so several
 * files can stay open at once and `Save All` knows which ones are modified.
 */
class OpenDocument(val file: File) {
    var content by mutableStateOf("")
        private set

    private var savedContent by mutableStateOf("")

    /** False while the first read from disk is still in flight. */
    var isLoaded by mutableStateOf(false)
        private set

    val isDirty: Boolean get() = content != savedContent

    val name: String get() = file.name

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
 * Holds the workspaces opened in Xed Editor: up to [MAX_OPEN_WORKSPACES] folders
 * with their trees and opened editors, plus the replace/search operations that
 * span the files of the workspace that is in front.
 *
 * When a [Context] is supplied the open workspace folders are written to
 * [CompilerConfig] and restored on the next launch, so a folder chosen in the Xed
 * editor is not lost when the app is closed. The main screen only ever picks a
 * single file and treats it as temporary; a workspace folder is a deliberate
 * choice, so it is remembered on its own.
 */
class XedWorkspaceViewModel(context: Context? = null) : ViewModel() {

    private val config: CompilerConfig? =
        context?.let { CompilerConfig.getInstanceOrNull() ?: CompilerConfig.getInstance(it) }

    private val workspaces = mutableStateListOf<Workspace>()
    private val scanning = mutableStateListOf<Workspace>()
    private val fileObservers = mutableMapOf<Workspace, MutableMap<String, FileObserver>>()
    private val refreshJobs = mutableMapOf<Workspace, Job>()

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

    val activePath: String? get() = activeWorkspace?.activePath

    val searchQuery: String get() = activeWorkspace?.searchQuery.orEmpty()

    val searchResults: List<WorkspaceSearchHit> get() = activeWorkspace?.searchResults.orEmpty()

    val isSearching: Boolean get() = activeWorkspace?.isSearching == true

    var statusMessage by mutableStateOf<String?>(null)
        private set

    init {
        restoreRememberedWorkspaces()
    }

    /**
     * Re-opens the workspace folders stored by a previous run.
     *
     * Paths that no longer point at a folder are dropped from the store as well, so
     * an SD card that is absent right now does not leave a permanently broken entry
     * behind. The first folder in the stored order comes back in front, matching
     * the order they were opened in.
     */
    private fun restoreRememberedWorkspaces() {
        val store = config ?: return
        val stored = store.n_xed_workspace_folders
        if (stored.isEmpty()) return

        // Duplicates are dropped but the order is kept: the first folder has to come
        // back in front, exactly as it was in front when the app was left.
        val requested = stored.distinct()
        val surviving = requested.filter { File(it).isDirectory }
        if (surviving.size != requested.size) {
            store.n_xed_workspace_folders = surviving
        }
        if (surviving.isEmpty()) return

        val restored = surviving.mapNotNull { path -> newWorkspaceFor(File(path), persist = false) }
        if (restored.isNotEmpty()) {
            activeWorkspace = restored.first()
        }
    }

    /**
     * Opens [dir] as a workspace, closing the file that was open in single-file
     * mode and bringing the new workspace to the front.
     */
    fun openWorkspace(dir: File) {
        if (!dir.isDirectory) {
            statusMessage = "Not a folder: ${dir.name}"
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
        statusMessage = "Workspace opened: ${dir.name}"
    }

    /** Brings [workspace] to the front; the editor follows its active file. */
    fun activateWorkspace(workspace: Workspace) {
        if (workspaces.any { it.root.absolutePath == workspace.root.absolutePath }) {
            activeWorkspace = workspace
        }
    }

    /** Closes one workspace and its opened editors, switching to another one. */
    fun closeWorkspace(workspace: Workspace) {
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
    }

    /**
     * Registers a file the user opened outside the Xed workspace panel, i.e. from the
     * main screen or the file browser.
     *
     * The folder of the file becomes a workspace (so the tab strip, the explorer and
     * `Save All` all work from the first file), and the file itself is added as a tab.
     * Calling it twice for the same file is a no-op.
     */
    fun registerExternalFile(file: File) {
        if (!file.isFile) return
        val dir = file.parentFile ?: return

        val workspace = workspaces.firstOrNull { it.root.absolutePath == dir.absolutePath }
            ?: newWorkspaceFor(dir)
            ?: return

        if (activeWorkspace == null || activeWorkspace?.root?.absolutePath != workspace.root.absolutePath) {
            activeWorkspace = workspace
        }
        openFile(file, workspace)
    }

    /** Creates and scans a workspace for [dir], or returns null when the limit is hit. */
    private fun newWorkspaceFor(dir: File, persist: Boolean = true): Workspace? {
        if (isAtWorkspaceLimit) return null
        val created = Workspace(dir)
        workspaces.add(created)
        scan(created)
        if (persist) persistWorkspaceFolders()
        return created
    }

    /**
     * Writes the open workspace folders to the config so they survive the process.
     *
     * A `null` config means the view model was built without a context (unit tests),
     * and persistence is then simply skipped.
     */
    private fun persistWorkspaceFolders() {
        // The list order mirrors the open order so the front workspace is restored
        // in front, and duplicates are collapsed so closing and reopening a folder
        // cannot leave the same path stored twice.
        config?.n_xed_workspace_folders = workspaces.map { it.root.absolutePath }.distinct()
    }

    /**
     * Closes the workspace folder in front and drops its opened editors.
     *
     * The folder only disappears from the list, so a folder that is deleted or
     * unmounted outside the app can be cleared with the same call.
     *
     * @return true when a workspace was closed.
     */
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
        config?.clearXedWorkspaceFolders()
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
                val scanned = withContext(Dispatchers.IO) { scanDirectory(workspace.root, 0) }
                if (workspace in workspaces) {
                    workspace.applyTree(scanned)
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

    /**
     * Builds the watcher for one workspace folder.
     *
     * `FileObserver(String, Int)` is deprecated, but its replacement — the
     * `FileObserver(File, Int)` overload — only exists from API 29 while this app still
     * supports API 24. Using `File` unconditionally would turn a deprecation warning into
     * a crash on older devices, so the version decides which overload is constructed and
     * the deprecated one is suppressed for the API 24..28 branch only.
     *
     * A `File` cannot be handled directly by the constructor of the branch that takes a
     * path, which is why the path is passed alongside the directory.
     */
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

    /**
     * Registers [file] as open and makes it the active editor.
     *
     * The disk read is kicked off immediately, before the editor even asks for the
     * buffer, so tapping a file in the explorer fills the editor in one step instead
     * of waiting for a second round trip. A document that was already open is only
     * activated, never re-read, so its unsaved buffer survives the tap.
     */
    fun openFile(file: File, workspace: Workspace? = activeWorkspace) {
        val target = workspace ?: return
        val existing = target.documentFor(file)
        if (existing != null) {
            target.activate(existing.file.absolutePath)
            if (!existing.isLoaded) loadDocument(existing, target)
            return
        }
        val opened = OpenDocument(file)
        target.addDocument(opened)
        target.activate(file.absolutePath)
        loadDocument(opened, target)
    }

    /** Creates a new Pawn source file in the active workspace and opens it. */
    suspend fun createNewPawnFile(requestedName: String): File? {
        val target = activeWorkspace ?: run {
            statusMessage = "Open a workspace before creating a file"
            return null
        }

        val fileName = normalizeNewPawnFileName(requestedName)
        if (fileName == null) {
            statusMessage = "Enter a filename with .pawn, .pwn, .p, or .inc extension"
            return null
        }

        val file = File(target.root, fileName)
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

    /** Reads a registered document once and drops it again when the read fails. */
    private fun loadDocument(document: OpenDocument, workspace: Workspace) {
        viewModelScope.launch {
            try {
                document.loadFromDisk()
            } catch (e: Exception) {
                workspace.removeDocument(document.file.absolutePath)
                statusMessage = "Failed to open ${document.name}: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Drops every file of the workspace that is not a folder, and closes the
     * workspaces that are not folders on disk any more.
     *
     * Used by the editor when a workspace folder was closed or unmounted externally.
     */
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
        // A folder that vanished is not worth remembering, so the store follows.
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

    /**
     * Saves every modified document.
     *
     * @param workspace the workspace to save, the front one when omitted.
     */
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

    /**
     * Replaces [find] with [replace] inside [file], on disk.
     *
     * @return the number of replacements made.
     */
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

    /**
     * Replaces [find] with [replace] in every text file of a workspace.
     *
     * Open buffers are refreshed from disk afterwards so the editor never shows
     * content that the replace has already changed.
     *
     * @return the number of files that were modified.
     */
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
                    // Unreadable file: skip it instead of aborting the whole sweep.
                }
            }
        }
        if (touched > 0) {
            target.documents.forEach { document ->
                try {
                    document.updateContent(document.file.readText())
                    document.markSaved()
                } catch (_: Exception) {
                    // Keep the in-memory buffer if the file cannot be read back.
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
                        // Skip files that cannot be read as text.
                    }
                }
                found
            }
            target.applySearch(query, hits)
            if (hits.isEmpty() && target.searchQuery == query) {                statusMessage = "No matches in ${target.name}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage = null
    }

    private fun scanDirectory(dir: File, depth: Int): List<WorkspaceNode> {
        if (depth > MAX_WORKSPACE_DEPTH) return emptyList()
        val entries = dir.listFiles() ?: return emptyList()
        val budget = intArrayOf(MAX_WORKSPACE_ENTRIES)
        return entries
            .filterNot { it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .mapNotNull { entry ->
                if (budget[0] <= 0) return@mapNotNull null
                budget[0]--
                if (entry.isDirectory) {
                    WorkspaceNode(entry, true, scanDirectory(entry, depth + 1))
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
        val bytes = file.readBytes()
        val limit = minOf(bytes.size, 512)
        (0 until limit).none { bytes[it] == 0.toByte() }
    } catch (_: Exception) {
        false
    }
}
