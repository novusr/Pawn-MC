package com.rvdjv.pawnmc.data.pawn

import com.rvdjv.pawnmc.data.config.CompilerConfig

enum class PawnItemKind {
    KEYWORD,
    DIRECTIVE,
    TYPE,
    CONSTANT,
    FUNCTION,
    CALLBACK,
    OPERATOR
}

data class _item(
    val name: String,
    val description: String,
    val kind: PawnItemKind,
    val descriptionId: String? = null,
    val descriptionEs: String? = null,
) {
    fun descriptionFor(language: CompilerConfig.AppLanguage): String = when (language) {
        CompilerConfig.AppLanguage.ID -> descriptionId ?: description
        CompilerConfig.AppLanguage.ES -> descriptionEs ?: description
        CompilerConfig.AppLanguage.EN -> description
    }
}

/**
 * Dedicated Pawn language registry holding official Pawn specifications,
 * keywords, preprocessor directives, types, constants, operators, SA-MP
 * callbacks and native functions. Stored in the data layer separately from
 * the UI, and used by both the syntax highlighter and the autocompletion
 * popup so the two can never drift apart.
 */
object PawnRegistry {

    private val internalItems: List<_item>
        get() = InternDat.dataset().items

    val DIRECTIVES: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.DIRECTIVE }
    val KEYWORDS: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.KEYWORD }
    val OPERATORS: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.OPERATOR }
    val TYPES: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.TYPE }
    val CONSTANTS: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.CONSTANT }
    val CALLBACKS: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.CALLBACK }
    val NATIVES: List<_item> get() = internalItems.filter { it.kind == PawnItemKind.FUNCTION }
    private val allItems: List<_item> by lazy {
        buildList {
            addAll(DIRECTIVES)
            addAll(KEYWORDS)
            addAll(TYPES)
            addAll(CONSTANTS)
            addAll(CALLBACKS)
            addAll(NATIVES)
            addAll(OPERATORS)
            addAll(includeItems())
        }
    }

    /**
     * Include symbols that are not already described by [NATIVES]/[CALLBACKS].
     *
     * They are synthesised from [PawnIndex] so autocompletion offers the
     * complete SA-MP surface, while the curated lists keep their richer
     * descriptions for the symbols they already own.
     */
    private fun includeItems(): List<_item> = buildList {
        val natives = PawnIndex.INCLUDES.flatMap { it.natives }
            .filterNot { it.name in nativeSet || it.name in keywordSet }
            .distinctBy { it.name }
        addAll(natives.map { _item(it.name, it.description, it.kind, it.descriptionId, it.descriptionEs) })

        val forwards = PawnIndex.INCLUDES.flatMap { it.forwards }
            .filterNot { it.name in callbackSet || it.name in keywordSet }
            .distinctBy { it.name }
        addAll(forwards.map { _item(it.name, it.description, it.kind, it.descriptionId, it.descriptionEs) })
    }

    private val keywordSet: Set<String> by lazy {
        KEYWORDS.map { it.name }.toSet()
    }

    /** Include-derived natives, used for highlighting and completion. */
    private val includeNativeSet: Set<String> by lazy {
        PawnIndex.INCLUDES.flatMap { it.natives }.map { it.name }.toSet()
    }

    /** Include-derived forwards, used for highlighting and completion. */
    private val includeForwardSet: Set<String> by lazy {
        PawnIndex.INCLUDES.flatMap { it.forwards }.map { it.name }.toSet()
    }

    /**
     * Multi-character operators, longest first so the lexer can consume a
     * greedy match without leaving a dangling suffix behind.
     */
    val LONG_OPERATORS: List<String> by lazy {
        OPERATORS.map { it.name }.filter { it.length > 1 }.sortedByDescending { it.length }
    }

    /** First characters of any known operator. */
    val OPERATOR_HEADS: Set<Char> by lazy {
        OPERATORS.mapTo(mutableSetOf()) { it.name[0] }
    }

    private val directiveSet: Set<String> by lazy {
        DIRECTIVES.map { it.name.removePrefix("#") }.toSet()
    }

    private val typeSet: Set<String> by lazy {
        TYPES.map { it.name.removeSuffix(":") }.toSet()
    }

    private val constantSet: Set<String> by lazy {
        CONSTANTS.map { it.name }.toSet()
    }

    private val callbackSet: Set<String> by lazy {
        CALLBACKS.map { it.name }.toSet()
    }

    private val nativeSet: Set<String> by lazy {
        NATIVES.map { it.name }.toSet()
    }

    fun isKeyword(word: String): Boolean = word in keywordSet
    fun isDirective(word: String): Boolean = word in directiveSet || word.removePrefix("#") in directiveSet
    fun isType(word: String): Boolean = word in typeSet || word.removeSuffix(":") in typeSet
    fun isConstant(word: String): Boolean = word in constantSet
    fun containsSymbol(name: String): Boolean = allItems.any { it.name.equals(name, ignoreCase = true) }

    /**
     * True for every known callable symbol, including the natives and forwards
     * that only come from the include table.
     */
    fun isFunction(word: String): Boolean =
        word in nativeSet || word in callbackSet || word in includeNativeSet || word in includeForwardSet

    /** True when [text] is a complete operator at the start of a token. */
    fun isOperator(text: String): Boolean = text in operatorSet

    private val operatorSet: Set<String> by lazy {
        OPERATORS.mapTo(mutableSetOf()) { it.name }
    }

    fun getCompletions(
        prefix: String,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): List<_item> {
        val query = prefix.trim()
        if (query.isEmpty()) return emptyList()
        val queryLower = query.lowercase()
        return allItems.filter { item ->
            item.name.lowercase().startsWith(queryLower) ||
                (query.startsWith("#") && item.name.startsWith(query, ignoreCase = true)) ||
                item.name.removePrefix("#").lowercase().startsWith(queryLower)
        }.take(35).map { item ->
            item.copy(description = item.descriptionFor(language))
        }
    }
}
