package com.rvdjv.pawnmc.`interface`.editor

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
 * Intent that opens the system folder picker, starting in the Downloads folder.
 *
 * [ActivityResultContracts.OpenDocumentTree][androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree]
 * cannot preset the starting location, so the intent is built by hand and
 * [DocumentsContract.EXTRA_INITIAL_URI] is added where the platform supports it.
 */
fun buildOpenFolderIntent(): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        downloadsInitialUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
    }
    return intent
}

/** Intent that opens the system file picker, starting in the Downloads folder. */
fun buildOpenFileIntent(): Intent {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        downloadsInitialUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
    }
    return intent
}

/** Downloads folder of the primary shared storage, where the picker starts. */
private fun downloadsInitialUri(): Uri? = Uri.parse(
    "content://com.android.externalstorage.documents/document/primary%3ADownload"
)

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

private fun resolveTree(resolver: ContentResolver, treeUri: Uri): File? {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
    if (!documentId.startsWith("primary:")) return null

    val segments = documentId.removePrefix("primary:").split(':').filter { it.isNotEmpty() }
    if (segments.isEmpty()) return null

    val relative = segments.joinToString(File.separator)
    val candidate = File("/storage/emulated/0/$relative")
    return candidate.takeIf { it.exists() }
}