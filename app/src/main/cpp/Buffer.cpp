/**
 * Compiler output capture.
 * File: Buffer.cpp
 * Location: app/src/main/cpp
 * License: Apache License (v2)
 *
 * The Pawn compiler does not return its diagnostics: it calls back into the host through
 * `pc_printf` (console text) and `pc_error` (numbered warnings and errors). Both sinks
 * live here together with the counters that decide when to stop collecting, so the whole
 * "what did the compiler say" story is in one translation unit and `Compiler.cpp` only
 * has to read the finished strings out.
 *
 * The mutex is shared across all of these functions because the compiler runs on its own
 * worker thread while the UI thread can call `getOutput`/`getErrors` at any point.
 */

# include <jni.h>
# include <string>
# include <sstream>
# include <mutex>
# include <cstdarg>
# include <cstdio>
# include <cstdlib>

# include "Compiler.h"
# include "Bridge.h"

extern "C" {
    /** Warning-as-error flag, owned by the compiler build itself. */
    int pc_geterrorwarnings(void);
}

namespace {

    int n_warning_count = 0;
    int n_error_count = 0;
    bool n_warning_limit_reached = false;
    bool n_error_limit_reached = false;

    std::mutex n_output_mutex;

    /** Everything the compiler printed through `pc_printf`. */
    std::stringstream n_output_buffer;

    /** Everything the compiler reported through `pc_error`, already formatted. */
    std::stringstream n_error_buffer;

    /**
     * Formats one variadic message into a stack buffer, falling back to the heap when the
     * message does not fit.
     *
     * `vsnprintf` returns the length it *would* have written, so a single call both
     * measures and writes; the second call only happens for oversized messages, which are
     * rare enough that an allocation there is cheaper than sizing every message.
     */
    struct n_formatted {
        char n_stack[4096];
        char* n_heap = nullptr;
        int n_written = 0;

        n_formatted(const char* message, va_list args, va_list probe) {
            n_written = vsnprintf(n_stack, sizeof(n_stack), message, probe);
            if (n_written >= static_cast<int>(sizeof(n_stack))) {
                n_heap = static_cast<char*>(malloc(static_cast<size_t>(n_written) + 1U));
                if (n_heap != nullptr) {
                    vsnprintf(n_heap, static_cast<size_t>(n_written) + 1U, message, args);
                }
            }
        }

        const char* text() const { return n_heap != nullptr ? n_heap : n_stack; }

        ~n_formatted() {
            if (n_heap != nullptr) {
                free(n_heap);
            }
        }
    };
}

namespace MC_buffer {

    /** Drops everything captured by the previous run and resets the counters. */
    void clear() {
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

    /**
     * The string handed back to Kotlin: exit code first, then the diagnostics.
     *
     * The exit code line is a contract, not decoration: `Runner.parseCompilerOutput`
     * reads it with `^Exit code: (-?\d+)` and treats everything after the first newline
     * as the log, so the format has to stay byte-for-byte stable on both sides.
     */
    std::string result(int exit_code) {
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

    /** Console text captured so far, without the exit code line. */
    std::string output() {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        return n_output_buffer.str();
    }

    /** Warnings and errors captured so far, without the exit code line. */
    std::string errors() {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        return n_error_buffer.str();
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

    n_formatted n_message(message, n_arg_ptr, n_arg_copy);
    va_end(n_arg_copy);
    va_end(n_arg_ptr);

    if (n_message.n_written > 0) {
        std::lock_guard<std::mutex> lock(n_output_mutex);
        n_output_buffer << n_message.text();
    }

    return n_message.n_written;
}

extern "C" int pc_error(int number, char* message, char* filename,
                        int first_line, int last_line, va_list arg_ptr) {
    std::stringstream result_stream;

    // 0xx = error, 1xx = fatal, 2xx = warning — unless the build is configured to promote
    // warnings, in which case a 2xx is reported (and counted) as a plain error.
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

        if (n_error_buffer.tellp() >= static_cast<std::streampos>(MC_MAX_BUFFER_SIZE)) {
            return 0;
        }

        if (n_warning_only) {
            ++n_warning_count;

            if (n_warning_count > MC_MAX_WARNINGS && !n_warning_limit_reached) {
                n_warning_limit_reached = true;
                n_error_buffer << "\n... (" << MC_MAX_WARNINGS << "+ warnings truncated)\n";
                LOGI("Warning limit reached (%d)", MC_MAX_WARNINGS);
                return 0;
            }
        }

        if (n_error_only) {
            ++n_error_count;

            if (n_error_count > MC_MAX_ERRORS && !n_error_limit_reached) {
                n_error_limit_reached = true;
                n_error_buffer << "\n... (" << MC_MAX_ERRORS << "+ errors truncated)\n";
                LOGE("Error limit reached (%d)", MC_MAX_ERRORS);
                return 0;
            }
        }
    }

    // Two diagnostics (111 and 237) carry a whole-file meaning, so they are printed with
    // a single line number instead of a range; everything else gets `first -- last` when
    // the compiler knows where it started.
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

    // `arg_ptr` has already been advanced by the caller's own formatting, so it is
    // re-copied here rather than reused directly.
    va_list n_message_args;
    va_copy(n_message_args, arg_ptr);
    n_formatted n_message(message, n_message_args, n_message_args);
    va_end(n_message_args);

    result_stream << n_message.text();

    std::string n_error_message = result_stream.str();

    // Logcat first, so a crash inside the UI still leaves the compiler's own account of
    // the failure behind.
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
// ---------------------------------------------------------------------------------
// C-linkage façade consumed by Compiler.cpp.
//
// The implementation above lives in `namespace MC_buffer`, which keeps the internal
// names off the global namespace the Pawn compiler itself links into. These wrappers
// are what `Bridge.h` declares where `std::string` is available.
// ---------------------------------------------------------------------------------

std::string MC_buffer_output() { return MC_buffer::output(); }

std::string MC_buffer_errors() { return MC_buffer::errors(); }

std::string MC_buffer_result(int exit_code) { return MC_buffer::result(exit_code); }

void MC_buffer_clear() { MC_buffer::clear(); }
