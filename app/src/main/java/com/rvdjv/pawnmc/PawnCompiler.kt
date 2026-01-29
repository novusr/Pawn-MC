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
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V31111
    ): Pair<Int, String> {
        if (!ensureInitialized(version)) {
            return Pair(-1, "Failed to load compiler library")
        }

        val actualVersion = initializedVersion ?: version
        android.util.Log.d("PawnCompiler", "Compiling with: ${actualVersion.label}")

        val args = mutableListOf("pawncc")
        args.addAll(options)
        args.add(sourceFile)

        val output = compile(args.toTypedArray())
        
        val exitCode = try {
            val lines = output.split('\n')
            if (lines.isNotEmpty() && lines[0].startsWith("Exit code:")) {
                lines[0].substringAfter("Exit code: ").trim().toIntOrNull() ?: -1
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
        
        val actualOutput = output.substringAfter('\n')
        return Pair(exitCode, actualOutput)
    }

    fun getCapturedOutput(): String = if (isInitialized) getOutput() else ""
    fun getCapturedErrors(): String = if (isInitialized) getErrors() else ""

    private external fun compile(args: Array<String>): String
    private external fun getOutput(): String
    private external fun getErrors(): String
}
