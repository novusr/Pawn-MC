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
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.rvdjv.pawnmc.data.config.TomlData
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

internal data class PawnccInvocation(val source: File, val options: List<String>)

/** Columns in the Developer Portal command suggestion grid. */
private const val SUGGESTION_COLUMNS = 3

/** Rows in the Developer Portal command suggestion grid (3x3). */
private const val SUGGESTION_ROWS = 3

internal object PortalCommandParser {
    /**
     * Every command the portal actually dispatches.
     *
     * The suggestion grid, the typo hints and the "unknown command" notice all read
     * from this one list, so a new command can never again be advertised as missing
     * the moment it is wired into `submit`.
     */
    val commandNames = listOf(
        "help", "docs", "pawncc", "switch", "clear", "ls",
        "whoami", "time", "date", "which", "deviceinfo", "version", "github"
    )

    /** Commands with no arguments of their own; `which`/`switch`/`pawncc` are separate. */
    private val argumentlessCommands = commandNames.filterNot { it == "which" || it == "switch" || it == "pawncc" }

    fun tokenize(command: String): List<String>? {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var tokenStarted = false

        command.forEach { character ->
            when {
                quote != null && character == quote -> quote = null
                quote != null -> token.append(character)
                character == '\'' || character == '"' -> {
                    quote = character
                    tokenStarted = true
                }
                character.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += token.toString()
                        token.clear()
                        tokenStarted = false
                    }
                }
                else -> {
                    token.append(character)
                    tokenStarted = true
                }
            }
        }

        if (quote != null) return null
        if (tokenStarted) tokens += token.toString()
        return tokens
    }

    fun pawnccInvocation(
        arguments: List<String>,
        workspaceRoot: File
    ): PawnccInvocation? {
        val sourceArgument = arguments.firstOrNull()?.takeUnless { it.startsWith("-") } ?: return null
        val requested = File(sourceArgument)
        val source = if (requested.isAbsolute) requested else File(workspaceRoot, sourceArgument)
        val options = arguments.drop(1)
        if (options.any { !it.startsWith("-") || it in setOf("--", "|", ";", "&&") }) return null

        val canonicalRoot = runCatching { workspaceRoot.canonicalFile }.getOrNull() ?: return null
        val canonicalSource = runCatching { source.canonicalFile }.getOrNull() ?: return null
        val rootPath = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!canonicalSource.path.startsWith(rootPath)) return null
        if (!canonicalSource.isFile || canonicalSource.extension.lowercase() !in setOf("pawn", "pwn", "p", "inc")) {
            return null
        }

        return PawnccInvocation(canonicalSource, options)
    }

    fun suggestions(input: String, activeSourceName: String?): List<String> {
        val trimmed = input.trimStart()
        if (!trimmed.contains(' ')) {
            // Longest prefix plus the shorter deep forms, deduplicated: typing `dev`
            // offers `deviceinfo`, and typing nothing changes nothing because an empty
            // prefix is filtered out (`it != trimmed`).
            return argumentlessCommands
                .filter { it.startsWith(trimmed, ignoreCase = true) && it != trimmed }
                .take(SUGGESTION_LIMIT)
        }
        if (trimmed.startsWith("pawncc ", ignoreCase = true) &&
            trimmed.substringAfter(' ').isBlank() && activeSourceName != null
        ) {
            return listOf("pawncc \"$activeSourceName\"")
        }
        if (trimmed.startsWith("switch ", ignoreCase = true)) {
            return listOf("3.10.7", "3.10.11")
                .filter { it.startsWith(trimmed.substringAfter(' ').trim(), ignoreCase = true) }
                .map { "switch $it" }
        }
        if (trimmed.startsWith("which ", ignoreCase = true)) {
            val arg = trimmed.substringAfter(' ').trim()
            return listOf("pawncc", "mariadbd", "qemu-i386", "sh")
                .filter { it.startsWith(arg, ignoreCase = true) }
                .map { "which $it" }
        }
        return emptyList()
    }

    /**
     * The 3x3 grid shown above the input field when nothing is being typed.
     *
     * The first cell is the `docs` help page; the other eight are the everyday
     * commands. `pawncc` and `switch` stay off the grid because they are only useful
     * with an argument that the grid cannot supply.
     */
    fun commandGrid(activeSourceName: String?): List<String> = listOf(
        "docs",
        "help",
        "ls",
        "clear",
        "whoami",
        "time",
        "date",
        "version",
        "deviceinfo"
    ).let { names ->
        if (activeSourceName.isNullOrBlank()) names
        else names.dropLast(1) + "pawncc \"$activeSourceName\""
    }.take(SUGGESTION_LIMIT)

    /** A command [input] must be a known one; used to word the unknown-command help. */
    fun isKnownCommand(input: String): Boolean {
        val name = input.trimStart().substringBefore(' ').lowercase()
        return name == "celar" || name in commandNames
    }

    /** Commands close enough to [word] to be worth naming in the error output. */
    fun nearMisses(word: String, limit: Int = 3): List<String> {
        val needle = word.lowercase()
        if (needle.length < 2) return emptyList()
        return commandNames
            .map { name -> name to editDistance(needle, name) }
            .filter { (name, distance) ->
                distance <= 2 || name.startsWith(needle.take(2)) || name.contains(needle)
            }
            .sortedBy { (_, distance) -> distance }
            .map { (name, _) -> name }
            .take(limit)
    }

    /** Levenshtein distance, small enough to inline for typo hints. */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /** Suggestions per state; the grid and the inline list both cap at nine. */
    const val SUGGESTION_LIMIT = 9
}

