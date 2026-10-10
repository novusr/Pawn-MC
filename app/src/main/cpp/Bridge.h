# ifndef PAWN_BRIDGE_H
# define PAWN_BRIDGE_H

# include <jni.h>
# include <android/log.h>

# include "Compiler.h"

/**
 * Packages PawnMC installs into `nativeLibraryDir`, i.e. the two compiler builds the
 * user can switch between. Kept here rather than in a Kotlin constant because the C++
 * side has to agree with the file names the JVM will `System.loadLibrary`.
 */
# define MC_PACKAGE "com/rvdjv/pawnmc"

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
extern "C" {

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

void* MC_source_open(char* filename);
void  MC_source_close(void* handle);
void  MC_source_reset(void* handle, void* position);
char* MC_source_read(void* handle, unsigned char* target, int max_chars);
void* MC_source_position(void* handle);
int   MC_source_eof(void* handle);
void* MC_source_create(char* filename);

// --- Files.cpp ------------------------------------------------------------------
// Assembly / binary back ends plus the temporary-file helper.

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

} // extern "C"

#endif // PAWN_BRIDGE_H
