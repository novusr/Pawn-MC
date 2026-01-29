# PawnMC - Pawn Mobile Compiler

An Android app for compiling Pawn scripts directly on mobile devices. Built with Kotlin and integrating the native Pawn compiler library.

![pawnmc](https://raw.githubusercontent.com/gskeleton/Pawn-MC/refs/heads/main/pawnmc.png)

## Requirements

- Android 7.0 (API 24) or higher
- Supported architectures: arm64-v8a, armeabi-v7a

## Installation

### From APK
1. Download the latest APK from releases<br>
\- release-2 (2026): https://github.com/novusr/Pawn-MC/releases/download/Pawn-MC-26-2/pawnmc-release-2.apk
![pawnmc](https://raw.githubusercontent.com/gskeleton/Pawn-MC/refs/heads/main/click_file.png)
2. Install on your Android device
3. And Run.<br>

Custom Compiler Flags: https://github.com/novusr/Pawn-MC/blob/main/options.md<br>
- ![pawnmc](https://raw.githubusercontent.com/gskeleton/Pawn-MC/refs/heads/main/custom_flag.png)

### Build from Source
```bash
# Clone the repository with submodules
git clone --recurse-submodules https://github.com/novusr/Pawn-MC.git
cd Pawn-MC

# If you already cloned without --recurse-submodules:
git submodule update --init --recursive

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug

# Install release APK
./gradlew installRelease
```

## Project Structure

```
Pawn-MC/
├── app/
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt    # Native build config
│       │   └── native-lib.cpp    # JNI bridge & compiler integration
│       ├── java/com/rvdjv/pawnmc/
│       │   ├── MainActivity.kt     # Main UI
│       │   ├── SettingsActivity.kt # Compiler settings
│       │   ├── PawnCompiler.kt     # Native wrapper
│       │   └── CompilerConfig.kt   # Settings storage
│       └── res/
│           ├── layout/             # UI layouts
│           └── menu/               # Menu resources
├── pawnc-3.10.7/                   # Pawn compiler 3.10.7 source (submodule)
└── pawnc-3.10.11/                  # Pawn compiler 3.10.11 source (submodule)
```

## Native Library API

The JNI bridge (`native-lib.cpp`) implements the compiler interface:

```c
// Main compile function
int pc_compile(int argc, char *argv[]);

// Output handler (captures to internal buffer)
int pc_printf(const char *message, ...);

// Error handler with severity levels
int pc_error(int number,      // 0=info, 1-99=error, 100-199=fatal, 200+=warning
             char *message,
             char *filename,
             int firstline,
             int lastline,
             va_list argptr);
```

**JNI Functions:**
- `nativeCompileWithOutput(String[] args)` - Compile and return output
- `nativeGetCapturedOutput()` - Get stdout buffer
- `nativeGetCapturedErrors()` - Get error buffer

## Dev Notes

- Compilation runs on a separate thread with 8MB stack to handle large scripts
- Output is buffered internally and returned as a combined string\
- Settings are persisted using SharedPreferences
