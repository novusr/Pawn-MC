# ifndef COMPILER_H
# define COMPILER_H

# include <string>

/**
 * Shared definitions of the PawnMC native compiler bridge.
 *
 * Everything here is used by more than one translation unit, so it lives in the header
 * instead of being repeated. The implementation is split by responsibility across
 * `Bridge.h`, `Buffer.cpp`, `Source.cpp`, `Files.cpp` and `Diagnostics.cpp`; see
 * `README.rst` in this directory for the whole picture.
 */

/**
 * Template `mkstemp` uses for the preprocessor scratch file.
 *
 * Appended to the caches directory handed over by `getCacheDir`, so the compiler can
 * expand macros without ever touching a path it does not own.
 */
# define MC_CACHE "/pawnXXXXXX"

/** Stack size of the compile worker thread. 8 MB; the Pawn compiler recurses deeply. */
# define MC_STACK (8 * 1024 * 1024)

# define LOG_TAG "PawnCompiler"
# define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
# define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
# define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/**
 * Ceilings on how much of a failed build is reported.
 *
 * A single missing `#include` can produce thousands of cascading errors, and shipping all
 * of them costs a megabyte of UI text to say the same thing. The counters truncate rather
 * than drop, so the user still sees `... (24+ errors truncated)` and knows why.
 */
# define MC_MAX_WARNINGS 15
# define MC_MAX_ERRORS 24

/** Upper bound on the captured output buffers, in bytes. */
# define MC_MAX_BUFFER_SIZE (512U * 1024U)

# endif
