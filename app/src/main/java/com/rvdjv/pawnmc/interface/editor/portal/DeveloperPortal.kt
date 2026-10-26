package com.rvdjv.pawnmc.`interface`.editor.portal

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.rvdjv.pawnmc.data.compiler.Compiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private data class PortalEntry(
    val command: String,
    val output: String,
    val markdown: Boolean = false,
    /**
     * Whether the `% command` line is echoed above the output.
     *
     * The automatic `docs` entry on first open is a welcome document rather than
     * something the user asked for, so it is shown without a prompt.
     */
    val showPrompt: Boolean = true
)

@Composable
internal fun DeveloperPortal(
    context: Context,
    language: CompilerConfig.AppLanguage,
    activeSource: File?,
    workspaceRoot: File?,
    selectedVersion: CompilerConfig.CompilerVersion,
    onVersionChange: (CompilerConfig.CompilerVersion) -> Unit,
    onClose: () -> Unit,
    onCompile: suspend (File, List<String>, CompilerConfig.CompilerVersion) -> String
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    val entries = remember { mutableStateListOf<PortalEntry>() }
    var input by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    // Whether the dots button has unfolded the floating input. Defaulting to visible
    // keeps the field discoverable; the dots collapse it for an immersive read-only
    // transcript and bring it back with one tap.
    var inputImmersive by remember { mutableStateOf(true) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val currentDragOffset = rememberUpdatedState(dragOffset)

    fun text(key: String) = PortalText.get(context, key, language)

    /**
     * Runs [commandLine] as if it had been typed in the console.
     *
     * Used both by the input field and by the automatic `docs` run on first open,
     * so the welcome content goes through exactly the same command path as a
     * manually typed command and can never drift from it.
     */
    fun submit(commandLine: String = input.trim(), isAutomatic: Boolean = false) {
        if (commandLine.isEmpty() || isRunning) return
        input = ""
        keyboard?.hide()
        isRunning = true
        scope.launch {
            val tokens = PortalCommandParser.tokenize(commandLine)
            val output: String
            val markdown: Boolean
            var recordCommand = true
            if (tokens == null) {
                output = text("terminal.error.quote")
                markdown = false
            } else {
                when (tokens.firstOrNull()?.lowercase()) {
                    "help" -> {
                        output = text("terminal.help")
                        markdown = false
                    }
                    "docs" -> {
                        output = text("terminal.docs")
                        markdown = true
                    }
                    "clear", "celar" -> {
                        entries.clear()
                        output = ""
                        markdown = false
                        recordCommand = false
                    }
                    "switch" -> {
                        val requested = when (tokens.getOrNull(1)?.removePrefix("3.10.")) {
                            null -> selectedVersion.other()
                            "7" -> CompilerConfig.CompilerVersion.V3107
                            "11" -> CompilerConfig.CompilerVersion.V31011
                            else -> null
                        }
                        if (tokens.size > 2 || requested == null) {
                            output = text("terminal.error.switch")
                        } else {
                            onVersionChange(requested)
                            output = text("terminal.switch.success")
                                .replace("{version}", requested.value)
                                .replace(
                                    "{restart}",
                                    if (Compiler.isRestartRequired(requested)) text("terminal.switch.restart") else ""
                                )
                        }
                        markdown = false
                    }
                    "pawncc" -> {
                        if (tokens.size == 1) {
                            output = text("terminal.pawncc.usage")
                            markdown = false
                        } else {
                            val root = workspaceRoot ?: activeSource?.parentFile
                            val invocation = root?.let {
                                PortalCommandParser.pawnccInvocation(tokens.drop(1), it)
                            }
                            if (invocation == null) {
                                output = text("terminal.error.source")
                                markdown = false
                            } else if (Compiler.isRestartRequired(selectedVersion)) {
                                output = text("terminal.error.restart")
                                markdown = false
                            } else {
                                output = runCatching {
                                    onCompile(invocation.source, invocation.options, selectedVersion)
                                }.getOrElse {
                                    "${text("terminal.error.execution")} ${it.message.orEmpty()}"
                                }
                                markdown = false
                            }
                        }
                    }
                    "ls" -> {
                        val root = workspaceRoot ?: activeSource?.parentFile
                        val files = root?.listFiles()?.map { if (it.isDirectory) "${it.name}/" else it.name }?.sorted()
                        output = if (!files.isNullOrEmpty()) {
                            files.joinToString("  ")
                        } else {
                            "gamemodes/  filterscripts/  include/  pawno/  scriptfiles/  server.cfg"
                        }
                        markdown = false
                    }
                    "whoami" -> {
                        output = "pawnmc"
                        markdown = false
                    }
                    "time" -> {
                        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        output = timeFormat.format(java.util.Date())
                        markdown = false
                    }
                    "date" -> {
                        val dateFormat = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", java.util.Locale.US)
                        output = dateFormat.format(java.util.Date())
                        markdown = false
                    }
                    "which" -> {
                        val target = tokens.getOrNull(1)
                        output = when (target?.lowercase()) {
                            null -> "usage: which <command>"
                            "pawncc" -> "/system/bin/pawncc (${selectedVersion.value})"
                            "mariadbd", "mariadb" -> "${context.filesDir.absolutePath}/mariadb/bin/mariadbd"
                            "qemu-i386" -> "${context.applicationInfo.nativeLibraryDir}/libqemu-i386.so"
                            "sh", "bash" -> "/system/bin/sh"
                            "ls", "whoami", "time", "date", "which", "deviceinfo", "version", "github", "help", "docs", "switch", "clear", "celar" -> "built-in command: $target"
                            else -> "$target: command not found"
                        }
                        markdown = false
                    }
                    "deviceinfo" -> {
                        output = buildString {
                            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.DEVICE})")
                            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                            appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")
                            append("Board: ${android.os.Build.BOARD} | Hardware: ${android.os.Build.HARDWARE}")
                        }
                        markdown = false
                    }
                    "version" -> {
                        val packageInfo = runCatching {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                context.packageManager.getPackageInfo(context.packageName, 0)
                            }
                        }.getOrNull()
                        val versionName = packageInfo?.versionName ?: "1.5.1"
                        output = "PawnMC v$versionName (compiler: ${selectedVersion.value})"
                        markdown = false
                    }
                    "github" -> {
                        output = "https://github.com/novusr/PawnMC"
                        markdown = false
                    }
                    else -> {
                        // The notice is composed from the command catalogue rather
                        // than a hand-written sentence: `ls`, `whoami`, `time`, ... are
                        // real commands now, and a hardcoded list kept telling users
                        // they did not exist. A near miss is named when it is obvious.
                        val typed = tokens.firstOrNull().orEmpty()
                        val near = PortalCommandParser.nearMisses(typed)
                        val base = text("terminal.error.command")
                            .replace("{commands}", PortalCommandParser.commandNames.joinToString(", ") { "`$it`" })
                        output = if (near.isEmpty()) {
                            base
                        } else {
                            base + "\n" +
                                text("terminal.error.command.suggest")
                                    .replace("{matches}", near.joinToString(", ") { "`$it`" })
                        }
                        markdown = false
                    }
                }
            }
            if (recordCommand) {
                entries += PortalEntry(commandLine, output, markdown, showPrompt = !isAutomatic)
                while (entries.size > 24) entries.removeAt(0)
            }
            isRunning = false
        }
    }

    // Opening the portal should never show an empty console, so `docs` runs once
    // as soon as it appears. The docs entry is marked as pre-recorded, which keeps
    // the `% docs` prompt out of the transcript while still filling the view.
    LaunchedEffect(Unit) {
        if (entries.isEmpty()) submit("docs", isAutomatic = true)
    }

    LaunchedEffect(entries.size, isRunning) {
        if (!isRunning) scrollState.animateScrollTo(scrollState.maxValue)
    }

    BackHandler(enabled = true, onBack = onClose)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .testTag("developer_portal")
    ) {
        val horizontalLimit = with(density) { (maxWidth / 2f - 36.dp).toPx().coerceAtLeast(0f) }
        val verticalLimit = with(density) { (maxHeight / 2f - 48.dp).toPx().coerceAtLeast(0f) }
        val limitedDragOffset = IntOffset(
            dragOffset.x.coerceIn(-horizontalLimit.roundToInt(), horizontalLimit.roundToInt()),
            dragOffset.y.coerceIn(-verticalLimit.roundToInt(), verticalLimit.roundToInt())
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 14.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { limitedDragOffset }
                .fillMaxWidth(0.92f)
                .widthIn(max = 620.dp)
                .fillMaxHeight(0.78f)
                .heightIn(min = 220.dp)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                dragOffset = IntOffset(
                                    currentDragOffset.value.x + dragAmount.x.roundToInt(),
                                    currentDragOffset.value.y + dragAmount.y.roundToInt()
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Developer Portal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = selectedVersion.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Hi!",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = text("terminal.close"))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .verticalScroll(scrollState)
                        .testTag("developer_portal_output")
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            text = text("terminal.ready"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    entries.forEach { entry ->
                        if (entry.showPrompt) {
                            Text(
                                text = "% ${entry.command}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                            )
                        }
                        if (entry.markdown) {
                            MarkdownOutput(entry.output)
                        } else {
                            Text(
                                text = entry.output,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                    if (isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(text("terminal.running"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Input on top: the field now sits directly under the title bar, so it
                // is the first thing the eye meets and suggestions unfold below it
                // instead of being pushed off-screen by a growing transcript.
                val suggestions = PortalCommandParser.suggestions(input, activeSource?.name)
                AnimatedVisibility(
                    visible = inputImmersive || input.isNotBlank() || suggestions.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        // 3x3 grid of what can actually be typed. With nothing typed
                        // it lists every everyday command (`ls`, `whoami`, `time`, ...)
                        // rather than only the PawnCC documentation; while typing it
                        // shows the prefix matches, still laid out as a grid so the
                        // rows never turn into a tall list that pushes the input off
                        // the bottom of the floating card.
                        val gridCells = if (input.isBlank()) {
                            PortalCommandParser.commandGrid(activeSource?.name)
                        } else {
                            suggestions
                        }
                        if (gridCells.isNotEmpty()) {
                            CommandSuggestionGrid(
                                cells = gridCells,
                                // A plain command runs on tap, since the grid is the
                                // discoverability surface for them. A cell that is a
                                // template (`pawncc "file.pwn"`) only fills the field,
                                // because compiling whatever is loaded should stay an
                                // explicit choice.
                                onCellClick = { cell ->
                                    if (cell.contains(' ')) {
                                        input = cell
                                    } else {
                                        submit(cell)
                                    }
                                }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it.take(500) },
                                modifier = Modifier.weight(1f).testTag("developer_portal_input"),
                                singleLine = true,
                                enabled = !isRunning,
                                shape = CircleShape,
                                placeholder = { Text(text("terminal.input.placeholder"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submit() })
                            )
                            IconButton(onClick = { submit() }, enabled = !isRunning && input.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = text("terminal.run"))
                            }
                        }
                    }
                }
            }
        }
    }
}
