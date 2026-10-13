package com.pocketsamp.server.crash

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CrashManager {

    private const val TAG = "CrashManager"
    private const val DIR_NAME = "crash_reports"
    private const val DELIMITER = "===POCKETSAMP_CRASH_FIELD==="

    private val _crashesFlow = MutableStateFlow<List<CrashReport>>(emptyList())
    val crashesFlow: StateFlow<List<CrashReport>> = _crashesFlow.asStateFlow()

    private fun getCrashDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveCrash(context: Context, report: CrashReport): File {
        return try {
            val dir = getCrashDir(context)
            val fileName = "crash_${report.timestamp}_${report.id}.txt"
            val file = File(dir, fileName)

            val payload = buildString {
                append(report.id).append("\n").append(DELIMITER).append("\n")
                append(report.timestamp).append("\n").append(DELIMITER).append("\n")
                append(report.formattedDate).append("\n").append(DELIMITER).append("\n")
                append(report.exceptionClass).append("\n").append(DELIMITER).append("\n")
                append(report.message ?: "No message provided").append("\n").append(DELIMITER).append("\n")
                append(report.threadName).append("\n").append(DELIMITER).append("\n")
                append(report.deviceInfo).append("\n").append(DELIMITER).append("\n")
                append(report.stackTrace)
            }

            file.writeText(payload)
            refreshCrashes(context)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report to disk", e)
            File(context.cacheDir, "crash_fallback.txt")
        }
    }

    fun getAllCrashes(context: Context): List<CrashReport> {
        val dir = getCrashDir(context)
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: return emptyList()

        val reports = files.mapNotNull { file ->
            try {
                val content = file.readText()
                val parts = content.split("\n$DELIMITER\n")
                if (parts.size >= 8) {
                    CrashReport(
                        id = parts[0].trim(),
                        timestamp = parts[1].trim().toLongOrNull() ?: file.lastModified(),
                        formattedDate = parts[2].trim(),
                        exceptionClass = parts[3].trim(),
                        message = parts[4].trim().takeIf { it != "No message provided" },
                        threadName = parts[5].trim(),
                        deviceInfo = parts[6].trim(),
                        stackTrace = parts[7].trim()
                    )
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt crash file: ${file.name}", e)
                null
            }
        }

        return reports.sortedByDescending { it.timestamp }
    }

    fun refreshCrashes(context: Context) {
        _crashesFlow.value = getAllCrashes(context)
    }

    fun deleteCrash(context: Context, id: String): Boolean {
        val dir = getCrashDir(context)
        val files = dir.listFiles { f -> f.name.contains(id) } ?: return false
        var deleted = false
        files.forEach {
            if (it.delete()) deleted = true
        }
        refreshCrashes(context)
        return deleted
    }

    fun clearAllCrashes(context: Context) {
        val dir = getCrashDir(context)
        dir.listFiles()?.forEach { it.delete() }
        _crashesFlow.value = emptyList()
    }

    fun formatForClipboard(report: CrashReport): String {
        return buildString {
            appendLine("=========================================")
            appendLine("           POCKETSAMP CRASH REPORT       ")
            appendLine("=========================================")
            appendLine("Report ID    : ${report.id}")
            appendLine("Date & Time  : ${report.formattedDate}")
            appendLine("Thread       : ${report.threadName}")
            appendLine("Exception    : ${report.exceptionClass}")
            appendLine("Message      : ${report.message ?: "(none)"}")
            appendLine("-----------------------------------------")
            appendLine("DEVICE INFORMATION:")
            appendLine(report.deviceInfo)
            appendLine("-----------------------------------------")
            appendLine("FULL STACK TRACE:")
            appendLine(report.stackTrace)
            appendLine("=========================================")
        }
    }
}
