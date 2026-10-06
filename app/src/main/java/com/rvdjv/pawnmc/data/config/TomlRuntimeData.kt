package com.rvdjv.pawnmc.data.config

/**
 * Minimal TOML reader for the files in `data/`.
 *
 * The 2026 data files are real TOML: tables are declared with `[a.b]`, string
 * leaves use `key = "value"` and inline tables use `key = { en = "...", id = "..." }`.
 * Only the subset those files use is supported, which keeps the parser small and
 * predictable:
 *
 *  - comments (`#`), blank lines
 *  - `[table]` headers with dotted and/or quoted segments
 *  - `key = "string"` leaves, with the escapes `\" \\ \n \r \t \uXXXX`
 *  - `key = { k = "v", ... }` inline tables (used for `en`/`id` pairs)
 *
 * The reader never guesses: anything it does not recognise is skipped instead of
 * being half-parsed, so a typo can only drop one entry and never corrupt the
 * whole file.
 */
internal object TomlData {

    /** A parsed document: dotted table path -> (key -> value). */
    class Document internal constructor(
        private val tables: Map<String, Map<String, String>>,
        private val inline: Map<String, Map<String, Map<String, String>>>
    ) {
        /** Value of [key] inside table [table], or `null` when it is not there. */
        fun string(table: String, key: String): String? = tables[table]?.get(key)

        /** Every `key -> value` pair of table [table]. */
        fun table(table: String): Map<String, String> = tables[table].orEmpty()

        /** Inline table (`{ en = "...", id = "..." }`) stored under [table]/[key]. */
        fun inlineTable(table: String, key: String): Map<String, String> =
            inline[table]?.get(key).orEmpty()

        /** Every table whose path starts with [prefix], as `path -> values`. */
        fun tablesUnder(prefix: String): Map<String, Map<String, String>> {
            val head = if (prefix.isEmpty()) "" else "$prefix."
            return tables.filterKeys { it.startsWith(head) && it != prefix }
        }

        /** Every inline table whose path starts with [prefix], as `path -> key -> fields`. */
        fun inlineTablesUnder(prefix: String): Map<String, Map<String, Map<String, String>>> {
            val head = if (prefix.isEmpty()) "" else "$prefix."
            return inline.filterKeys { it.startsWith(head) && it != prefix }
        }

        /** True when nothing at all could be read. */
        fun isEmpty(): Boolean = tables.isEmpty() && inline.isEmpty()
    }

    fun parse(raw: String): Document {
        if (raw.isBlank()) return Document(emptyMap(), emptyMap())

        val tables = linkedMapOf<String, MutableMap<String, String>>()
        val inline = linkedMapOf<String, MutableMap<String, MutableMap<String, String>>>()
        var currentTable = ""

        for (line in raw.lineSequence()) {
            val clean = stripComment(line).trim()
            if (clean.isEmpty()) continue

            if (clean.startsWith("[") && clean.endsWith("]") && !clean.startsWith("[[")) {
                currentTable = parseTablePath(clean.substring(1, clean.length - 1))
                continue
            }

            val equals = indexOfAssignment(clean)
            if (equals < 0) continue

            val key = unquoteKey(clean.substring(0, equals).trim())
            val rawValue = clean.substring(equals + 1).trim()
            if (key.isEmpty()) continue

            if (rawValue.startsWith("{")) {
                val fields = parseInlineTable(rawValue)
                if (fields.isNotEmpty()) {
                    inline.getOrPut(currentTable) { linkedMapOf() }[key] = fields
                }
            } else if (rawValue.startsWith("\"")) {
                val value = parseBasicString(rawValue) ?: continue
                tables.getOrPut(currentTable) { linkedMapOf() }[key] = value
            }
        }

        return Document(tables, inline)
    }

