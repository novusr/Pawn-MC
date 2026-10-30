package com.rvdjv.pawnmc.`interface`.editor.portal

import java.io.File

/**
 * A resolved `pawncc` call inside the workspace root.
 */
internal data class PawnccInvocation(val source: File, val options: List<String>)

/**
 * Parses what the user types into the Developer Portal.
 */
internal object PortalCommandParser {

    /** Every command the portal dispatches. */
    val commandNames = listOf(
        "help", "docs", "pawncc", "switch", "clear", "ls",
        "whoami", "time", "date", "which", "deviceinfo", "version", "github"
    )

    /** Commands with no arguments of their own; `which`/`switch`/`pawncc` are separate. */
    private val argumentlessCommands =
        commandNames.filterNot { it == "which" || it == "switch" || it == "pawncc" }

    /** Splits [command] on whitespace, honouring quotes; null on an unterminated quote. */
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

    /** Resolves `pawncc <file> [flags]` against [workspaceRoot]. */
    fun pawnccInvocation(arguments: List<String>, workspaceRoot: File): PawnccInvocation? {
        val sourceArgument = arguments.firstOrNull()?.takeUnless { it.startsWith("-") } ?: return null
        val requested = File(sourceArgument)
        val source = if (requested.isAbsolute) requested else File(workspaceRoot, sourceArgument)
        val options = arguments.drop(1)
        if (options.any { !it.startsWith("-") || it in setOf("--", "|", ";", "&&") }) return null

        val canonicalRoot = runCatching { workspaceRoot.canonicalFile }.getOrNull() ?: return null
        val canonicalSource = runCatching { source.canonicalFile }.getOrNull() ?: return null
        val rootPath = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!canonicalSource.path.startsWith(rootPath)) return null
        if (!canonicalSource.isFile ||
            canonicalSource.extension.lowercase() !in setOf("pawn", "pwn", "p", "inc")
        ) {
            return null
        }

        return PawnccInvocation(canonicalSource, options)
    }

    /** Inline suggestions for the text typed so far. */
    fun suggestions(input: String, activeSourceName: String?): List<String> {
        val trimmed = input.trimStart()
        if (!trimmed.contains(' ')) {
            /*
             * Prefix only, and never the input itself: typing `dev` offers
             * `deviceinfo`, typing nothing offers everything.
             */
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
     * The first cell is the `docs` help page; the rest are the everyday commands.
     * `pawncc` and `switch` stay off the grid because they are only useful with an
     * argument the grid cannot supply - unless a source is open, in which case the
     * `pawncc` template takes the last cell.
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

    /** True when the portal dispatches [input] as a command. */
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

    /** Columns in the suggestion grid. */
    const val SUGGESTION_COLUMNS = 3

    /** Rows in the suggestion grid (3x3). */
    const val SUGGESTION_ROWS = 3
}
