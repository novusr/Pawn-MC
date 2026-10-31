# ifndef PAWN_BRIDGE_H
# define PAWN_BRIDGE_H

# include <jni.h>
# include <android/log.h>

# include "Compiler.h"

/**
 * Package of the class carrying the JNI entry points, `Runner`, in slash form.
 * Kept here rather than in a Kotlin constant because the C++ side has to agree with
 * the class the JVM registers the bridge against.
 */
# define MC_PACKAGE "com/rvdjv/pawnmc"

/**
 * Name of the small library that `System.loadLibrary`s the compiler versions.
 *
 * The APK ships this file (and `libandroidx.graphics.path.so`) only; the compiler
 * builds themselves are unpacked by the app into `Documents/PawnMC/library/<abi>/`
 * and `dlopen`ed from there, so several versions can coexist per ABI.
 */
# define MC_BRIDGE_LIBRARY "m"

/**
 * Session state of the bridge: which folder is searched for the compiler versions and
 * which one is currently loaded.
 *
 * `library_dir` is the folder the app extracted the per-ABI archive into, or empty when
 * the app has not extracted anything and only `nativeLibraryDir` applies. The selected
 * version is dlopen'ed on the first `MC_runner_compile` that needs it, not at select
 * time, so a version the user never compiles with is never mapped.
 */
struct MC_BridgeState {
    char* library_dir;
    char* version;
    void* handle;
    int (*pc_compile)(int argc, char** argv);
    char* (*get_output)(void);
    char* (*get_errors)(void);
};

/** Symbol names the version libraries export for the bridge to dispatch to. */
# define MC_SYM_COMPILE "mc_compile_entry"
# define MC_SYM_OUTPUT "mc_output_entry"
# define MC_SYM_ERRORS "mc_errors_entry"

/**
 * Shared entry points of the native compiler bridge.
 *
 * `Compiler.cpp` is the only translation unit that carries `JNIEXPORT`; every other file
 * declares what it implements here so the compiler catches a signature drift between the
 * definition and the call site at build time instead of at the first `UnsatisfiedLinkError`.
 *
 * The header deliberately stays free of `<string>` / `<vector>`: it is included by the
 * JNI-onward layer only, and pulling those in from here would drag them into the two
 * `pc_*` trampolines as well.
 */

// --- Compiler.cpp ---------------------------------------------------------------
// JNI entry points. Their names encode the owner class:
// `com.rvdjv.pawnmc.data.compiler.Runner`.

/** Runs one `pc_compile` on a worker thread and returns `"Exit code: N\n<output>"`. */
jstring MC_runner_compile(JNIEnv* env, jobject thiz, jobjectArray args);

/** Console output captured by the last compile. */
jstring MC_runner_get_output(JNIEnv* env, jobject thiz);

/** Warnings and errors captured by the last compile. */
jstring MC_runner_get_errors(JNIEnv* env, jobject thiz);

// --- Buffer.cpp -----------------------------------------------------------------
// Output/error capture, warning and error counters, and the `pc_printf` sink.

void MC_buffer_clear();
std::string MC_buffer_result(int exit_code);
std::string MC_buffer_output();
std::string MC_buffer_errors();

// --- Source.cpp -----------------------------------------------------------------
// `pc_opensrc` family: the in-memory cache the compiler reads scripts through.

void  MC_source_clear_cache();
void* MC_source_open(char* filename);
void  MC_source_close(void* handle);
void  MC_source_reset(void* handle, void* position);
char* MC_source_read(void* handle, unsigned char* target, int max_chars);
void* MC_source_position(void* handle);
int   MC_source_eof(void* handle);
void* MC_source_create(char* filename);

// --- Files.cpp ------------------------------------------------------------------
// Assembly / binary back ends plus the temporary-file helper.

void  MC_files_set_cache_dir(const char* directory);
void* MC_asm_open(char* filename);
void  MC_asm_close(void* handle, int delete_file);
void  MC_asm_reset(void* handle);
int   MC_asm_write(void* handle, char* string);
char* MC_asm_read(void* handle, char* string, int max_chars);

void* MC_bin_open(char* filename);
void  MC_bin_close(void* handle, int delete_file);
void  MC_bin_reset(void* handle, long offset);
int   MC_bin_write(void* handle, void* buffer, int size);
long  MC_bin_length(void* handle);

void* MC_temp_create(char** filename);

// --- Diagnostics.cpp -------------------------------------------------------------
// Argument classification and the on-device compile thread.

/** True when [value] looks like a Pawn source path rather than a compiler flag. */
bool MC_is_pawn_source_argument(const char* value);

/**
 * Runs `pc_compile` on a thread with the enlarged stack PawnMC needs, falling back to a
 * direct call when the thread cannot be created.
 */
int MC_compile_on_worker_thread(int argc, char** argv);

/**
 * Engine entry points every version library exports for the bridge to dispatch to.
 *
 * Called through `dlsym` on the library selected by `NativeBridge.selectVersion`, so
 * they resolve inside that version even though the bridge itself links a copy of the
 * same translation units. They are `extern "C"` so the symbol name is not mangled.
 */
extern "C" {
    int   mc_compile_entry(int argc, char** argv);
    char* mc_output_entry(void);
    char* mc_errors_entry(void);
}

/**
 * The `pc_compile` the worker thread runs.
 *
 * `Diagnostics.cpp` is linked into `libm.so` and into both version libraries, and each
 * defines this with *its own* `pc_compile` (defined in `Compiler.cpp` for the bridge,
 * in `Engine.cpp` for a version). Calling `pc_compile` straight from `Diagnostics.cpp`
 * would bind every copy to whichever definition the linker saw first, so the compile
 * would run on the bridge's engine instead of the selected one. This hook is what keeps
 * each copy self-contained.
 */
int MC_run_compile_entry(int argc, char** argv);

#endif // PAWN_BRIDGE_H
