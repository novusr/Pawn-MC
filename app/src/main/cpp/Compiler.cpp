/**
 * JNI wrapper for Pawn compiler
 * File: Compiler.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 */

# include <jni.h>
# include <string>
# include <vector>
# include <mutex>
# include <sstream>
# include <cstring>
# include <cstdarg>
# include <cstdio>
# include <cstdlib>
# include <chrono>
# include <android/log.h>
# include <unistd.h>
# include <libgen.h>
# include <pthread.h>
# include <map>
# include <unordered_map>

# define MC_CACHE "/pawnXXXXXX"
# define MC_STACK (8 * 1024 * 1024) // 8MB
# define LOG_TAG "PawnCompiler"
# define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
# define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
# define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {
    int pc_compile(int argc, char *argv[]);
    int pc_geterrorwarnings(void);
}

extern "C" {
    typedef unsigned char MEMFILE;
    MEMFILE *mfcreate(const char *filename);
    void mfclose(MEMFILE *mf);
    int mfdump(MEMFILE *mf);
    long mfseek(MEMFILE *mf, long offset, int whence);
    int mfputs(MEMFILE *mf, const char *string);
    char *mfgets(MEMFILE *mf, char *string, unsigned int size);
}

namespace {
    const int n_max_warnings = 15;
    const int n_max_errors = 24;
    const size_t n_max_buffer_size = 512 * 1024;

    int n_warning_count = 0;
    int n_error_count = 0;
    bool n_warning_limit_reached = false;
    bool n_error_limit_reached = false;

    std::mutex n_output_mutex;
    std::stringstream n_output_buffer;
    std::stringstream n_error_buffer;
    std::mutex n_position_mutex;
    std::map<FILE*, fpos_t> n_file_positions;

    struct n_cached_file {
        const std::string* n_data;
        size_t n_position;
        size_t n_saved_position;

        explicit n_cached_file(const std::string* data_ptr)
            : n_data(data_ptr), n_position(0), n_saved_position(0) {}
    };

    std::unordered_map<std::string, std::string> n_source_cache;
    std::string n_pawn_cache_dir = "/tmp";

    void n_buffer_clear() {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        n_output_buffer.str("");
        n_output_buffer.clear();
        n_error_buffer.str("");
        n_error_buffer.clear();
        n_warning_count = 0;
        n_error_count = 0;
        n_warning_limit_reached = false;
        n_error_limit_reached = false;
    }

    std::string n_build_result(int exit_code) {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        std::string output = n_error_buffer.str();

        if (!n_output_buffer.str().empty()) {
            if (!output.empty()) {
                output += "\n";
            }
            output += n_output_buffer.str();
        }

        std::stringstream result_stream;
        result_stream << "Exit code: " << exit_code << "\n" << output;
        return result_stream.str();
    }
}

extern "C" int pc_printf(const char* message, ...) {
    if (message == nullptr) {
        return 0;
    }

    va_list n_arg_ptr;
    va_start(n_arg_ptr, message);

    va_list n_arg_copy;
    va_copy(n_arg_copy, n_arg_ptr);

    char n_buffer[4096];
    int n_written = vsnprintf(n_buffer, sizeof(n_buffer), message, n_arg_copy);
    va_end(n_arg_copy);

    char* n_new_buffer = n_buffer;
    char* n_heap_buffer = nullptr;

    if (n_written >= static_cast<int>(sizeof(n_buffer))) {
        n_heap_buffer = static_cast<char*>(malloc(static_cast<size_t>(n_written) + 1U));
        if (n_heap_buffer != nullptr) {
            vsnprintf(n_heap_buffer, static_cast<size_t>(n_written) + 1U, message, n_arg_ptr);
            n_new_buffer = n_heap_buffer;
        }
    }
    va_end(n_arg_ptr);

    if (n_written > 0) {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        n_output_buffer << n_new_buffer;
    }

    if (n_heap_buffer != nullptr) {
        free(n_heap_buffer);
    }

    return n_written;
}

