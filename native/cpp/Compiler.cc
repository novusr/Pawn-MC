/**
 * JNI entry points of the Pawn compiler bridge.
 * File: Compiler.cc
 * Location: cpp/
 * License: Apache License (v2)
 *
 * This file is the *only* translation unit that carries `JNIEXPORT`. It owns three
 * responsibilities and nothing else:
 *
 *  1. turning the Java `String[]` of compiler arguments into the `char**` that
 *     `pc_compile` expects, in the order the compiler requires;
 *  2. telling the back ends where the app cache lives, so the preprocessor scratch file
 *     and the working directory end up next to the project rather than in `/tmp`;
 *  3. handing the compile off to `Diagnostics.cc`, which runs it on a thread with the
 *     enlarged stack the Pawn parser needs.
 *
 * Everything the compiler calls back into lives in its own file — `Buffer.cc` for the
 * printf/error sinks, `Source.cc` for the in-memory script cache, `Files.cc` for the
 * asm/bin/scratch streams. See `README.rst` in this directory for the layout.
 */

# include <jni.h>
# include <string>
# include <vector>
# include <cstdlib>
# include <cstring>
# include <cerrno>
# include <libgen.h>
# include <pthread.h>
# include <dlfcn.h>
# include <android/dlext.h>
# include <fcntl.h>
# include <unistd.h>

# include "Compiler.h"
# include "Bridge.h"

extern "C" {
    int pc_compile(int argc, char *argv[]);
}

namespace {

    /** Session state of the library loader; one bridge per process. */
    MC_BridgeState n_state = { nullptr, nullptr, nullptr, nullptr, nullptr, nullptr };
    std::string n_load_error;

    /**
     * Finds the engine symbols inside an already `dlopen`ed version library.
     *
     * A version library is a plain shared object, not a JNI library: it exports
     * `mc_compile_entry` / `mc_output_entry` / `mc_errors_entry` and nothing else. The
     * bridge calls through those pointers so `pc_compile` resolves inside the version
     * that was selected, instead of the copy linked into the bridge itself.
     */
    bool n_bind_version_handle(void* n_handle) {
        n_state.pc_compile = reinterpret_cast<int (*)(int, char**)>(dlsym(n_handle, MC_SYM_COMPILE));
        n_state.get_output = reinterpret_cast<char* (*)(void)>(dlsym(n_handle, MC_SYM_OUTPUT));
        n_state.get_errors = reinterpret_cast<char* (*)(void)>(dlsym(n_handle, MC_SYM_ERRORS));
        if (n_state.pc_compile == nullptr || n_state.get_output == nullptr || n_state.get_errors == nullptr) {
            const char* n_symbol_error = dlerror();
            n_load_error = n_symbol_error != nullptr
                ? n_symbol_error
                : "Version library is missing one or more engine entry points.";
            LOGE("Version library is missing its engine entry points: %s", n_load_error.c_str());
            n_state.pc_compile = nullptr;
            n_state.get_output = nullptr;
            n_state.get_errors = nullptr;
            return false;
        }
        LOGI("Engine symbols bound from the selected version library");
        return true;
    }

    /**
     * Loads the selected compiler from the app-private code-cache copy.
     * Documents remains the authoritative source; the private copy avoids
     * executable-mapping restrictions on shared storage.
     */
    bool n_load_version(const char* n_version) {
        n_load_error.clear();
        if (n_state.library_dir == nullptr || n_state.library_dir[0] == '\0') {
            n_load_error = "Compiler library directory is not set.";
            LOGE("%s", n_load_error.c_str());
            return false;
        }

        const std::string n_path = std::string(n_state.library_dir) + "/lib" + n_version + ".so";
        const int n_fd = open(n_path.c_str(), O_RDONLY | O_CLOEXEC);
        if (n_fd < 0) {
            n_load_error = strerror(errno);
            LOGE("Could not open %s: %s", n_path.c_str(), n_load_error.c_str());
            return false;
        }

        android_dlextinfo n_ext_info = {};
        n_ext_info.flags = ANDROID_DLEXT_USE_LIBRARY_FD;
        n_ext_info.library_fd = n_fd;
        dlerror();
        void* n_handle = android_dlopen_ext(n_path.c_str(), RTLD_NOW | RTLD_LOCAL, &n_ext_info);
        const char* n_linker_error = n_handle == nullptr ? dlerror() : nullptr;
        const std::string n_error = n_linker_error != nullptr ? n_linker_error : "unknown linker error";
        close(n_fd);
        if (n_handle == nullptr) {
            n_load_error = n_error;
            LOGE("Could not load %s: %s", n_path.c_str(), n_load_error.c_str());
            return false;
        }

        if (!n_bind_version_handle(n_handle)) {
            dlclose(n_handle);
            return false;
        }

        // The previous version stays mapped: a compile may still be walking its buffer
        // when the user switches, and `dlclose` on a live handle would unmap it.
        n_state.handle = n_handle;
        if (n_state.version != nullptr) {
            free(n_state.version);
        }
        n_state.version = strdup(n_version);
        n_load_error.clear();
        LOGI("Engine ready: %s from %s", n_version, n_path.c_str());
        return true;
    }

