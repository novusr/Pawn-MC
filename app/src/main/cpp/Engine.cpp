/**
 * Engine entry points of a Pawn version library.
 * File: Engine.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 *
 * Every `libpawnc<version>.so` ships this one file in addition to the version's own
 * `compiler/` sources and the PawnMC bridge files. It is the only thing the JNI bridge
 * knows about a version library:
 *
 * ```
 * mc_compile_entry(argc, argv) -> exit code of pc_compile on the worker thread
 * mc_output_entry()            -> console output captured by the last compile
 * mc_errors_entry()            -> warnings and errors captured by the last compile
 * ```
 *
 * The bridge `dlopen`s the version the user selected and calls these through
 * `dlsym` (`Bridge.h`), which is what keeps `pc_compile` and its globals inside the
 * selected version. Without that indirection both compiler builds would be linked
 * into one library and share a single set of compiler globals.
 *
 * The functions return `char*` instead of `std::string` on purpose: the caller is
 * plain C-style dispatch, and a C++ type crossing a `dlsym` boundary is only safe
 * while both sides are built by the same toolchain — this is, but there is no reason
 * to depend on it.
 */

# include <string>

# include "Bridge.h"

extern "C" {
    int pc_compile(int argc, char** argv);
}

// Executable code, not data: at least one of these has to leave the library or the
// linker is free to drop the whole translation unit when every symbol is hidden.
# define MC_ENGINE_EXPORT __attribute__((visibility("default")))

int MC_run_compile_entry(int argc, char** argv) {
    return pc_compile(argc, argv);
}

extern "C" {

/** Runs `pc_compile` for the active version; returns its exit code. */
MC_ENGINE_EXPORT int mc_compile_entry(int argc, char** argv) {
    return MC_compile_on_worker_thread(argc, argv);
}

/** Console text captured by the last compile, owned by the version library's buffer. */
MC_ENGINE_EXPORT char* mc_output_entry(void) {
    // The caller copies immediately, so the buffer's own storage is enough; the cast
    // drops the constness of `std::string::c_str()` for the `char*` ABI.
    static std::string n_output;
    n_output = MC_buffer_output();
    return const_cast<char*>(n_output.c_str());
}

/** Warning/error text captured by the last compile. */
MC_ENGINE_EXPORT char* mc_errors_entry(void) {
    static std::string n_errors;
    n_errors = MC_buffer_errors();
    return const_cast<char*>(n_errors.c_str());
}

} // extern "C"
