package com.rvdjv.pawnmc.user_interface.editor.workspace

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Intents of the workspace folder picker.
 *
 * [ActivityResultContracts.OpenDocumentTree][androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree]
 * cannot preset the starting location, so both intents are built by hand and
 * [DocumentsContract.EXTRA_INITIAL_URI] is added where the platform supports it. The
 * helpers live in this package because picking a folder is what a workspace is created
 * from; the settings screen imports the same two functions for include paths, so there is
 * exactly one description of "where the picker opens".
 */

/** Intent that opens the system folder picker, starting in the Downloads folder. */
fun buildFolderPickerIntent(): Intent = buildFolderPickerIntentAt(null)

/**
 * Same picker as [buildFolderPickerIntent], but opened at [startDirectory] instead of
 * Downloads. Used where the location matters - an include path the user already owns
 * should open the picker right on that folder - and falls back to Downloads when the
 * folder cannot be expressed as a document uri.
 */
fun buildFolderPickerIntentAt(startDirectory: File?): Intent {
    val initialUri = initialUriFor(startDirectory) ?: downloadsInitialUri()
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
    }
    return intent
}

/** Intent that opens the system file picker, starting in the Downloads folder. */
fun buildFilePickerIntent(): Intent {
    val initialUri = downloadsInitialUri()
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
    }
    return intent
}

/**
 * Conversions between a folder on disk and the document uri the storage provider uses.
 *
 * Primary storage is `content://com.android.externalstorage.documents/document/primary%3A<path>`;
 * a removable volume keeps its volume id before the colon. Anything outside
 * `/storage/...` (the app-private directory, for one) has no document uri and therefore
 * no picker start, so the callers fall back to Downloads.
 */
@Suppress("unused")
fun documentUriFor(directory: File): Uri? = runCatching {
    val canonical = directory.canonicalFile
    val path = canonical.absolutePath.replace('\\', '/')
    val relative = when {
        path.startsWith("/storage/emulated/0/") -> "primary:" + path.removePrefix("/storage/emulated/0/")
        path == "/storage/emulated/0" -> "primary:"
        path.startsWith("/storage/") -> {
            val rest = path.removePrefix("/storage/")
            val volume = rest.substringBefore('/')
            val tail = rest.substringAfter('/', "")
            "$volume:$tail"
        }
        else -> return null
    }
    val encoded = Uri.encode(relative).replace("/", "%2F")
    Uri.parse("content://com.android.externalstorage.documents/document/$encoded")
}.getOrNull()

private fun initialUriFor(directory: File?): Uri? {
    if (directory == null) return null
    if (!directory.isDirectory) return null
    return documentUriFor(directory)
}

/**
 * Downloads folder of the primary shared storage, where the picker starts.
 *
 * Falling back to `content://com.android.externalstorage.documents/document/primary:`
 * (the storage root) when the Downloads document id is rejected by the provider —
 * a null initial uri made some pickers open in a random or empty place, which the
 * user reported as "the folder shows nothing".
 */
private fun downloadsInitialUri(): Uri? = runCatching {
    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
}.getOrNull() ?: runCatching {
    Uri.parse("content://com.android.externalstorage.documents/document/primary:")
}.getOrNull()

/**
 * Turns a picked tree URI into a real [File] on the primary shared storage.
 *
 * The picked tree id (`primary:Download/Calibrate`) is turned back into the
 * `/storage/emulated/0/...` path the file APIs understand. Returns `null` when
 * the tree lives outside the shared storage (a cloud provider, for example),
 * where no local path exists.
 */
suspend fun resolveTreeToFile(context: Context, treeUri: Uri): File? =
    withContext(Dispatchers.IO) { resolveTree(context.contentResolver, treeUri) }

/**
 * Resolves a picked document to a file the workspace can edit in place.
 *
 * The document id of a pick from the system picker is `primary:Download/x.pwn` on most
 * devices, but providers also hand out `emulated:`, `home:` and removable-volume ids.
 * Accepting only `primary:` was what made a perfectly ordinary file in `Downloads` fail
 * with "Choose a Pawn file on local primary storage".
 */
suspend fun resolveDocumentToFile(context: Context, uri: Uri): File? =
    withContext(Dispatchers.IO) { resolveDocument(uri) }

private fun resolveDocument(uri: Uri): File? {
    val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
    val prefix = id.substringBefore(':', "").lowercase()
    val relative = id.substringAfter(':', "")
    val segments = relative.split('/').filter { it.isNotEmpty() }
    if (segments.any { it == ".." || it == "." }) return null

    val root = when (prefix) {
        "primary", "emulated" -> primaryStorageRoot()
        "home" -> File(primaryStorageRoot(), "Documents")
        // Removable volumes (`1234-ABCD:...`): the volume folder itself.
        else -> File("/storage", prefix).takeIf { it.isDirectory } ?: return null
    }
    // A bare `primary:` document id (the storage root itself) maps to the root;
    // previously this returned null and the pick read as "invalid path".
    if (segments.isEmpty()) return root.takeIf { it.isDirectory }
    val file = File(root, segments.joinToString(File.separator))
    return file.takeIf { it.isFile }
}

