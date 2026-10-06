/**
 * Argument inspection and the compile worker thread.
 * File: Diagnostics.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 *
 * Two things that are easy to get wrong and painful to debug on a phone:
 *
 *  1. Which argument is the source file. The compiler is invoked as
 *     `pawncc <source> -d=2 -i=...`, and the JNI side has to know the source *before* it
 *     runs so it can add the `-D<working dir>` flag. Guessing wrong used to mean a build
 *     that silently compiled the wrong directory, so the classification is strict: a
 *     value counts as a source only when it is not a flag, has a real extension, and the
 *     dot is not part of a parent folder name.
 *
 *  2. The stack the compiler runs on. The Pawn parser recurses once per nested
 *     expression, so a deeply nested script overflows a default 1 MB worker stack. The
 *     compile therefore runs on a thread with `MC_STACK`, and every failure to create
 *     that thread falls back to a direct call rather than to an error: a compile on a
 *     small stack is better than no compile at all.
 */

# include <jni.h>
# include <string>
# include <cctype>
# include <chrono>
# include <pthread.h>

# include "Compiler.h"
# include "Bridge.h"

extern "C" {
    int pc_compile(int argc, char *argv[]);
}

bool MC_is_pawn_source_argument(const char* value) {
    if (value == nullptr) {
        return false;
    }
    std::string n_value(value);

    // Flags never carry a source path, and `sym=value` defines do not either.
    if (n_value.empty() || n_value[0] == '-' || n_value.find('=') != std::string::npos) {
        return false;
    }

    const size_t separator = n_value.find_last_of("/\\");
    const size_t dot = n_value.find_last_of('.');
    // No dot at all, or a dot that belongs to a parent folder (`include.v2/main`).
    if (dot == std::string::npos || (separator != std::string::npos && dot < separator)) {
        return false;
    }

    std::string extension = n_value.substr(dot);
    for (char& character : extension) {
        character = static_cast<char>(std::tolower(static_cast<unsigned char>(character)));
    }
    return extension == ".pawn" || extension == ".pwn" || extension == ".p" || extension == ".inc";
}

namespace {

    struct n_compile_args {
        int n_argc;
        char** n_argv;
        int n_result;
    };

    void* n_compiler_thread(void* arg) {
        auto* n_args = static_cast<struct n_compile_args*>(arg);

        auto n_start_time = std::chrono::steady_clock::now();
        n_args->n_result = pc_compile(n_args->n_argc, n_args->n_argv);

        auto n_end_time = std::chrono::steady_clock::now();
        double n_elapsed_ms = std::chrono::duration<double, std::milli>(n_end_time - n_start_time).count();
        LOGI("Compile finished in %.2f ms (exit code: %d)", n_elapsed_ms, n_args->n_result);

        return nullptr;
    }
}

int MC_compile_on_worker_thread(int argc, char** argv) {
    n_compile_args n_args = {argc, argv, -1};

    pthread_t n_thread;
    pthread_attr_t n_thread_attr;

    if (pthread_attr_init(&n_thread_attr) != 0) {
        LOGE("Failed to init thread attributes, falling back to direct call");
        return pc_compile(argc, argv);
    }
    if (pthread_attr_setstacksize(&n_thread_attr, MC_STACK) != 0) {
        LOGE("Failed to set stack size, falling back to direct call");
        pthread_attr_destroy(&n_thread_attr);
        return pc_compile(argc, argv);
    }

    LOGI("Creating compile thread with %zu byte stack", static_cast<size_t>(MC_STACK));

    if (pthread_create(&n_thread, &n_thread_attr, n_compiler_thread, &n_args) != 0) {
        LOGE("Failed to create compile thread, falling back to direct call");
        pthread_attr_destroy(&n_thread_attr);
        return pc_compile(argc, argv);
    }

    pthread_attr_destroy(&n_thread_attr);

    // Joined, not detached: the caller needs the exit code, and the JNI frame that owns
    // the argument vectors stays alive until this returns.
    pthread_join(n_thread, nullptr);

    return n_args.n_result;
}
