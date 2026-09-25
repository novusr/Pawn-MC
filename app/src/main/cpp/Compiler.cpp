/**
 * JNI wraper Pawn Compiler
 * File: Compiler.cpp
 * Location: app/src/
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
# define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__) // NORMAL INFO
# define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__) // ERROR INFO
# define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__) // DEBUGGING

extern "C" { int pc_compile(int argc, char *argv[]); int pc_geterrorwarnings(void); }

extern "C" {
    typedef unsigned char MEMFILE;
    MEMFILE *mfcreate(const char *filename);
    void     mfclose(MEMFILE *mf);
    int      mfdump(MEMFILE *mf);
    long     mfseek(MEMFILE *mf, long offset, int whence);
    int      mfputs(MEMFILE *mf, const char *string);
    char    *mfgets(MEMFILE *mf, char *string, unsigned int size);
}

namespace {
    const int    _MAX_WARNINGS = 15, _MAX_ERRORS = 24;
    const size_t _MAX_BUFFER_SIZE = 512 * 1024; // 512KB

    int  n_warn_cnt = 0;
    int  n_err_cnt = 0;
    bool n_warn_limit = (bool)0;
    bool n_err_limit = (bool)0;

    std::mutex        n_out_mutex;
    std::stringstream n_outputBuffer;
    std::stringstream n_err_buf;
    std::mutex        n_posMutex;
    std::map<FILE*, fpos_t> n_filePositions;

struct n_cached_file {
    const std::string* data;
    size_t pos;
    size_t savedPos;
    n_cached_file(const std::string* d) : data(d), pos(0), savedPos(0) {}
};

    std::unordered_map<std::string, std::string> n_src_cache;
    std::string n_pawn_cachedir = "/tmp";

    void _buf_clear() {
        std::lock_guard<std::mutex> lock(n_out_mutex);
        n_outputBuffer.str("");
        n_outputBuffer.clear();
        n_err_buf.str("");
        n_err_buf.clear();
        n_warn_cnt        = 0;
        n_err_cnt         = 0;
        n_warn_limit      = (bool)0;
        n_err_limit       = (bool)0;
    }

    std::string _out_res(int exitCode) {
        std::lock_guard<std::mutex> lock(n_out_mutex);
        std::string _output = n_err_buf.str();

        if (!n_outputBuffer.str().empty()) {
            if (!_output.empty())
                _output += "\n";
            _output += n_outputBuffer.str();
        }

        std::stringstream ss;
        ss << "Exit code: " << exitCode << "\n" << _output;

        return ss.str();
    }
}

extern "C" int pc_printf(const char* message, ...) {

    if (!message)
        return 0;

    va_list _arg_ptr;
    va_start(_arg_ptr, message);

    va_list _arg_cpy;
    va_copy(_arg_cpy, _arg_ptr);

    char buf[4096];
    int n = vsnprintf(buf,
        sizeof(buf), message, _arg_cpy);
    va_end(_arg_cpy);

    char* _new_buf = buf;
    char* _heap = nullptr;

    if (n >= (int)sizeof(buf)) {
        _heap = static_cast<char*>(malloc(n + 1));
        if (_heap) {
            vsnprintf(_heap,
                n + 1, message, _arg_ptr);
            _new_buf = _heap;
        }
    }
    va_end(_arg_ptr);

    if (n > 0) {
        std::lock_guard<std::mutex> lock(n_out_mutex);
        n_outputBuffer << _new_buf;
    }

    if (_heap) { free(_heap); }

    return n;
}

extern "C" int pc_error(int number, char* message, char* filename,
                        int firstline, int lastline, va_list argptr)
{
    std::stringstream ss;

    bool _n_warn_as_err = (number >= 200 &&
        pc_geterrorwarnings());
    bool _warnings_     = (number >= 200 &&
        !_n_warn_as_err);
    bool _errors_       = (number > 0 &&
        number < 200) || _n_warn_as_err;

    static const char* _prefix[3] = { "error",
                                    "fatal error",
                                    "warning" };

{
    std::lock_guard<std::mutex> lock(n_out_mutex);

    if (n_err_buf.tellp() >= (std::streampos)_MAX_BUFFER_SIZE) { return 0; }

    if (_warnings_ != (bool)0) {
        ++n_warn_cnt;

        if (n_warn_cnt > _MAX_WARNINGS && !n_warn_limit) {
            n_warn_limit = !n_warn_limit;
            n_err_buf << "\n... (" << _MAX_WARNINGS << "+ warnings truncated)\n";
            LOGI("Warning limit reached (%d)", _MAX_WARNINGS);
            return 0;
        }
    }
    
    if (_errors_ != (bool)0) {
        ++n_err_cnt;

        if (n_err_cnt > _MAX_ERRORS && !n_err_limit) {
            n_err_limit = !n_err_limit;
            n_err_buf << "\n... (" << _MAX_ERRORS << "+ errors truncated)\n";
            LOGE("Error limit reached (%d)", _MAX_ERRORS);
            return 0;
        }
    }
}

    if (number != 0) {
        const char* pre = _prefix[number / 100];
        if (_n_warn_as_err) { pre = _prefix[0]; }

        if (number == 111 || number == 237) {
            ss << filename << "(" << lastline << ") : ";
        } else if (firstline >= 0) {
            ss << filename << "(" << firstline << " -- " << lastline << ") : "
               << pre << " " << number << ": ";
        } else {
            ss << filename << "(" << lastline << ") : "
                << pre << " " << number << ": ";
        }
    }
    
    va_list _arg_cpy;
    va_copy(_arg_cpy, argptr);

    char buf[4096];
    int n = vsnprintf(buf,
        sizeof(buf), message, _arg_cpy);
    va_end(_arg_cpy);

    char* msg_buf = buf;
    char* heap_buf = nullptr;

    if (n >= (int)sizeof(buf)) {
        heap_buf = static_cast<char*>(malloc(n + 1));
        if (heap_buf) {
            vsnprintf(heap_buf,
                n + 1, message, argptr);
            msg_buf = heap_buf;
        }
    }

    ss << msg_buf;
    if (heap_buf) free(heap_buf);

    std::string errorStr = ss.str();

    if (_warnings_) { LOGI("Warning: %s", errorStr.c_str()); }
    else if (number >= 100 && !_n_warn_as_err) { LOGE("Fatal: %s", errorStr.c_str()); }
    else if (_errors_)                         { LOGE("Error: %s", errorStr.c_str()); }
    else                                       { LOGD("Info: %s", errorStr.c_str()); }

    std::lock_guard<std::mutex> lock(n_out_mutex);
    n_err_buf << errorStr;

    return 0;
}

extern "C" void* pc_opensrc(char* filename) {
    std::string fname(filename);

    auto it = n_src_cache.find(fname);
    if (it == n_src_cache.end()) {

        FILE* f = fopen(filename, "rb");
        if (!f) return nullptr;

        fseek(f, 0, SEEK_END);
        long fsize = ftell(f);
        fseek(f, 0, SEEK_SET);

        if (fsize <= 0) {
            fclose(f);
            n_src_cache[fname] = "";
        } else {
            std::string content(fsize, '\0');
            size_t bytesRead = fread(&content[0], 1, fsize, f);
            content.resize(bytesRead);
            fclose(f);
            n_src_cache[fname] = std::move(content);
        }
        it = n_src_cache.find(fname);
    }

    return new n_cached_file(&it->second);
}

extern "C" void* pc_createsrc(char* filename) {
    return fopen(filename, "wt");
}

extern "C" void* pc_createtmpsrc(char** filename) {
    char* tname = nullptr;
    FILE* ftmp = nullptr;

    std::string tmpl = n_pawn_cachedir + MC_CACHE;
    if ((tname = static_cast<char*>(malloc(tmpl.size() + 1))) != nullptr) {
        int fdtmp = -1;
        memcpy(tname, tmpl.c_str(), tmpl.size() + 1);
        if ((fdtmp = mkstemp(tname)) >= 0) {
            ftmp = fdopen(fdtmp, "wt");
        }
        if (fdtmp < 0 || filename == nullptr) {
            free(tname);
            tname = nullptr;
        }
    }

    if (filename != nullptr) *filename = tname;
    return ftmp;
}

extern "C" void pc_closesrc(void* handle) {
    if (handle != nullptr) {
        n_cached_file* cf = static_cast<n_cached_file*>(handle);
        delete cf;
    }
}

extern "C" void pc_resetsrc(void* handle, void* position) {
    if (handle != nullptr) {
        n_cached_file* cf = static_cast<n_cached_file*>(handle);
        cf->pos = *static_cast<size_t*>(position);
    }
}

extern "C" char* pc_readsrc(void* handle, unsigned char* target, int maxchars) {
    if (!handle)
        return nullptr;

    n_cached_file* cf = static_cast<n_cached_file*>(handle);
    const std::string& data = *cf->data;

    if (cf->pos >= data.size() || maxchars <= 1)
        return nullptr;

    size_t avail = data.size() - cf->pos;
    size_t maxread = (avail < (size_t)(maxchars - 1)) ? avail : (size_t)(maxchars - 1);

    const char* src = data.c_str() + cf->pos;
    const char* nl = (const char*)memchr(src, '\n', maxread);
    size_t copylen = nl ? (size_t)(nl - src + 1) : maxread;

    memcpy(target, src, copylen);
    target[copylen] = '\0';
    cf->pos += copylen;

    return reinterpret_cast<char*>(target);
}

extern "C" int pc_writesrc(void* handle, unsigned char* source) {
    return fputs(reinterpret_cast<char*>(source), static_cast<FILE*>(handle)) >= 0;
}

extern "C" void* pc_getpossrc(void* handle) {
    n_cached_file* cf = static_cast<n_cached_file*>(handle);
    cf->savedPos = cf->pos;
    return &cf->savedPos;
}

extern "C" int pc_eofsrc(void* handle) {
    n_cached_file* cf = static_cast<n_cached_file*>(handle);
    return (cf->pos >= cf->data->size()) ? 1 : 0;
}

extern "C" void* pc_openasm(char* filename) {
    return mfcreate(filename);
}

extern "C" void pc_closeasm(void* handle, int deletefile) {
    if (handle != nullptr) {
        if (!deletefile)
            mfdump(static_cast<MEMFILE*>(handle));
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

extern "C" char* pc_readasm(void* handle, char* string, int maxchars) {
    return mfgets(static_cast<MEMFILE*>(handle), string, static_cast<unsigned int>(maxchars));
}

extern "C" void* pc_openbin(char* filename) {
    FILE* fbin = fopen(filename, "wb");
    if (fbin != nullptr)
        setvbuf(fbin, nullptr, _IOFBF, 1UL << 20);
    return fbin;
}

extern "C" void pc_closebin(void* handle, int deletefile) {
    if (handle != nullptr) {
        fclose(static_cast<FILE*>(handle));
    if (deletefile) {
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
    return static_cast<int>(fwrite(buffer, 1, size, static_cast<FILE*>(handle))) == size;
}

extern "C" long pc_lengthbin(void* handle) {
    return ftell(static_cast<FILE*>(handle));
}

struct CompileArgs {
    int argc;
    char** argv;
    int result;
};

static void* compiler_thread(void* arg) {
    CompileArgs* cargs = static_cast<CompileArgs*>(arg);

    auto start = std::chrono::steady_clock::now();
    cargs->result = pc_compile(cargs->argc, cargs->argv);

    auto end = std::chrono::steady_clock::now();
    double elp = std::chrono::duration<double, std::milli>(end - start).count();
    LOGI("Compile finished in %.2f ms (exit code: %d)", elp, cargs->result);

    return nullptr;
}

static int compiler_open(int argc, char** argv) {
    CompileArgs cargs = {argc, argv, -1};

    pthread_t thread;
    pthread_attr_t attr;

    if (pthread_attr_init(&attr) != 0) {
        LOGE("Failed to init thread attributes, falling back to direct call");
        return pc_compile(argc, argv);
    }

    if (pthread_attr_setstacksize(&attr, MC_STACK) != 0) {
        LOGE("Failed to set stack size, falling back to direct call");
        pthread_attr_destroy(&attr);
        return pc_compile(argc, argv);
    }

    LOGI("Creating compile thread with %zu byte stack", (size_t)MC_STACK);

    if (pthread_create(&thread, &attr, compiler_thread, &cargs) != 0) {
        LOGE("Failed to create compile thread, falling back to direct call");
        pthread_attr_destroy(&attr);
        return pc_compile(argc, argv);
    }

    pthread_attr_destroy(&attr);
    pthread_join(thread, nullptr);

    return cargs.result;
}

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("PawnCompiler native library loaded");
    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_compile(JNIEnv* env, jobject thiz,
                                           jobjectArray args) {
    _buf_clear();
    n_src_cache.clear();

{
    jclass contextClass = env->FindClass("android/app/ActivityThread");

    if (!contextClass) {
        if (env->ExceptionCheck())
            env->ExceptionClear();

        return env->NewStringUTF(
            "Exit code: -2\nActivityThread not found"
        );
    }

    jmethodID currentApp = env->GetStaticMethodID(
        contextClass,
        "currentApplication",
        "()Landroid/app/Application;"
    );

    if (!currentApp) {
        env->DeleteLocalRef(contextClass);

        if (env->ExceptionCheck())
            env->ExceptionClear();

        return env->NewStringUTF(
            "Exit code: -2\nFailed get currentApplication method"
        );
    }

    jobject app = env->CallStaticObjectMethod(
        contextClass,
        currentApp
    );

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nException currentApplication"
        );
    }

    if (!app) {
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed to get Application context"
        );
    }

    jclass appClass = env->GetObjectClass(app);

    if (!appClass) {
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed get Application class"
        );
    }

    jmethodID getCacheDir = env->GetMethodID(
        appClass,
        "getCacheDir",
        "()Ljava/io/File;"
    );

    if (!getCacheDir) {
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed getCacheDir method"
        );
    }

    jobject cacheFile = env->CallObjectMethod(
        app,
        getCacheDir
    );

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nException getCacheDir"
        );
    }

    if (!cacheFile) {
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\ngetCacheDir returned null"
        );
    }

    jclass fileClass = env->GetObjectClass(cacheFile);

    if (!fileClass) {
        env->DeleteLocalRef(cacheFile);
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed get File class"
        );
    }

    jmethodID getPath = env->GetMethodID(
        fileClass,
        "getAbsolutePath",
        "()Ljava/lang/String;"
    );

    if (!getPath) {
        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(cacheFile);
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed getAbsolutePath method"
        );
    }

    jstring pathStr = (jstring)env->CallObjectMethod(
        cacheFile,
        getPath
    );

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(cacheFile);
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nException getAbsolutePath"
        );
    }

    if (!pathStr) {
        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(cacheFile);
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\ngetAbsolutePath returned null"
        );
    }

    const char* pathChars = env->GetStringUTFChars(
        pathStr,
        nullptr
    );

    if (!pathChars) {
        env->DeleteLocalRef(pathStr);
        env->DeleteLocalRef(fileClass);
        env->DeleteLocalRef(cacheFile);
        env->DeleteLocalRef(appClass);
        env->DeleteLocalRef(app);
        env->DeleteLocalRef(contextClass);

        return env->NewStringUTF(
            "Exit code: -2\nFailed convert cache path"
        );
    }

    n_pawn_cachedir = pathChars;

    env->ReleaseStringUTFChars(
        pathStr,
        pathChars
    );

    env->DeleteLocalRef(pathStr);
    env->DeleteLocalRef(fileClass);
    env->DeleteLocalRef(cacheFile);
    env->DeleteLocalRef(appClass);
    env->DeleteLocalRef(app);
    env->DeleteLocalRef(contextClass);
}

{
    std::lock_guard<std::mutex> lock(n_posMutex);
    n_filePositions.clear();
}

    int argc = env->GetArrayLength(args);
    if (argc == 0) {
        return env->NewStringUTF("Exit code: -1\nNo arguments provided");
    }

    std::vector<std::string> args_storage;
    args_storage.reserve(argc + 1);

    jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(args, 0));
    const char* str = env->GetStringUTFChars(jstr, nullptr);
    args_storage.emplace_back(str);
    env->ReleaseStringUTFChars(jstr, str);
    env->DeleteLocalRef(jstr);

    if (argc > 1) {
        jstring srcJstr = static_cast<jstring>(env->GetObjectArrayElement(args, argc - 1));
        const char* srcStr = env->GetStringUTFChars(srcJstr, nullptr);
        char* pathCopy = strdup(srcStr);
        args_storage.emplace_back(std::string("-D") + dirname(pathCopy));
        free(pathCopy);
        env->ReleaseStringUTFChars(srcJstr, srcStr);
        env->DeleteLocalRef(srcJstr);

        LOGI("Working directory: %s", args_storage[1].c_str());
    }

    for (int i = 1; i < argc; i++) {
        jstr = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        str = env->GetStringUTFChars(jstr, nullptr);
        args_storage.emplace_back(str);
        env->ReleaseStringUTFChars(jstr, str);
        env->DeleteLocalRef(jstr);
    }

    std::vector<char*> argv(args_storage.size());
    for (size_t i = 0; i < args_storage.size(); i++) {
        argv[i] = const_cast<char*>(args_storage[i].c_str());
        LOGD("Arg[%zu]: %s", i, argv[i]);
    }

    LOGI("Calling pc_compile with %zu arguments", argv.size());
    int result = compiler_open((int)argv.size(), argv.data());
    LOGI("pc_compile returned: %d", result);

    return env->NewStringUTF(_out_res(result).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_getOutput(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(n_out_mutex);
    return env->NewStringUTF(n_outputBuffer.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_data_compiler_PawnCompiler_getErrors(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(n_out_mutex);
    return env->NewStringUTF(n_err_buf.str().c_str());
}

} // extern "C"
