/**
 * Source-file back end for the Pawn compiler.
 * File: Source.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 *
 * The compiler reads scripts through the `pc_opensrc` family rather than through `fopen`,
 * which lets the host decide where the bytes come from. PawnMC serves them from memory:
 * a project re-reads the same `#include` dozens of times during one build, and on Android
 * shared storage every one of those reads is an `open`/`read`/`close` round trip to the
 * FUSE layer.
 *
 * The cache is deliberately *not* invalidated per compile. It is cleared by
 * `MC_runner_compile` at the start of every run, so a stale buffer can never survive into
 * the next build, while a single build still gets the full benefit.
 */

# include <jni.h>
# include <string>
# include <unordered_map>
# include <cstdio>
# include <cstring>

# include "Compiler.h"
# include "Bridge.h"

namespace {

    /**
     * An open script.
     *
     * The compiler treats the handle as opaque and only ever reads the cursor through
     * `pc_readsrc`, so this may hold a pointer into the cache map instead of a copy: the
     * cache is cleared only between builds, which is exactly when no handle is alive.
     */
    struct n_cached_file {
        const std::string* n_data;
        size_t n_position;
        size_t n_saved_position;

        explicit n_cached_file(const std::string* data_ptr)
            : n_data(data_ptr), n_position(0), n_saved_position(0) {}
    };

    /** Absolute path -> contents, for the duration of one compile. */
    std::unordered_map<std::string, std::string> n_source_cache;
}

void MC_source_clear_cache() {
    n_source_cache.clear();
}

extern "C" void* pc_opensrc(char* filename) {
    std::string n_file_name(filename);

    auto n_cache_it = n_source_cache.find(n_file_name);
    if (n_cache_it == n_source_cache.end()) {
        FILE* n_source_file = fopen(filename, "rb");
        if (n_source_file == nullptr) {
            return nullptr;
        }

        fseek(n_source_file, 0, SEEK_END);
        long n_file_size = ftell(n_source_file);
        fseek(n_source_file, 0, SEEK_SET);

        if (n_file_size <= 0) {
            // An empty file is cached as an empty string on purpose: the compiler must
            // still be able to open it, and re-probing the filesystem for every empty
            // include would be a wasted round trip.
            fclose(n_source_file);
            n_source_cache[n_file_name] = "";
        } else {
            std::string n_content(static_cast<size_t>(n_file_size), '\0');
            size_t n_bytes_read = fread(&n_content[0], 1, static_cast<size_t>(n_file_size), n_source_file);
            n_content.resize(n_bytes_read);
            fclose(n_source_file);
            n_source_cache[n_file_name] = std::move(n_content);
        }

        n_cache_it = n_source_cache.find(n_file_name);
    }

    return new n_cached_file(&n_cache_it->second);
}

extern "C" void* pc_createsrc(char* filename) {
    // Preprocessed output goes straight to disk; there is no reason to buffer it.
    return fopen(filename, "wt");
}

extern "C" void pc_closesrc(void* handle) {
    if (handle != nullptr) {
        auto* n_cached_source = static_cast<n_cached_file*>(handle);
        delete n_cached_source;
    }
}

extern "C" void pc_resetsrc(void* handle, void* position) {
    if (handle != nullptr) {
        auto* n_cached_source = static_cast<n_cached_file*>(handle);
        n_cached_source->n_position = *static_cast<size_t*>(position);
    }
}

extern "C" char* pc_readsrc(void* handle, unsigned char* target, int max_chars) {
    if (handle == nullptr) {
        return nullptr;
    }

    auto* n_cached_source = static_cast<n_cached_file*>(handle);
    const std::string& n_data = *n_cached_source->n_data;

    if (n_cached_source->n_position >= n_data.size() || max_chars <= 1) {
        return nullptr;
    }

    size_t n_available = n_data.size() - n_cached_source->n_position;
    size_t n_max_read = (n_available < static_cast<size_t>(max_chars - 1))
        ? n_available
        : static_cast<size_t>(max_chars - 1);

    // The caller's buffer is one line at a time, so the read is cut at the next newline
    // when there is one; a partial line would make the compiler mis-count line numbers in
    // every diagnostic that follows.
    const char* n_source_data = n_data.c_str() + n_cached_source->n_position;
    const char* n_new_line = static_cast<const char*>(memchr(n_source_data, '\n', n_max_read));
    size_t n_copy_length = (n_new_line != nullptr)
        ? static_cast<size_t>(n_new_line - n_source_data + 1)
        : n_max_read;

    memcpy(target, n_source_data, n_copy_length);
    target[n_copy_length] = '\0';
    n_cached_source->n_position += n_copy_length;

    return reinterpret_cast<char*>(target);
}

extern "C" int pc_writesrc(void* handle, unsigned char* source) {
    return fputs(reinterpret_cast<char*>(source), static_cast<FILE*>(handle)) >= 0;
}

extern "C" void* pc_getpossrc(void* handle) {
    // The saved position has to outlive this call, so it lives in the handle rather than
    // on the stack; the compiler keeps the pointer and resets to it later.
    auto* n_cached_source = static_cast<n_cached_file*>(handle);
    n_cached_source->n_saved_position = n_cached_source->n_position;
    return &n_cached_source->n_saved_position;
}

extern "C" int pc_eofsrc(void* handle) {
    auto* n_cached_source = static_cast<n_cached_file*>(handle);
    return (n_cached_source->n_position >= n_cached_source->n_data->size()) ? 1 : 0;
}
