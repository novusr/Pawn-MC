/**
 * Assembly, binary and temporary-file back ends.
 * File: Files.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 *
 * Three small file abstractions the Pawn compiler calls into, grouped because they are
 * all the same shape — open a stream, hand it to the compiler, close it:
 *
 *  - `.asm` output goes through the compiler's own `MEMFILE`, which the submodules
 *    provide as `memfile.c`;
 *  - `.amx` output is a plain buffered `FILE`;
 *  - the preprocessor scratch file comes from `mkstemp`, so the compiler can expand
 *    macros without colliding with a concurrently running build.
 */

# include <jni.h>
# include <string>
# include <cstdio>
# include <cstdlib>
# include <cstring>
# include <unistd.h>

# include "Compiler.h"
# include "Bridge.h"

extern "C" {
    /** In-memory file used by the compiler for the generated assembly. */
    typedef unsigned char MEMFILE;
    MEMFILE *mfcreate(const char *filename);
    void mfclose(MEMFILE *mf);
    int mfdump(MEMFILE *mf);
    long mfseek(MEMFILE *mf, long offset, int whence);
    int mfputs(MEMFILE *mf, const char *string);
    char *mfgets(MEMFILE *mf, char *string, unsigned int size);
}

namespace {

    /**
     * Directory handed to `mkstemp`.
     *
     * Set once per compile by `MC_runner_compile` from `getCacheDir`, because only the
     * Android side knows a path that is guaranteed writable on every device. `/tmp` is the
     * starting value so a stub compile in a unit test still behaves.
     */
    std::string n_pawn_cache_dir = "/tmp";
}

void MC_files_set_cache_dir(const char* directory) {
    if (directory != nullptr && *directory != '\0') {
        n_pawn_cache_dir = directory;
    }
}

// --- Assembly output ------------------------------------------------------------

extern "C" void* pc_openasm(char* filename) {
    return mfcreate(filename);
}

extern "C" void pc_closeasm(void* handle, int delete_file) {
    if (handle != nullptr) {
        // `mfdump` writes the buffered assembly out; a `delete_file` close is the
        // preprocessor-only path, where nothing should reach disk.
        if (!delete_file) {
            mfdump(static_cast<MEMFILE*>(handle));
        }
        mfclose(static_cast<MEMFILE*>(handle));
    }
}

extern "C" void pc_resetasm(void* handle) {
    if (handle != nullptr) {
        mfseek(static_cast<MEMFILE*>(handle), 0, SEEK_SET);
    }
}

extern "C" int pc_writeasm(void* handle, char* string) {
    return mfputs(static_cast<MEMFILE*>(handle), string);
}

extern "C" char* pc_readasm(void* handle, char* string, int max_chars) {
    return mfgets(static_cast<MEMFILE*>(handle), string, static_cast<unsigned int>(max_chars));
}

// --- Binary output --------------------------------------------------------------

extern "C" void* pc_openbin(char* filename) {
    FILE* n_binary_file = fopen(filename, "wb");
    if (n_binary_file != nullptr) {
        // A full megabyte of buffering: the `.amx` is written in many small calls, and on
        // shared storage each flush is a round trip to the FUSE layer.
        setvbuf(n_binary_file, nullptr, _IOFBF, 1UL << 20);
    }
    return n_binary_file;
}

extern "C" void pc_closebin(void* handle, int delete_file) {
    if (handle == nullptr) {
        return;
    }

    fclose(static_cast<FILE*>(handle));

    if (delete_file) {
        // `binfname` is the compiler's own record of the output path; there is no handle
        // left to unlink from once the stream is closed.
        extern char binfname[];
        remove(binfname);
    }
}

extern "C" void pc_resetbin(void* handle, long offset) {
    if (handle != nullptr) {
        fflush(static_cast<FILE*>(handle));
        fseek(static_cast<FILE*>(handle), offset, SEEK_SET);
    }
}

extern "C" int pc_writebin(void* handle, void* buffer, int size) {
    return static_cast<int>(fwrite(buffer, 1, static_cast<size_t>(size), static_cast<FILE*>(handle))) == size;
}

extern "C" long pc_lengthbin(void* handle) {
    return ftell(static_cast<FILE*>(handle));
}

// --- Preprocessor scratch file --------------------------------------------------

extern "C" void* pc_createtmpsrc(char** filename) {
    char* n_temp_name = nullptr;
    FILE* n_temp_file = nullptr;

    std::string n_template = n_pawn_cache_dir + MC_CACHE;
    if ((n_temp_name = static_cast<char*>(malloc(n_template.size() + 1U))) != nullptr) {
        int n_file_descriptor = -1;
        memcpy(n_temp_name, n_template.c_str(), n_template.size() + 1U);
        if ((n_file_descriptor = mkstemp(n_temp_name)) >= 0) {
            n_temp_file = fdopen(n_file_descriptor, "wt");
        }

        if (n_file_descriptor < 0 || filename == nullptr) {
            // Nothing was created, or nobody can learn the name: drop the buffer instead
            // of leaking it and reporting a path that has no file behind it.
            free(n_temp_name);
            n_temp_name = nullptr;
        }
    }

    if (filename != nullptr) {
        *filename = n_temp_name;
    }

    return n_temp_file;
}
