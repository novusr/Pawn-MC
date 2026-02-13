# Contributing to PawnMC

Thank you for your interest in contributing to PawnMC! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Environment](#development-environment)
- [Project Structure](#project-structure)
- [Building the Project](#building-the-project)
- [Coding Standards](#coding-standards)
- [Submitting Changes](#submitting-changes)
- [Reporting Issues](#reporting-issues)

## Getting Started

### Prerequisites

- **Android Studio** Ladybug or newer (recommended)
- **Android SDK** with API Level 36
- **Android NDK** version 29.0.14206865
- **CMake** version 3.22.1
- **JDK 11** or higher
- **Git** with submodule support

### Cloning the Repository

```bash
# Clone with submodules (required for Pawn compiler sources)
git clone --recurse-submodules https://github.com/novusr/Pawn-MC.git
cd Pawn-MC

# If you already cloned without submodules:
git submodule update --init --recursive
```

## Development Environment

### Android Studio Setup

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the cloned `Pawn-MC` directory
4. Wait for Gradle sync to complete
5. Ensure NDK 29.0.14206865 is installed via SDK Manager

### SDK Configuration

Create a `local.properties` file in the project root (if not exists):

```properties
sdk.dir=/path/to/your/Android/Sdk
```

## Project Structure

```
Pawn-MC/
├── app/
│   ├── build.gradle.kts          # App-level build config
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt    # Native build configuration
│       │   └── native-lib.cpp    # JNI bridge & compiler integration
│       ├── java/com/rvdjv/pawnmc/
│       │   ├── MainActivity.kt     # Main UI & file handling
│       │   ├── SettingsActivity.kt # Compiler settings UI
│       │   ├── PawnCompiler.kt     # Native wrapper class
│       │   └── CompilerConfig.kt   # Settings storage (SharedPreferences)
│       ├── res/                    # Android resources (layouts, strings, etc.)
│       └── AndroidManifest.xml     # App manifest
├── pawnc-3.10.7/                   # Pawn compiler 3.10.7 source (submodule)
├── pawnc-3.10.11/                  # Pawn compiler 3.10.11 source (submodule)
├── gradle/                         # Gradle wrapper & version catalog
├── build.gradle.kts                # Project-level build config
└── settings.gradle.kts             # Project settings
```

### Key Components

| Component | Language | Description |
|-----------|----------|-------------|
| `native-lib.cpp` | C++ | JNI bridge that interfaces with Pawn compiler |
| `PawnCompiler.kt` | Kotlin | Kotlin wrapper for native functions |
| `MainActivity.kt` | Kotlin | Main UI, file selection, and compilation logic |
| `SettingsActivity.kt` | Kotlin | Settings UI for compiler options |
| `CompilerConfig.kt` | Kotlin | Persists compiler settings using SharedPreferences |

## Building the Project

### Debug Build

```bash
./gradlew assembleDebug
```

The debug APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

```bash
./gradlew assembleRelease
```

> **Note:** Release builds require signing configuration in `local.properties`:
> ```properties
> RELEASE_STORE_PASSWORD=your_store_password
> RELEASE_KEY_PASSWORD=your_key_password
> ```

### Installing on Device

```bash
# Debug
./gradlew installDebug

# Release
./gradlew installRelease
```

### Clean Build

```bash
./gradlew clean
```

## Coding Standards

### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public functions and classes
- Use coroutines for async operations (avoid blocking main thread)

### C++ (Native Code)

- Follow existing code style in `native-lib.cpp`
- Use C++17 features where appropriate
- Handle memory allocation carefully (avoid leaks)
- Log errors using Android's `__android_log_print`

### XML (Layouts/Resources)

- Use consistent indentation (4 spaces)
- Define reusable styles in `styles.xml`
- Use string resources for user-facing text

## Submitting Changes

### Branch Naming

- `feature/description` - New features
- `fix/description` - Bug fixes
- `refactor/description` - Code refactoring
- `docs/description` - Documentation updates

### Pull Request Process

1. **Fork** the repository
2. **Create** a feature branch from `main`
3. **Make** your changes following the coding standards
4. **Test** your changes thoroughly on an actual device or emulator
5. **Commit** with clear, descriptive messages:
   ```
   type: brief description

   Longer explanation if needed.
   ```
   Types: `feat`, `fix`, `refactor`, `docs`, `style`, `test`, `chore`
6. **Push** to your fork
7. **Open** a Pull Request with:
   - Clear title describing the change
   - Description of what was changed and why
   - Screenshots/recordings for UI changes
   - Reference to related issues (if any)

### Commit Message Examples

```
feat: add support for compiler flags customization

fix: resolve crash when compiling large scripts

refactor: simplify native-lib output buffering logic

docs: update README with build instructions
```

## Reporting Issues

### Before Reporting

- Check existing issues to avoid duplicates
- Ensure you're using the latest version
- Try a clean build (`./gradlew clean assembleDebug`)

### Issue Template

```markdown
**Description**
A clear description of the issue.

**Steps to Reproduce**
1. Open app
2. Select file...
3. Click compile...
4. See error

**Expected Behavior**
What you expected to happen.

**Actual Behavior**
What actually happened.

**Environment**
- Android Version: (e.g., Android 13)
- Device: (e.g., Samsung Galaxy S21)
- App Version: (e.g., 1.2.7)
- Compiler Version: (3.10.7 or 3.10.11)

**Logs/Screenshots**
Attach any relevant logs or screenshots.
```

## Architecture Notes

### Native Compilation Flow

1. User selects `.pwn` file via `MainActivity`
2. Compiler arguments are built from `CompilerConfig`
3. `PawnCompiler.compileWithOutput()` invokes native function
4. Native `pc_compile()` runs in separate thread (8MB stack)
5. Output/errors captured and returned to Kotlin
6. Results displayed in UI

### Compiler Versions

The app supports two Pawn compiler versions:
- **3.10.7** - Stable
- **3.10.11** - Newer

Switching requires app restart due to native library loading.

---

If you have questions, feel free to open a discussion or issue. Happy contributing!
