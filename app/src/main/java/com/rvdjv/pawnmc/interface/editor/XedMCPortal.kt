package com.rvdjv.pawnmc.`interface`.editor

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
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
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

internal data class PawnccInvocation(val source: File, val options: List<String>)

internal object XedTerminalCommandParser {
    val commandNames = listOf("help", "docs", "pawncc", "switch", "clear", "celar")

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
            return commandNames.filter { it.startsWith(trimmed, ignoreCase = true) && it != trimmed }
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
        return emptyList()
    }
}

internal object XedSimulationData {
    @Volatile
    private var cached: Map<String, String>? = null

    fun get(context: Context, key: String, language: CompilerConfig.AppLanguage): String {
        val entries = cached ?: synchronized(this) {
            cached ?: runCatching {
                context.applicationContext.assets.open("_dat_simulation.dat")
                    .bufferedReader().use { parse(it.readText()) }
            }.getOrDefault(emptyMap()).also { cached = it }
        }
        return entries["$key.${language.value}"]
            ?: entries["$key.en"]
            ?: key
    }

    internal fun parse(raw: String): Map<String, String> = buildMap {
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                if (line.startsWith("0x01:") && ":0x02:" in line) {
                    val pair = line.removePrefix("0x01:").split(":0x02:", limit = 2)
                    if (pair.size == 2 && pair[0].isNotBlank()) {
                        put(pair[0].trim(), pair[1].replace("\\n", "\n"))
                    }
                }
            }
    }
}

private data class XedTerminalEntry(
    val command: String,
    val output: String,
    val markdown: Boolean = false
)

@Composable
internal fun XedMCPortal(
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
    val entries = remember { mutableStateListOf<XedTerminalEntry>() }
    var input by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    val currentDragOffset = rememberUpdatedState(dragOffset)

    fun text(key: String) = XedSimulationData.get(context, key, language)

    fun submit() {
        val commandLine = input.trim()
        if (commandLine.isEmpty() || isRunning) return
        input = ""
        keyboard?.hide()
        isRunning = true
        scope.launch {
            val tokens = XedTerminalCommandParser.tokenize(commandLine)
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
                                XedTerminalCommandParser.pawnccInvocation(tokens.drop(1), it)
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
                    else -> {
                        output = text("terminal.error.command")
                        markdown = false
                    }
                }
            }
            if (recordCommand) {
                entries += XedTerminalEntry(commandLine, output, markdown)
                while (entries.size > 24) entries.removeAt(0)
            }
            isRunning = false
        }
    }

    LaunchedEffect(entries.size, isRunning) {
        if (!isRunning) scrollState.animateScrollTo(scrollState.maxValue)
    }

    BackHandler(enabled = true, onBack = onClose)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .testTag("xed_pawn_terminal")
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp,
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
                        text = "MC Developer Portal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = selectedVersion.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .testTag("xed_terminal_output")
                ) {
                    if (entries.isEmpty()) {
                        Text(
                            text = text("terminal.ready"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    entries.forEach { entry ->
                        Text(
                            text = "% ${entry.command}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(top = 6.dp, bottom = 3.dp)
                        )
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

                val suggestions = XedTerminalCommandParser.suggestions(input, activeSource?.name)
                suggestions.take(3).forEach { suggestion ->
                    Text(
                        text = suggestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { input = suggestion },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
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
                        modifier = Modifier.weight(1f).testTag("xed_terminal_input"),
                        singleLine = true,
                        enabled = !isRunning,
                        placeholder = { Text(text("terminal.input.placeholder"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() })
                    )
                    IconButton(onClick = { submit() }, enabled = !isRunning && input.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = text("terminal.run"))
                    }
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