internal object PortalText {

    /**
     * Terminal strings and the `docs` markdown, in the 2026 TOML format.
     *
     * Every table is named after the terminal key (`[terminal.help]`,
     * `[terminal.docs]`, ...), the languages are the leaves, and the old
     * `0x01:` / `0x02:` markers are gone.
     */
    private const val ASSET_PATH = "_data_2026_term.toml"

    @Volatile
    private var cached: Map<String, String>? = null

    fun get(context: Context, key: String, language: CompilerConfig.AppLanguage): String {
        val entries = cached ?: synchronized(this) {
            cached ?: runCatching {
                context.applicationContext.assets.open(ASSET_PATH)
                    .bufferedReader().use { parse(it.readText()) }
            }.getOrDefault(emptyMap()).also { cached = it }
        }
        return entries["$key.${language.value}"]
            ?: entries["$key.en"]
            ?: key
    }

    /**
     * Flattens the tables into `terminal.<key>.<lang> -> text`.
     *
     * The values are stored as single line TOML strings, so the literal `\n`
     * sequences that carry the console layout are expanded into real line breaks
     * here, once, before anything renders them.
     */
    internal fun parse(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()

        val document = TomlData.parse(raw)
        val result = linkedMapOf<String, String>()
        document.tablesUnder("").forEach { (table, entries) ->
            if (table.isBlank()) return@forEach
            entries.forEach { (language, value) ->
                result["$table.$language"] = value.replace("\\n", "\n")
            }
        }
        return result
    }
}

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

@Composable
private fun CommandSuggestionGrid(
    cells: List<String>,
    onCellClick: (String) -> Unit
) {
    // Laid out as three columns of three. A `Column` of fixed-width `Row`s rather
    // than a `LazyVerticalGrid`: the portal card is already a scroll container and
    // nine cells do not need recycling, while a nested lazy grid inside a
    // `verticalScroll` parent throws on unbounded height.
    val rows = cells.chunked(SUGGESTION_COLUMNS).take(SUGGESTION_ROWS)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCells.forEach { cell ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCellClick(cell) }
                    ) {
                        Text(
                            text = cell,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
                // Keep the last row's cells the same width as the full rows so a
                // stray one or two matches do not stretch across the card.
                repeat(SUGGESTION_COLUMNS - rowCells.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MarkdownOutput(markdown: String) {
    var inCodeBlock = false
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markdown.lineSequence().forEach { line ->
            when {
                line.trimStart().startsWith("```") -> inCodeBlock = !inCodeBlock
                line.isBlank() -> Spacer(Modifier.height(3.dp))
                inCodeBlock -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        line,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                }
                line.startsWith("#") -> Text(
                    text = line.trimStart('#', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = "- ${line.drop(2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                else -> Text(
                    text = markdownInline(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun markdownInline(line: String) = buildAnnotatedString {
    val token = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
    var cursor = 0
    token.findAll(line).forEach { match ->
        append(line.substring(cursor, match.range.first))
        val value = match.value
        val content = if (value.startsWith("**")) value.removeSurrounding("**") else value.removeSurrounding("`")
        val style = if (value.startsWith("**")) {
            SpanStyle(fontWeight = FontWeight.Bold)
        } else {
            SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        pushStyle(style)
        append(content)
        pop()
        cursor = match.range.last + 1
    }
    append(line.substring(cursor))
}