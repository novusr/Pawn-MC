package com.rvdjv.pawnmc

/**
 *kotlin wrapper
 */
object PawnCompiler {

    private var initializedVersion: CompilerConfig.CompilerVersion? = null
    private var isInitialized = false

    //load lib when app is opened
    private fun ensureInitialized(version: CompilerConfig.CompilerVersion): Boolean {
        if (isInitialized) {
            if (initializedVersion != version) {
                android.util.Log.w("PawnCompiler", 
                    "Requested ${version.label} but ${initializedVersion?.label} is already loaded. " +
                    "App restart required.")
            }
            return true
        }

        return try {
            System.loadLibrary(version.libraryName)
            initializedVersion = version
            isInitialized = true
            android.util.Log.i("PawnCompiler", "Loaded: ${version.libraryName}")
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("PawnCompiler", "Failed to load: ${version.libraryName}", e)
            false
        }
    }

    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = initializedVersion

    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean {
        return isInitialized && initializedVersion != requestedVersion
    }

    /**
     * pawn file compilation
     * 
     * @param sourceFile absolute path to .pwn source file
     * @param options compiler options
     * @param version compiler versions
     * @return exitCode capturedOutput
     */
    fun compile(
        sourceFile: String,
        options: List<String> = emptyList(),
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V31011
    ): Pair<Int, String> {
        if (!ensureInitialized(version)) {
            return -1 to "Failed to load compiler library"
        }

        android.util.Log.d("PawnCompiler", "Compiling with: ${initializedVersion?.label}")

        val args = buildList {
            add("pawncc")
            addAll(options)
            add(sourceFile)
        }

        val output = compile(args.toTypedArray())
        return parseCompilerOutput(output)
    }

    private fun parseCompilerOutput(output: String): Pair<Int, String> {
        val exitCodeRegex = """^Exit code: (-?\d+)""".toRegex()
        val match = exitCodeRegex.find(output)
        
        val exitCode = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val actualOutput = output.substringAfter('\n', "")
        
        return exitCode to actualOutput
    }

    fun getCapturedOutput(): String = if (isInitialized) getOutput() else ""
    fun getCapturedErrors(): String = if (isInitialized) getErrors() else ""

    private external fun compile(args: Array<String>): String
    private external fun getOutput(): String
    private external fun getErrors(): String
}
