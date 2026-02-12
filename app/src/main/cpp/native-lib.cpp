/**
 * JNI wraper Pawn Compiler
 */

#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <sstream>
#include <cstring>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <android/log.h>
#include <unistd.h>
#include <libgen.h>
#include <pthread.h>
#include <map>

#define LOG_TAG "PawnCompiler"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// compilation thread stack size 
#define COMPILE_THREAD_STACK_SIZE (8 * 1024 * 1024)

extern "C" {
    int pc_compile(int argc, char *argv[]);
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

// output sistem
namespace {
    std::mutex g_outputMutex;
    std::stringstream g_outputBuffer;
    std::stringstream g_errorBuffer;
    
    const int MAX_WARNINGS = 15;
    const int MAX_ERRORS = 24;
    const size_t MAX_BUFFER_SIZE = 512 * 1024;
    
    int g_warningCount = 0;
    int g_errorCount = 0;
    bool g_warningLimitReached = false;
    bool g_errorLimitReached = false;
    
    std::mutex g_posMutex;
    std::map<FILE*, fpos_t> g_filePositions;
    
    void clearBuffers() {
        std::lock_guard<std::mutex> lock(g_outputMutex);
        g_outputBuffer.str("");
        g_outputBuffer.clear();
        g_errorBuffer.str("");
        g_errorBuffer.clear();
        g_warningCount = 0;
        g_errorCount = 0;
        g_warningLimitReached = false;
        g_errorLimitReached = false;
    }
    
    std::string getOutputResult(int exitCode) {
        std::lock_guard<std::mutex> lock(g_outputMutex);
        std::string output = g_errorBuffer.str();
        if (!g_outputBuffer.str().empty()) {
            if (!output.empty()) output += "\n";
            output += g_outputBuffer.str();
        }
        std::stringstream ss;
        ss << "Exit code: " << exitCode << "\n" << output;
        return ss.str();
    }
}

// function i/o

extern "C" int pc_printf(const char* message, ...) {
    if (!message) return 0;
    
    va_list argptr;
    va_start(argptr, message);
    
    char stackBuffer[4096];
    va_list argcopy;
    va_copy(argcopy, argptr);
    int needed = vsnprintf(stackBuffer, sizeof(stackBuffer), message, argcopy);
    va_end(argcopy);
    
    char* buffer = stackBuffer;
    char* heapBuffer = nullptr;
    
    if (needed >= (int)sizeof(stackBuffer)) {
        heapBuffer = static_cast<char*>(malloc(needed + 1));
        if (heapBuffer) {
            vsnprintf(heapBuffer, needed + 1, message, argptr);
            buffer = heapBuffer;
        }
    }
    va_end(argptr);
    
    if (needed > 0) {
        LOGD("pc_printf: %s", buffer);
        std::lock_guard<std::mutex> lock(g_outputMutex);
        g_outputBuffer << buffer;
    }
    
    if (heapBuffer) free(heapBuffer);
    return needed;
}

extern "C" int pc_error(int number, char* message, char* filename,
                        int firstline, int lastline, va_list argptr) {
    static const char* prefix[3] = {"error", "fatal error", "warning"};
    
    bool isWarning = (number >= 200);
    bool isError = (number > 0 && number < 200);
    
    {
        std::lock_guard<std::mutex> lock(g_outputMutex);
        
        if (g_errorBuffer.tellp() >= (std::streampos)MAX_BUFFER_SIZE) {
            return 0;
        }
        
        if (isWarning) {
            g_warningCount++;
            if (g_warningCount > MAX_WARNINGS) {
                if (!g_warningLimitReached) {
                    g_warningLimitReached = true;
                    g_errorBuffer << "\n... (" << MAX_WARNINGS << "+ warnings truncated)\n";
                    LOGI("Warning limit reached (%d)", MAX_WARNINGS);
                }
                return 0;
            }
        }
        
        if (isError) {
            g_errorCount++;
            if (g_errorCount > MAX_ERRORS) {
                if (!g_errorLimitReached) {
                    g_errorLimitReached = true;
                    g_errorBuffer << "\n... (" << MAX_ERRORS << "+ errors truncated)\n";
                    LOGE("Error limit reached (%d)", MAX_ERRORS);
                }
                return 0;
            }
        }
    }
    
    std::stringstream ss;
    
    if (number != 0) {
        const char* pre = prefix[number / 100];
        if (number == 111 || number == 237) {
            ss << filename << "(" << lastline << ") : ";
        } else if (firstline >= 0) {
            ss << filename << "(" << firstline << " -- " << lastline << ") : "
               << pre << " " << number << ": ";
        } else {
            ss << filename << "(" << lastline << ") : " << pre << " " << number << ": ";
        }
    }
    
    char stackBuffer[4096];
    va_list argcopy;
    va_copy(argcopy, argptr);
    int needed = vsnprintf(stackBuffer, sizeof(stackBuffer), message, argcopy);
    va_end(argcopy);
    
    char* msgBuffer = stackBuffer;
    char* heapBuffer = nullptr;
    
    if (needed >= (int)sizeof(stackBuffer)) {
        heapBuffer = static_cast<char*>(malloc(needed + 1));
        if (heapBuffer) {
            vsnprintf(heapBuffer, needed + 1, message, argptr);
            msgBuffer = heapBuffer;
        }
    }
    
    ss << msgBuffer;
    if (heapBuffer) free(heapBuffer);
    
    std::string errorStr = ss.str();
    
    if (isWarning) {
        LOGI("Warning: %s", errorStr.c_str());
    } else if (number >= 100) {
        LOGE("Fatal: %s", errorStr.c_str());
    } else if (isError) {
        LOGE("Error: %s", errorStr.c_str());
    } else {
        LOGD("Info: %s", errorStr.c_str());
    }
    
    std::lock_guard<std::mutex> lock(g_outputMutex);
    g_errorBuffer << errorStr;
    return 0;
}

// pawnc file i/o functions

extern "C" void* pc_opensrc(char* filename) {
    return fopen(filename, "rt");
}

extern "C" void* pc_createsrc(char* filename) {
    return fopen(filename, "wt");
}

extern "C" void* pc_createtmpsrc(char** filename) {
    char* tname = nullptr;
    FILE* ftmp = nullptr;
    
    static const char template_str[] = "/tmp/pawnXXXXXX";
    if ((tname = static_cast<char*>(malloc(sizeof(template_str)))) != nullptr) {
        int fdtmp;
        strncpy(tname, template_str, sizeof(template_str));
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
        FILE* fp = static_cast<FILE*>(handle);
        {
            std::lock_guard<std::mutex> lock(g_posMutex);
            g_filePositions.erase(fp);
        }
        fclose(fp);
    }
}

extern "C" void pc_resetsrc(void* handle, void* position) {
    if (handle != nullptr) {
        fsetpos(static_cast<FILE*>(handle), static_cast<fpos_t*>(position));
    }
}

extern "C" char* pc_readsrc(void* handle, unsigned char* target, int maxchars) {
    return fgets(reinterpret_cast<char*>(target), maxchars, static_cast<FILE*>(handle));
}

extern "C" int pc_writesrc(void* handle, unsigned char* source) {
    return fputs(reinterpret_cast<char*>(source), static_cast<FILE*>(handle)) >= 0;
}

extern "C" void* pc_getpossrc(void* handle) {
    FILE* fp = static_cast<FILE*>(handle);
    std::lock_guard<std::mutex> lock(g_posMutex);
    fgetpos(fp, &g_filePositions[fp]);
    return &g_filePositions[fp];
}

extern "C" int pc_eofsrc(void* handle) {
    return feof(static_cast<FILE*>(handle));
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
    if (fbin != nullptr) setvbuf(fbin, nullptr, _IOFBF, 1UL << 20);
    return fbin;
}

extern "C" void pc_closebin(void* handle, int deletefile) {
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

//thread

struct CompileArgs {
    int argc;
    char** argv;
    int result;
};

static void* compile_thread_func(void* arg) {
    CompileArgs* cargs = static_cast<CompileArgs*>(arg);
    LOGI("Compile thread started with %d arguments", cargs->argc);
    cargs->result = pc_compile(cargs->argc, cargs->argv);
    LOGI("Compile thread finished with result: %d", cargs->result);
    return nullptr;
}

static int compile_with_large_stack(int argc, char** argv) {
    CompileArgs cargs = {argc, argv, -1};
    
    pthread_t thread;
    pthread_attr_t attr;
    
    if (pthread_attr_init(&attr) != 0) {
        LOGE("Failed to init thread attributes, falling back to direct call");
        return pc_compile(argc, argv);
    }
    
    if (pthread_attr_setstacksize(&attr, COMPILE_THREAD_STACK_SIZE) != 0) {
        LOGE("Failed to set stack size, falling back to direct call");
        pthread_attr_destroy(&attr);
        return pc_compile(argc, argv);
    }
    
    LOGI("Creating compile thread with %zu byte stack", (size_t)COMPILE_THREAD_STACK_SIZE);
    
    if (pthread_create(&thread, &attr, compile_thread_func, &cargs) != 0) {
        LOGE("Failed to create compile thread, falling back to direct call");
        pthread_attr_destroy(&attr);
        return pc_compile(argc, argv);
    }
    
    pthread_attr_destroy(&attr);
    pthread_join(thread, nullptr);
    
    return cargs.result;
}

//jni intfc

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("PawnCompiler native library loaded");
    return JNI_VERSION_1_6;
}


//nativeCompileWithOutput

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_compile(JNIEnv* env, jobject thiz,
                                           jobjectArray args) {
    clearBuffers();
    
    {
        std::lock_guard<std::mutex> lock(g_posMutex);
        g_filePositions.clear();
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
    int result = compile_with_large_stack((int)argv.size(), argv.data());
    LOGI("pc_compile returned: %d", result);
    
    return env->NewStringUTF(getOutputResult(result).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_getOutput(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_outputMutex);
    return env->NewStringUTF(g_outputBuffer.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvdjv_pawnmc_PawnCompiler_getErrors(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_outputMutex);
    return env->NewStringUTF(g_errorBuffer.str().c_str());
}

} // extern "C"