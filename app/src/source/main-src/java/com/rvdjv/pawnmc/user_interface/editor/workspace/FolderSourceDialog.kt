package com.rvdjv.pawnmc.user_interface.editor.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.user_interface.filebrowser.FileBrowserMode
import com.rvdjv.pawnmc.user_interface.filebrowser.FileBrowserDialog
import java.io.File

/**
 * Asks which application should show the folder picker.
 *
 * Android answers `ACTION_OPEN_DOCUMENT_TREE` with the system picker only, so a user who
 * browses folders with ZArchiver had no way to reach it from PawnMC. Every place that
 * needs a folder now goes through this dialog first: the built-in browser, the system
 * picker, or - when they are installed - the third-party managers that understand the
 * same intent.
 *
 * The dialog is skipped entirely when nothing else is installed, so a device without
 * ZArchiver sees no extra step.
 */
@Composable
fun FolderSourceChooserDialog(
    appLanguage: ContentConfig.AppLanguage,
    localizer: com.rvdjv.pawnmc.apps_data.data_config.AppLocalization?,
    onBuiltInPick: () -> Unit,
    onSystemPick: () -> Unit,
    onThirdPartyPick: (FolderPickerTarget) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val thirdParty = remember(context) { availableThirdPartyFolderPickers(context) }

    fun text(key: String, fallback: String): String =
        localizer?.get(key, appLanguage, fallback) ?: fallback

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text("picker.source.title", "Choose a folder picker")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = text(
                        "picker.source.desc",
                        "Pick where the folder comes from. Third-party managers appear only when they are installed."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                PickerSourceRow(
                    icon = Icons.Filled.FolderOpen,
                    title = text("picker.source.builtin", "Built-in browser"),
                    subtitle = text("picker.source.builtin.desc", "Browse folders inside PawnMC"),
                    onClick = onBuiltInPick
                )
                PickerSourceRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = text("picker.source.system", "System picker"),
                    subtitle = text("picker.source.system.desc", "The Android folder picker"),
                    onClick = onSystemPick
                )
                for (target in thirdParty) {
                    PickerSourceRow(
                        icon = Icons.Filled.Folder,
                        title = target.label,
                        subtitle = text("picker.source.thirdparty.desc", "Installed on this device"),
                        onClick = { onThirdPartyPick(target) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text("settings.dialog.cancel", "Cancel"))
            }
        }
    )
}

@Composable
private fun PickerSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One folder or file shown in [IncludePathInfoDialog]. */
private data class PathEntryRow(val file: File, val isDirectory: Boolean)

/** How many child folders / files the listing shows at most. */
private const val INFO_MAX_FOLDERS = 4
private const val INFO_MAX_FILES = 3

/**
 * Detail view of an already configured include path.
 *
 * Replaces the old behaviour where tapping a stored path immediately opened a folder
 * picker: the user can now first see *which* path is stored and what is inside it (the
 * parent, up to four child folders and up to three files) and only then decide to open
 * the picker - which starts on that very folder.
 */
@Composable
fun IncludePathInfoDialog(
    path: String,
    appLanguage: ContentConfig.AppLanguage,
    localizer: com.rvdjv.pawnmc.apps_data.data_config.AppLocalization?,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit
) {
    val folder = remember(path) { File(path) }
    val exists = folder.isDirectory && folder.canRead()
    val children = remember(path, exists) {
        if (!exists) {
            emptyList()
        } else {
            runCatching { folder.listFiles()?.toList().orEmpty() }
                .getOrDefault(emptyList())
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }
    val parents = remember(path) {
        generateSequence(folder.canonicalFileOrNull()?.parentFile) { it.parentFile }
            .take(4)
            .toList()
    }

    fun text(key: String, fallback: String): String =
        localizer?.get(key, appLanguage, fallback) ?: fallback

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = folder.name.ifBlank { path },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column {
                Text(
                    text = text("settings.include.info.full", "Full path"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.padding(top = 12.dp))

                if (!exists) {
                    Text(
                        text = text("settings.include.info.missing", "This folder is not available right now."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = text("settings.include.info.parents", "Parent folders"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    for (parent in parents.take(FILE_LIST_PARENT_LIMIT)) {
                        EntryLine(
                            icon = Icons.Filled.Folder,
                            label = parent.absolutePath,
                            isDirectory = true
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                                    // Built outside the lazy scope so folders always precede files; the
                    // list is short (four plus three) and `items` keeps its order.
                    val folders = children.filter { it.isDirectory }.take(INFO_MAX_FOLDERS)
                    val files = children.filter { it.isFile }.take(INFO_MAX_FILES)
                    val rows = folders.map { PathEntryRow(it, true) } + files.map { PathEntryRow(it, false) }
                    Text(
                        text = text(
                            "settings.include.info.contents",
                            "Contents (up to 4 folders and 3 files)"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (folders.isEmpty() && files.isEmpty()) {
                        Text(
                            text = text("settings.include.info.empty", "This folder is empty."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items(rows) { entry ->
                            EntryLine(
                                icon = if (entry.isDirectory) {
                                    Icons.Filled.Folder
                                } else {
                                    Icons.AutoMirrored.Filled.InsertDriveFile
                                },
                                label = if (entry.isDirectory) {
                                    entry.file.name + "/"
                                } else {
                                    entry.file.name
                                },
                                isDirectory = entry.isDirectory
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBrowse) {
                Text(text("settings.include.info.browse", "Open folder picker"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text("settings.dialog.close", "Close"))
            }
        }
    )
}

private const val FILE_LIST_PARENT_LIMIT = 4

@Composable
private fun EntryLine(icon: ImageVector, label: String, isDirectory: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun File.canonicalFileOrNull(): File? = runCatching { canonicalFile }.getOrNull()

/** Built-in browser used as the "pick it inside PawnMC" answer of the chooser. */
@Composable
fun BuiltInFolderBrowser(
    startDirectory: File?,
    onFolderSelected: (File) -> Unit,
    onDismiss: () -> Unit
) {
    FileBrowserDialog(
        mode = FileBrowserMode.FOLDER,
        initialDirectory = startDirectory,
        onFileSelected = {},
        onFolderSelected = { path -> onFolderSelected(File(path)) },
        onDismiss = onDismiss
    )
}