extern "C" int pc_error(int number, char* message, char* filename,
                        int first_line, int last_line, va_list arg_ptr) {
    std::stringstream result_stream;

    bool n_warn_as_error = (number >= 200 && pc_geterrorwarnings());
    bool n_warning_only = (number >= 200 && !n_warn_as_error);
    bool n_error_only = (number > 0 && number < 200) || n_warn_as_error;

    static const char* n_prefix[] = {
        "error",
        "fatal error",
        "warning"
    };

    {
        std::lock_guard<std::mutex> lock(n_output_mutex);

        if (n_error_buffer.tellp() >= static_cast<std::streampos>(n_max_buffer_size)) {
            return 0;
        }

        if (n_warning_only) {
            ++n_warning_count;

            if (n_warning_count > n_max_warnings && !n_warning_limit_reached) {
                n_warning_limit_reached = true;
                n_error_buffer << "\n... (" << n_max_warnings << "+ warnings truncated)\n";
                LOGI("Warning limit reached (%d)", n_max_warnings);
                return 0;
            }
        }

        if (n_error_only) {
            ++n_error_count;

            if (n_error_count > n_max_errors && !n_error_limit_reached) {
                n_error_limit_reached = true;
                n_error_buffer << "\n... (" << n_max_errors << "+ errors truncated)\n";
                LOGE("Error limit reached (%d)", n_max_errors);
                return 0;
            }
        }
    }

    if (number != 0) {
        const char* prefix_value = n_prefix[number / 100];
        if (n_warn_as_error) {
            prefix_value = n_prefix[0];
        }

        if (number == 111 || number == 237) {
            result_stream << filename << "(" << last_line << ") : ";
        } else if (first_line >= 0) {
            result_stream << filename << "(" << first_line << " -- " << last_line << ") : "
                          << prefix_value << " " << number << ": ";
        } else {
            result_stream << filename << "(" << last_line << ") : "
                          << prefix_value << " " << number << ": ";
        }
    }

    va_list n_arg_copy;
    va_copy(n_arg_copy, arg_ptr);

    char n_buffer[4096];
    int n_written = vsnprintf(n_buffer, sizeof(n_buffer), message, n_arg_copy);
    va_end(n_arg_copy);

    char* n_message_buffer = n_buffer;
    char* n_heap_buffer = nullptr;

    if (n_written >= static_cast<int>(sizeof(n_buffer))) {
        n_heap_buffer = static_cast<char*>(malloc(static_cast<size_t>(n_written) + 1U));
        if (n_heap_buffer != nullptr) {
            vsnprintf(n_heap_buffer, static_cast<size_t>(n_written) + 1U, message, arg_ptr);
            n_message_buffer = n_heap_buffer;
        }
    }

    result_stream << n_message_buffer;
    if (n_heap_buffer != nullptr) {
        free(n_heap_buffer);
    }

    std::string n_error_message = result_stream.str();

    if (n_warning_only) {
        LOGI("Warning: %s", n_error_message.c_str());
    } else if (number >= 100 && !n_warn_as_error) {
        LOGE("Fatal: %s", n_error_message.c_str());
    } else if (n_error_only) {
        LOGE("Error: %s", n_error_message.c_str());
    } else {
        LOGD("Info: %s", n_error_message.c_str());
    }

    std::lock_guard<std::mutex> lock(n_output_mutex);
    n_error_buffer << n_error_message;

    return 0;
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
    return fopen(filename, "wt");
}

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
            free(n_temp_name);
            n_temp_name = nullptr;
        }
    }

    if (filename != nullptr) {
        *filename = n_temp_name;
    }

    return n_temp_file;
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
    auto* n_cached_source = static_cast<n_cached_file*>(handle);
    n_cached_source->n_saved_position = n_cached_source->n_position;
    return &n_cached_source->n_saved_position;
}

extern "C" int pc_eofsrc(void* handle) {
    auto* n_cached_source = static_cast<n_cached_file*>(handle);
    return (n_cached_source->n_position >= n_cached_source->n_data->size()) ? 1 : 0;
}

extern "C" void* pc_openasm(char* filename) {
    return mfcreate(filename);
}