    /**
     * Resolves the app's cache directory through the JVM.
     *
     * The compiler needs a writable directory for its preprocessor scratch file. Doing it
     * from C++ instead of passing the path in as an argument keeps `Runner.kt` free of any
     * knowledge about where the native layer stages its files, at the cost of this small
     * reflection hop — which happens once per compile, not once per file.
     *
     * Every failure returns `false` and leaves the cache directory untouched, which means
     * the scratch file simply stays in the default location; a compile is never aborted
     * because the cache path could not be read.
     */
    bool n_apply_cache_dir(JNIEnv* env) {
        jclass n_context_class = env->FindClass("android/app/ActivityThread");

        if (n_context_class == nullptr) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            return false;
        }

        jmethodID n_current_application = env->GetStaticMethodID(
            n_context_class,
            "currentApplication",
            "()Landroid/app/Application;"
        );

        if (n_current_application == nullptr) {
            env->DeleteLocalRef(n_context_class);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            return false;
        }

        jobject n_application = env->CallStaticObjectMethod(n_context_class, n_current_application);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        if (n_application == nullptr) {
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jclass n_app_class = env->GetObjectClass(n_application);
        if (n_app_class == nullptr) {
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jmethodID n_get_cache_dir = env->GetMethodID(
            n_app_class,
            "getCacheDir",
            "()Ljava/io/File;"
        );

        if (n_get_cache_dir == nullptr) {
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jobject n_cache_file = env->CallObjectMethod(n_application, n_get_cache_dir);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        if (n_cache_file == nullptr) {
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jclass n_file_class = env->GetObjectClass(n_cache_file);
        if (n_file_class == nullptr) {
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jmethodID n_get_path = env->GetMethodID(
            n_file_class,
            "getAbsolutePath",
            "()Ljava/lang/String;"
        );

        if (n_get_path == nullptr) {
            env->DeleteLocalRef(n_file_class);
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        jstring n_path_string = static_cast<jstring>(env->CallObjectMethod(n_cache_file, n_get_path));

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(n_file_class);
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        if (n_path_string == nullptr) {
            env->DeleteLocalRef(n_file_class);
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return false;
        }

        const char* n_path_chars = env->GetStringUTFChars(n_path_string, nullptr);
        if (n_path_chars != nullptr) {
            MC_files_set_cache_dir(n_path_chars);
            env->ReleaseStringUTFChars(n_path_string, n_path_chars);
        }

        env->DeleteLocalRef(n_path_string);
        env->DeleteLocalRef(n_file_class);
        env->DeleteLocalRef(n_cache_file);
        env->DeleteLocalRef(n_app_class);
        env->DeleteLocalRef(n_application);
        env->DeleteLocalRef(n_context_class);
        return true;
    }
}

// `Diagnostics.cc` binds to `libm.so`'s own compiler, which is what the engine hook
// in `Bridge.h` is for; this file defines it there and `Engine.cc` in a version library.
int MC_run_compile_entry(int argc, char** argv) {
    return pc_compile(argc, argv);
}

jstring MC_runner_compile(JNIEnv* env, jobject thiz, jobjectArray args) {
    (void)thiz;

    MC_buffer_clear();
    MC_source_clear_cache();
    n_apply_cache_dir(env);

    int n_arg_count = env->GetArrayLength(args);
    if (n_arg_count == 0) {
        return env->NewStringUTF("Exit code: -1\nNo arguments provided");
    }

    std::vector<std::string> n_args_storage;
    n_args_storage.reserve(static_cast<size_t>(n_arg_count) + 1U);

    // argv[0] is the compiler's own name, exactly as a desktop invocation would have it.
    jstring n_first_string = static_cast<jstring>(env->GetObjectArrayElement(args, 0));
    const char* n_first_value = env->GetStringUTFChars(n_first_string, nullptr);
    n_args_storage.emplace_back(n_first_value);
    env->ReleaseStringUTFChars(n_first_string, n_first_value);
    env->DeleteLocalRef(n_first_string);

    // The source path is looked for from the end: user options follow the source, so the
    // last recognised script argument is the one being compiled.
    std::string n_source_path;
    for (int n_index = n_arg_count - 1; n_index >= 1; --n_index) {
        jstring n_candidate_string = static_cast<jstring>(env->GetObjectArrayElement(args, n_index));
        const char* n_candidate_value = env->GetStringUTFChars(n_candidate_string, nullptr);
        const bool n_is_source = MC_is_pawn_source_argument(n_candidate_value);
        if (n_is_source) {
            n_source_path = n_candidate_value;
        }
        env->ReleaseStringUTFChars(n_candidate_string, n_candidate_value);
        env->DeleteLocalRef(n_candidate_string);
        if (n_is_source) break;
    }

    if (n_source_path.empty()) {
        return env->NewStringUTF("Exit code: -1\nNo Pawn source path found in compiler arguments");
    }

    // Without `-D` the compiler resolves `#include` relative to its own working directory,
    // which on Android is `/`, so a project-relative include would never be found.
    {
        char* n_path_copy = strdup(n_source_path.c_str());
        if (n_path_copy == nullptr) {
            return env->NewStringUTF("Exit code: -1\nFailed to copy Pawn source path");
        }
        n_args_storage.emplace_back(std::string("-D") + dirname(n_path_copy));
        free(n_path_copy);

        LOGI("Working directory: %s", n_args_storage[1].c_str());
    }

    for (int i = 1; i < n_arg_count; ++i) {
        jstring n_arg_string = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        const char* n_arg_value = env->GetStringUTFChars(n_arg_string, nullptr);
        n_args_storage.emplace_back(n_arg_value);
        env->ReleaseStringUTFChars(n_arg_string, n_arg_value);
        env->DeleteLocalRef(n_arg_string);
    }

    std::vector<char*> n_argv(n_args_storage.size());
    for (size_t i = 0; i < n_args_storage.size(); ++i) {
        n_argv[i] = const_cast<char*>(n_args_storage[i].c_str());
        LOGD("Arg[%zu]: %s", i, n_argv[i]);
    }

    if (n_state.pc_compile == nullptr) {
        return env->NewStringUTF("Exit code: -1\nNo compiler version is loaded");
    }

    LOGI("Calling pc_compile with %zu arguments (engine: %s)", n_argv.size(),
         n_state.version != nullptr ? n_state.version : "?");
    int n_result = MC_compile_on_worker_thread(static_cast<int>(n_argv.size()), n_argv.data());
    LOGI("pc_compile returned: %d", n_result);

    return env->NewStringUTF(MC_buffer_result(n_result).c_str());
}

jstring MC_runner_get_output(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return env->NewStringUTF(MC_buffer_output().c_str());
}

jstring MC_runner_get_errors(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return env->NewStringUTF(MC_buffer_errors().c_str());
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    LOGI("Compiler bridge loaded");
    return JNI_VERSION_1_6;
}

// The exported names follow the Java owner class
// (`com.rvdjv.pawnmc.apps_data.data_compiler.NativeBridge`), which is what the JVM resolves
// against; the internal `MC_*` names above are only the file-to-file contract.
JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_compile(
        JNIEnv* env, jobject thiz, jobjectArray args) {
    return MC_runner_compile(env, thiz, args);
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_getOutput(JNIEnv* env, jobject thiz) {
    return MC_runner_get_output(env, thiz);
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_getErrors(JNIEnv* env, jobject thiz) {
    return MC_runner_get_errors(env, thiz);
}

/**
 * Sets the private code-cache folder containing the staged compiler library.
 *
 * Called on every compile start, so the path is always the one the app currently
 * considers its storage root.
 */
JNIEXPORT void JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_setLibraryDirectory(
        JNIEnv* env, jobject thiz, jstring path) {
    (void)thiz;
    if (path == nullptr) return;

    const char* n_chars = env->GetStringUTFChars(path, nullptr);
    if (n_chars == nullptr) return;

    // strdup before releasing: n_chars points into the JVM's string, not into memory
    // this function owns.
    char* n_copy = strdup(n_chars);
    env->ReleaseStringUTFChars(path, n_chars);
    if (n_copy == nullptr) return;

    const bool n_changed = n_state.library_dir != nullptr && strcmp(n_state.library_dir, n_copy) != 0;
    if (n_state.library_dir != nullptr) {
        free(n_state.library_dir);
    }
    n_state.library_dir = n_copy;

    if (n_changed) {
        // The ABI folder changed (storage root moved); the engine loaded from the old
        // folder is not the one this path names, so it is re-resolved on the next
        // `selectVersion`.
        n_state.pc_compile = nullptr;
        n_state.get_output = nullptr;
        n_state.get_errors = nullptr;
    }

    LOGI("Library directory: %s", n_state.library_dir);
}

/** Loads `lib<version>.so` from app-private code cache; false when unusable. */
JNIEXPORT jboolean JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_selectVersion(
        JNIEnv* env, jobject thiz, jstring version) {
    (void)thiz;
    if (version == nullptr) return JNI_FALSE;

    const char* n_version = env->GetStringUTFChars(version, nullptr);
    if (n_version == nullptr) return JNI_FALSE;

    const bool n_ok = n_load_version(n_version);
    env->ReleaseStringUTFChars(version, n_version);
    return n_ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_apps_1data_data_1compiler_NativeBridge_getLoadError(JNIEnv* env, jobject thiz) {
    (void)thiz;
    return env->NewStringUTF(n_load_error.c_str());
}

} // extern "C"