    /** Removes a trailing `#` comment while respecting quoted strings. */
    private fun stripComment(line: String): String {
        var inString = false
        var escaped = false
        line.forEachIndexed { index, ch ->
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                ch == '#' && !inString -> return line.substring(0, index)
            }
        }
        return line
    }

    /**
     * Splits a dotted table header, keeping quoted segments such as
     * `[native."a_players.inc"]` intact. Quotes are removed from every segment.
     */
    private fun parseTablePath(raw: String): String {
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escaped = false

        raw.forEach { ch ->
            when {
                escaped -> { current.append(ch); escaped = false }
                ch == '\\' && inString -> { current.append(ch); escaped = true }
                ch == '"' -> inString = !inString
                ch == '.' && !inString -> {
                    segments += unquoteKey(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        segments += unquoteKey(current.toString().trim())
        return segments.filter { it.isNotEmpty() }.joinToString(".")
    }

    private fun unquoteKey(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return trimmed
    }

    /** Position of the first `=` that is not inside a quoted key. */
    private fun indexOfAssignment(line: String): Int {
        var inString = false
        var escaped = false
        line.forEachIndexed { index, ch ->
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                ch == '=' && !inString -> return index
            }
        }
        return -1
    }

    /**
     * Decodes a TOML basic string. Returns `null` when the closing quote is
     * missing, so a truncated line is dropped rather than half-read.
     */
    private fun parseBasicString(raw: String): String? {
        if (!raw.startsWith("\"")) return null
        val builder = StringBuilder()
        var index = 1
        while (index < raw.length) {
            val ch = raw[index]
            when {
                ch == '"' -> return builder.toString()
                ch == '\\' -> {
                    index += 1
                    if (index >= raw.length) return null
                    when (val escape = raw[index]) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> {
                            if (index + 4 >= raw.length) return null
                            val hex = raw.substring(index + 1, index + 5)
                            val code = hex.toIntOrNull(16) ?: return null
                            builder.append(code.toChar())
                            index += 4
                        }
                        else -> builder.append(escape)
                    }
                }
                else -> builder.append(ch)
            }
            index += 1
        }
        return null
    }

    /**
     * Parses `{ en = "...", id = "..." }` into a map.
     *
     * A comma inside a quoted value is not a separator, so the fields are walked
     * with the same string awareness as the rest of the reader.
     */
    private fun parseInlineTable(raw: String): MutableMap<String, String> {
        val body = raw.trim().removePrefix("{")
        val result = linkedMapOf<String, String>()

        var index = 0
        while (index < body.length) {
            // key
            val equals = run {
                var cursor = index
                var inString = false
                var escaped = false
                var found = -1
                while (cursor < body.length && found < 0) {
                    val ch = body[cursor]
                    when {
                        escaped -> escaped = false
                        ch == '\\' && inString -> escaped = true
                        ch == '"' -> inString = !inString
                        ch == '=' && !inString -> found = cursor
                    }
                    cursor += 1
                }
                found
            }
            if (equals < 0) break

            val key = unquoteKey(body.substring(index, equals).trim())

            val valueStart = body.indexOfFirstNonSpace(equals + 1)
            if (valueStart < 0) break
            if (body[valueStart] != '"') {
                index = body.indexOfAny(charArrayOf(',', '}'), valueStart).let { if (it < 0) body.length else it + 1 }
                continue
            }

            val value = parseBasicString(body.substring(valueStart)) ?: break
            if (key.isNotEmpty()) result[key] = value

            // Resume after the closing quote of the value, never at the first
            // comma or brace inside it: a value such as
            // "SetPlayerHealth(playerid, Float:health)" contains a comma of its
            // own, and searching from the opening quote would cut the field there.
            val valueEnd = valueStart + encodedLength(body, valueStart)
            val end = body.indexOfAny(charArrayOf(',', '}'), valueEnd)
            if (end < 0) break
            index = end + 1
        }

        return result
    }

    /**
     * Length in characters of the basic string that starts at [start], including
     * both quotes, or the rest of the text when the closing quote is missing.
     */
    private fun encodedLength(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            when {
                text[index] == '\\' -> index += 2
                text[index] == '"' -> return index - start + 1
                else -> index += 1
            }
        }
        return text.length - start
    }

    private fun String.indexOfFirstNonSpace(from: Int): Int {
        for (index in from until length) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }
}