private fun resolveTree(resolver: ContentResolver, treeUri: Uri): File? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        ?: return null

    // SAF sometimes hands back the *document* uri of a nested folder rather than
    // the tree root (a pick started from a child). When getTreeDocumentId looks
    // like a raw document id — its own uri still carries a document part — prefer
    // the deepest id the uri exposes; using the tree id blindly was what mapped
    // a `Downloads/xyz` pick onto `/storage/emulated` and showed the emulated/
    // skeleton in the panel instead of the picked folder.
    val nested = runCatching { DocumentsContract.getDocumentId(treeUri) }.getOrNull()
    val idForResolve = when {
        nested != null && nested != documentId -> nested
        else -> documentId
    }

    val prefix = idForResolve.substringBefore(':', "").lowercase()
    val relative = idForResolve.substringAfter(':', "")

    val segments = relative.split('/').filter { it.isNotEmpty() }
    if (segments.any { it == ".." || it == "." }) return null

    // `primary` is the emulated volume, and on most devices that *is*
    // `/storage/emulated/0`; resolving it through the enumerated roots keeps the
    // pick working when the build reports a different emulated id.
    val primary = primaryStorageRoot()
    val candidate = when (prefix) {
        "primary", "emulated" ->
            if (segments.isEmpty()) primary else File(primary, segments.joinToString(File.separator))
        // `home:` / `raw:` style ids still live under primary storage; resolve
        // their path portion against the primary root instead of dropping the pick.
        "home" -> if (segments.isEmpty()) {
            File(primary, "Documents")
        } else {
            File(primary, segments.joinToString(File.separator))
        }
        // Removable media: /storage/<volume>/<subpath>; a bare volume root is valid too.
        else -> {
            val volume = File("/storage", prefix)
            if (segments.isEmpty()) volume else File(volume, segments.joinToString(File.separator))
        }
    }
    val resolved = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
    if (!resolved.isDirectory || !resolved.canRead()) return null
    return resolved
}

/**
 * Root of the emulated primary volume.
 *
 * `Environment.getExternalStorageDirectory()` honours the platform's own mapping instead
 * of assuming `/storage/emulated/0`, which is not guaranteed on every device.
 */
internal fun primaryStorageRoot(): File =
    runCatching { android.os.Environment.getExternalStorageDirectory() }.getOrNull()
        ?: File("/storage/emulated/0")

/**
 * App-private copies of documents that have no local path.
 *
 * The system picker and third-party choosers (ZArchiver and friends) can return a
 * document that lives behind a provider whose id is not a plain `primary:` path - a
 * zip entry, a cloud provider, a scoped folder. Instead of rejecting the pick with
 * "choose a file on local primary storage", the content is streamed once into
 * `cache/open/` so the editor always has a real file to read and save.
 */
private fun stagedCopyFor(context: Context, uri: Uri, displayName: String?): File? {
    val resolver = context.contentResolver
    val name = displayName
        ?.takeIf { it.isNotBlank() }
        ?.substringAfterLast('/')
        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?: "document"
    val extension = name.substringAfterLast('.', "").lowercase()
    val safeName = if (extension.isBlank()) "$name.txt" else
        "${name.substringBeforeLast('.')}.${extension.take(8)}"
    return runCatching {
        val target = File(File(context.cacheDir, "open"), safeName)
        target.parentFile?.mkdirs()
        val input = resolver.openInputStream(uri) ?: error("Cannot open the selected document")
        input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
        target
    }.getOrNull()
}

/** True when the editor can show [path]: a plain text-like extension we support. */
internal fun isEditorOpenableExtension(path: String): Boolean =
    path.substringAfterLast('.', "").lowercase() in
        setOf("pawn", "pwn", "p", "inc", "txt", "log", "cfg", "conf", "ini", "html", "htm", "json", "md", "xml", "yml", "yaml", "sql")

/**
 * Resolves a picked document to something the editor can open.
 *
 * Returns the real [File] when the provider id maps back to shared storage (so edits land
 * where the user expects) and otherwise a staged copy in the cache. `null` only when the
 * document cannot be read at all. The display name always travels with the result because
 * a staged copy is named after the document, not after the user's file.
 */
suspend fun resolveOpenableDocument(
    context: Context,
    uri: Uri
): Pair<File, String?>? = withContext(Dispatchers.IO) {
    val displayName = documentDisplayName(context, uri)
    val direct = resolveDocument(uri)?.takeIf { it.isFile }
    if (direct != null) return@withContext direct to displayName
    val staged = stagedCopyFor(context, uri, displayName) ?: return@withContext null
    staged to displayName
}

/** Name the provider reports for [uri], falling back to its last path segment. */
fun documentDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver
        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}.getOrNull()

/**
 * Lets the user choose *which* folder picker opens.
 *
 * Browsing files goes through `Intent.createChooser`, so ZArchiver and friends show up
 * there. The folder pickers did not, and the reason is not ZArchiver: most file managers
 * only register `ACTION_OPEN_DOCUMENT` (files) and **not** `ACTION_OPEN_DOCUMENT_TREE`
 * (folders), so a folder chooser has nothing third-party to offer. The dialog makes that
 * explicit and always keeps the built-in browser one tap away.
 */
@Composable
fun FolderPickerSourceDialog(
    title: String = "Open folder with",
    onSystemPicker: () -> Unit,
    onBuiltInPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "The system picker hands the choice to another application " +
                        "(Open with). File managers that only handle files - ZArchiver, " +
                        "for example - will not appear for a folder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        onDismiss()
                        onSystemPicker()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("System picker (Open with)")
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        onBuiltInPicker()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Browse inside PawnMC")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
