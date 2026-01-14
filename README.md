# PawnMC - Pawn Mobile Compiler

An Android app for compiling Pawn scripts directly on mobile devices. Built with Kotlin and integrating the native Pawn compiler library.

## Requirements

- Android 7.0 (API 24) or higher
- ARM64 device (arm64-v8a architecture)

## Installation

### From APK
1. Download the latest APK from releases
- release-1: https://github.com/novusr/Pawn-MC/releases/download/Pawn-MC-26-1/pawnmc.apk
3. Install on your ARM64 Android device
4. And Run.

### Build from Source
```bash
# Clone the repository
git clone https://github.com/novusr/Pawn-MC.git
cd Pawn-MC

# Build debug APK
./gradlew assembleDebug

# APK will be at: app/build/outputs/apk/debug/app-debug.apk
# Install via adb (optional)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt      # Native build config
│   │   └── pawnc_jni.cpp       # JNI bridge
│   ├── java/com/rvdjv/pawnmc/
│   │   ├── MainActivity.kt     # Main UI
│   │   ├── SettingsActivity.kt # Compiler settings
│   │   ├── PawnCompiler.kt     # Native wrapper
│   │   ├── CompilerConfig.kt   # Settings storage
│   │   ├── CompileError.kt     # Error data class
│   │   └── CompileResult.kt    # Result data class
│   ├── jniLibs/arm64-v8a/
│   │   └── libpawnc.so         # Prebuilt Pawn compiler
│   └── res/
│       ├── layout/             # UI layouts
│       └── menu/               # Menu resources
└── build.gradle.kts
```

## Native Library API

The app uses `libpawnc.so` which provides:

```c
// Compile source file
int pc_compile(int argc, char **argv);

// Set callback for output messages
void pawnc_set_output_callback(void (*callback)(const char *message));

// Set callback for errors/warnings
void pawnc_set_error_callback(void (*callback)(
    int number,           // 0=info, 1-99=error, 100-199=fatal, 200+=warning
    const char *filename,
    int firstline,
    int lastline,
    const char *message
));

// Clear all callbacks
void pawnc_clear_callbacks(void);
```

## Dev Notes

- Compilation runs on a background coroutine to avoid blocking the UI
- Callbacks from native code are posted to the main thread for UI updates
- The working directory is changed to the source file's directory before compilation
- Settings are persisted using SharedPreferences
