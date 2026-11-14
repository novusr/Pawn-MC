package com.rvdjv.pawnmc.user_interface.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal
import com.rvdjv.pawnmc.apps_data.data_crash.CrashHandler

/**
 * The card the main screen shows once after a crash.
 *
 * A dump nobody notices is a dump nobody sends. The handler cannot display anything -
 * the process is dying - so the notice is deferred to the next launch: the card names the
 * crash, the module it came from and the file it wrote, and offers to copy the paths.
 *
 * Shown at most once per process: [dismiss] stores the id of the last acknowledged crash
 * in the journal folder's marker file, so restarting the app again does not bring the
 * same report back.
 */
@Composable
internal fun CrashReportCard(context: Context) {
    var pending by remember(context) { mutableStateOf(loadPendingCrash(context)) }
    val record = pending ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Previous session crashed",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        dismissCrash(context, record)
                        pending = null
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = record.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (record.dump != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${record.dump.name}  ·  ${record.dump.length() / 1024} KB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.folderPath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    val text = record.copyText()
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PawnMC crash path", text))
                    Toast.makeText(context, "Crash path copied", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy path", color = MaterialTheme.colorScheme.onErrorContainer)
                }
                TextButton(onClick = {
                    dismissCrash(context, record)
                    pending = null
                }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

/** One crash the user has not acknowledged yet. */
internal data class PendingCrash(
    val id: String,
    val summary: String,
    val dump: java.io.File?,
    val folderPath: String
) {
    fun copyText(): String = buildList {
        add("Crash $id")
        dump?.let { add(it.absolutePath) }
        add(folderPath)
    }.joinToString("\n")
}

/** Marker file recording the last crash the user saw, in the crash folder. */
private fun acknowledgedMarker(context: Context): java.io.File =
    runCatching { ActivityJournal.crashReportFolder(context) }
        .getOrNull()
        ?.let { java.io.File(it, ".acknowledged") }
        ?: java.io.File(context.filesDir, "crash_acknowledged")

/**
 * Picks the newest crash report not yet acknowledged.
 *
 * Reads the text reports written by [CrashHandler] rather than keeping a second index:
 * the report folder is the single source of truth, so a report deleted by the user (or by
 * the file manager) simply stops being offered.
 */
internal fun loadPendingCrash(context: Context): PendingCrash? {
    val lastSeen = runCatching { acknowledgedMarker(context).readText().trim() }.getOrDefault("")
    val reports = CrashHandler.listReportFiles()
    val newest = reports.firstOrNull() ?: return null
    val parsed = CrashHandler.parseTextReport(newest) ?: return null
    if (parsed.id == lastSeen) return null
    val dump = parsed.dumpPath?.let { path -> java.io.File(path) }?.takeIf { it.isFile }
        ?: CrashHandler.listDumpFiles().firstOrNull { it.name.contains(parsed.id) }
    return PendingCrash(
        id = parsed.id,
        summary = buildString {
            append(parsed.exceptionClass.substringAfterLast('.'))
            parsed.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
            append("  ·  ").append(parsed.formattedDate)
        },
        dump = dump,
        folderPath = (dump?.parentFile ?: newest.parentFile)?.absolutePath.orEmpty()
    )
}

/** Marks [record] as seen; the next launch no longer offers it. */
internal fun dismissCrash(context: Context, record: PendingCrash) {
    runCatching { acknowledgedMarker(context).writeText(record.id) }
}
