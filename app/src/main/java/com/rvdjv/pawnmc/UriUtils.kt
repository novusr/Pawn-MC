package com.rvdjv.pawnmc

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object UriUtils {

    fun getPathFromDocumentUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }

        if (uri.scheme == "content") {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                resolveDocId(context, docId)?.let { return it }
            } catch (_: Exception) { }

            // Fallback: query ContentResolver for _data column
            try {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_data")
                        if (index >= 0) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        return null
    }

    fun getPathFromTreeUri(context: Context, uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            return resolveDocId(context, docId)
        } catch (_: Exception) { }
        return null
    }

    private fun resolveDocId(context: Context, docId: String): String? {
        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }

        if (docId.contains(":")) {
            val parts = docId.split(":")
            if (parts.size == 2) {
                val type = parts[0]
                val path = parts[1]

                when (type.lowercase()) {
                    "home" -> return "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                    "downloads" -> return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath}/$path"
                    "raw" -> return path
                }

                val externalDirs = context.getExternalFilesDirs(null)
                for (dir in externalDirs) {
                    if (dir != null) {
                        val root = dir.absolutePath.substringBefore("/Android")
                        val testPath = "$root/$path"
                        if (File(testPath).exists()) {
                            return testPath
                        }
                    }
                }
            }
        }

        return null
    }
}
