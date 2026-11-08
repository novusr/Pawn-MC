package com.rvdjv.pawnmc.user_interface.editor.workspace

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
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
fun buildFolderPickerIntent(): Intent {
    val initialUri = downloadsInitialUri()
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

/** Resolves a picked document on primary storage to a file the workspace can edit in place. */
suspend fun resolveDocumentToFile(context: Context, uri: Uri): File? =
    withContext(Dispatchers.IO) {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return@withContext null
        val prefix = id.substringBefore(':', "").lowercase()
        val relative = id.substringAfter(':', "")
        if (prefix != "primary") return@withContext null
        if (relative.split('/').any { it == ".." || it == "." }) return@withContext null
        val root = File("/storage/emulated/0").canonicalFile
        // A bare `primary:` document id (the storage root itself) maps to the root;
        // previously this returned null and the pick read as "invalid path".
        if (relative.isBlank()) return@withContext root.takeIf { it.isDirectory }
        val file = File(root, relative).canonicalFile
        file.takeIf { it.isFile && it.path.startsWith(root.path + File.separator) }
    }

private fun resolveTree(resolver: ContentResolver, treeUri: Uri): File? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null

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

    val root = File("/storage/emulated/0").canonicalFile
    val candidate = when (prefix) {
        // Primary storage: root itself (empty relative) or a subfolder.
        "primary" -> if (segments.isEmpty()) root else File(root, segments.joinToString(File.separator))
        // `home:` / `raw:` style ids still live under primary storage; resolve
        // their path portion against the primary root instead of dropping the pick.
        "home" -> File(root, "Documents").takeIf { segments.isEmpty() }
            ?: File(root, segments.joinToString(File.separator))
        // Removable media: /storage/<volume>/<subpath>; a bare volume root is valid too.
        else -> {
            val volume = "/storage/$prefix"
            if (segments.isEmpty()) File(volume) else File(volume, segments.joinToString(File.separator))
        }
    }
    if (relative.split(File.separatorChar).any { it == ".." || it == "." }) return null
    val resolved = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
    if (!resolved.isDirectory || !resolved.canRead()) return null
    return resolved
}