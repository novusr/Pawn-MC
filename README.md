# PawnMC - Pawn Mobile Compiler

![Version](https://img.shields.io/badge/version-1.2.6-blue) ![Android](https://img.shields.io/badge/Android-7.0%2B-green) ![License](https://img.shields.io/badge/license-Apache%202.0-orange)

An Android app for compiling Pawn scripts directly on mobile devices. Built with Kotlin and integrating the native Pawn compiler library with support for multiple compiler versions.

![pawnmc](https://raw.githubusercontent.com/gskeleton/Pawn-MC/refs/heads/main/pawnmc.png)

## Features

- **Dual Compiler Support**: Switch between Pawn 3.10.7 and 3.10.11 (requires app restart)
- **File Association**: Open `.pwn`, `.p`, and `.inc` files directly from file managers
- **Auto-Load**: Automatically loads the last selected file on app start
- **Custom Compiler Flags**: Configure custom compilation flags via settings
- **Custom Include Paths**: Add multiple include directories for your libraries
- **Smart Output Buffering**: Limits output to prevent crashes (max 15 warnings, 24 errors, 512KB buffer)
- **Large Script Support**: Handles large scripts with 8MB compilation thread stack
- **Storage Permissions**: Proper handling for Android 10+ scoped storage
- **Native Performance**: Runs the actual Pawn compiler natively via JNI

## Requirements

- **Android**: 7.0 (API 24) or higher
- **Architectures**: arm64-v8a, armeabi-v7a
- **Permissions**: Storage access (MANAGE_EXTERNAL_STORAGE for Android 11+)

## Installation

### From Release

1. Download the latest APK from [Releases](https://github.com/novusr/Pawn-MC/releases)
2. Install on your Android device
3. Grant storage permissions when prompted

### Custom Compiler Flags

Configure custom flags in Settings. See [options.md](options.md) for available flags.

![Custom Flags](https://raw.githubusercontent.com/gskeleton/Pawn-MC/refs/heads/main/custom_flag.png)

### Build from Source

**Prerequisites:**
- Android Studio Arctic Fox or later
- NDK 29.0.14206865
- CMake 3.22.1

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

# Install debug APK directly
./gradlew installDebug

# Install release APK
./gradlew installRelease
```

Built APKs will be in `app/build/outputs/apk/`.

## Usage

1. **Select File**: Tap "Browse Files" button or open `.pwn` file from file manager
2. **Configure Settings**: Access via toolbar menu to:
   - Switch compiler version (3.10.7 ↔ 3.10.11)
   - Add custom include paths
   - Set custom compiler flags
3. **Compile**: Tap "Compile" button to build your script
4. **View Output**: Compilation results appear in the output window

## Settings & Configuration

Access settings from the toolbar menu:

### Compiler Version
- **Pawn 3.10.7**: Stable, SA-MP compatible version
- **Pawn 3.10.11**: Latest version with improvements
- **Note**: Switching versions requires app restart

### Include Paths
- Add multiple include directories
- Automatically applies `-i` flag for each path
- Remove paths individually via UI

### Custom Compiler Flags
- Enter any valid compiler flags (e.g., `-d3`, `-O2`)
- See [options.md](options.md) for complete flag reference
- Flags are persisted across app sessions

### Settings Storage
All settings are stored using Android SharedPreferences and persist across app restarts.

## Project Structure

```
Pawn-MC/
├── app/
│   ├── build.gradle.kts           # App build configuration
│   └── src/main/
│       ├── AndroidManifest.xml    # App manifest with permissions
│       ├── cpp/
│       │   ├── CMakeLists.txt     # Native build configuration
│       │   └── native-lib.cpp     # JNI bridge & compiler integration
│       ├── java/com/rvdjv/pawnmc/
│       │   ├── MainActivity.kt       # Main UI & file handling
│       │   ├── SettingsActivity.kt   # Settings & configuration
│       │   ├── PawnCompiler.kt       # Native compiler wrapper
│       │   └── CompilerConfig.kt     # Settings persistence
│       └── res/
│           ├── layout/               # UI layouts
│           ├── menu/                 # Toolbar menus
│           └── values/               # Strings & themes
├── pawnc-3.10.7/                  # Pawn compiler 3.10.7 (submodule)
├── pawnc-3.10.11/                 # Pawn compiler 3.10.11 (submodule)
├── .github/workflows/             # CI/CD workflows
├── CONTRIBUTING.md                # Contribution guidelines
├── options.md                     # Compiler flags reference
└── README.md                      # This file
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
- `compile(String[] args)` - Compile and return combined output
- `getOutput()` - Get stdout buffer
- `getErrors()` - Get stderr buffer

## Technical Details

### Compilation Process
1. File path converted to directory for `-D` flag (sets working directory)
2. Custom flags and include paths added from settings
3. Compilation runs on dedicated thread with 8MB stack
4. Output buffered with limits:
   - Max 15 warnings (additional warnings truncated)
   - Max 24 errors (additional errors truncated)
   - Max 512KB total buffer size

### Thread Safety
- Mutex-protected output buffers
- Thread-safe file position tracking
- Separate thread for compilation to prevent UI blocking

### Storage Permissions
- Uses `MANAGE_EXTERNAL_STORAGE` for Android 11+
- Falls back to `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` for Android 10 and below
- Requests permissions at runtime

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on:
- Code style
- Commit conventions
- Pull request process
- Issue reporting

## Build Configuration

**Gradle:**
- compileSdk: 36
- minSdk: 24
- targetSdk: 36
- NDK Version: 29.0.14206865
- CMake Version: 3.22.1

**Dependencies:**
- AndroidX Core KTX
- AndroidX AppCompat
- Material Components
- Kotlin Coroutines
- AndroidX Lifecycle Runtime

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Pawn compiler by ITB CompuPhase and community
- SA-MP community for continued Pawn development

## Links

- [Compiler Flags Reference](options.md)
- [Contributing Guidelines](CONTRIBUTING.md)
- [Releases](https://github.com/novusr/Pawn-MC/releases)
- [Issues](https://github.com/novusr/Pawn-MC/issues)