extern "C" void pc_closeasm(void* handle, int delete_file) {
    if (handle != nullptr) {
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

extern "C" void* pc_openbin(char* filename) {
    FILE* n_binary_file = fopen(filename, "wb");
    if (n_binary_file != nullptr) {
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

struct n_compile_args {
    int n_argc;
    char** n_argv;
    int n_result;
};

static void* n_compiler_thread(void* arg) {
    auto* n_compile_args = static_cast<struct n_compile_args*>(arg);

    auto n_start_time = std::chrono::steady_clock::now();
    n_compile_args->n_result = pc_compile(n_compile_args->n_argc, n_compile_args->n_argv);

    auto n_end_time = std::chrono::steady_clock::now();
    double n_elapsed_ms = std::chrono::duration<double, std::milli>(n_end_time - n_start_time).count();
    LOGI("Compile finished in %.2f ms (exit code: %d)", n_elapsed_ms, n_compile_args->n_result);

    return nullptr;
}

static int n_compiler_open(int argc, char** argv) {
    n_compile_args n_compile_args = {argc, argv, -1};

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

    if (pthread_create(&n_thread, &n_thread_attr, n_compiler_thread, &n_compile_args) != 0) {
        LOGE("Failed to create compile thread, falling back to direct call");
        pthread_attr_destroy(&n_thread_attr);
        return pc_compile(argc, argv);
    }

    pthread_attr_destroy(&n_thread_attr);
    pthread_join(n_thread, nullptr);

    return n_compile_args.n_result;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    LOGI("PawnCompiler native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_compile(JNIEnv* env, jobject thiz,
                                                          jobjectArray args) {
    (void)thiz;

    n_buffer_clear();
    n_source_cache.clear();

    {
        jclass n_context_class = env->FindClass("android/app/ActivityThread");

        if (n_context_class == nullptr) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
            return env->NewStringUTF("Exit code: -2\nActivityThread not found");
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
            return env->NewStringUTF("Exit code: -2\nFailed get currentApplication method");
        }

        jobject n_application = env->CallStaticObjectMethod(n_context_class, n_current_application);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nException currentApplication");
        }

        if (n_application == nullptr) {
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nFailed to get Application context");
        }

        jclass n_app_class = env->GetObjectClass(n_application);
        if (n_app_class == nullptr) {
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nFailed get Application class");
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
            return env->NewStringUTF("Exit code: -2\nFailed getCacheDir method");
        }

        jobject n_cache_file = env->CallObjectMethod(n_application, n_get_cache_dir);

        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nException getCacheDir");
        }

        if (n_cache_file == nullptr) {
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\ngetCacheDir returned null");
        }

        jclass n_file_class = env->GetObjectClass(n_cache_file);
        if (n_file_class == nullptr) {
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nFailed get File class");
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
            return env->NewStringUTF("Exit code: -2\nFailed getAbsolutePath method");
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
            return env->NewStringUTF("Exit code: -2\nException getAbsolutePath");
        }

        if (n_path_string == nullptr) {
            env->DeleteLocalRef(n_file_class);
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\ngetAbsolutePath returned null");
        }

        const char* n_path_chars = env->GetStringUTFChars(n_path_string, nullptr);
        if (n_path_chars == nullptr) {
            env->DeleteLocalRef(n_path_string);
            env->DeleteLocalRef(n_file_class);
            env->DeleteLocalRef(n_cache_file);
            env->DeleteLocalRef(n_app_class);
            env->DeleteLocalRef(n_application);
            env->DeleteLocalRef(n_context_class);
            return env->NewStringUTF("Exit code: -2\nFailed convert cache path");
        }

        n_pawn_cache_dir = n_path_chars;
        env->ReleaseStringUTFChars(n_path_string, n_path_chars);

        env->DeleteLocalRef(n_path_string);
        env->DeleteLocalRef(n_file_class);
        env->DeleteLocalRef(n_cache_file);
        env->DeleteLocalRef(n_app_class);
        env->DeleteLocalRef(n_application);
        env->DeleteLocalRef(n_context_class);
    }

    {
        std::lock_guard<std::mutex> lock(n_position_mutex);
        n_file_positions.clear();
    }

    int n_arg_count = env->GetArrayLength(args);
    if (n_arg_count == 0) {
        return env->NewStringUTF("Exit code: -1\nNo arguments provided");
    }

    std::vector<std::string> n_args_storage;
    n_args_storage.reserve(static_cast<size_t>(n_arg_count) + 1U);

    jstring n_first_string = static_cast<jstring>(env->GetObjectArrayElement(args, 0));
    const char* n_first_value = env->GetStringUTFChars(n_first_string, nullptr);
    n_args_storage.emplace_back(n_first_value);
    env->ReleaseStringUTFChars(n_first_string, n_first_value);
    env->DeleteLocalRef(n_first_string);

    if (n_arg_count > 1) {
        jstring n_source_string = static_cast<jstring>(env->GetObjectArrayElement(args, n_arg_count - 1));
        const char* n_source_value = env->GetStringUTFChars(n_source_string, nullptr);
        char* n_path_copy = strdup(n_source_value);
        n_args_storage.emplace_back(std::string("-D") + dirname(n_path_copy));
        free(n_path_copy);
        env->ReleaseStringUTFChars(n_source_string, n_source_value);
        env->DeleteLocalRef(n_source_string);

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

    LOGI("Calling pc_compile with %zu arguments", n_argv.size());
    int n_result = n_compiler_open(static_cast<int>(n_argv.size()), n_argv.data());
    LOGI("pc_compile returned: %d", n_result);

    return env->NewStringUTF(n_build_result(n_result).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_getOutput(JNIEnv* env, jobject thiz) {
    (void)thiz;
    std::lock_guard<std::mutex> lock(n_output_mutex);
    return env->NewStringUTF(n_output_buffer.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_getErrors(JNIEnv* env, jobject thiz) {
    (void)thiz;
    std::lock_guard<std::mutex> lock(n_output_mutex);
    return env->NewStringUTF(n_error_buffer.str().c_str());
}

} // extern "C"

