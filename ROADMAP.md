# PawnMC Roadmap & Architecture Overview

## 1. Project Summary

PawnMC is an Android application designed to simplify Pawn scripting workflows directly on mobile devices. The product is centered on native compilation, compiler configuration, project portability, and quick feedback for Pawn source files.

From a manual review of the repository, the current project focuses on:

| Area | Description |
|---|---|
| Pawn compilation | Compile Pawn source files directly on Android |
| Mobile workflow | Work without a desktop environment for most compile tasks |
| Compiler configuration | Control version, flags, debug modes, and include paths |
| Xed code editor | Built-in Sora-based editor with tabs, workspace explorer, and floating toolbars |
| Output feedback | Display compiler errors and build results clearly |
| Release maintenance | Check GitHub releases and prepare update/install flow |

---

## 2. Core Objectives

| Objective | Details |
|---|---|
| Native Pawn compile support | Run compiler logic natively using Android library support |
| Project portability | Keep source and config accessible from the mobile workspace |
| Flexible compiler setup | Let users change compiler version and compilation flags |
| Fast iteration | Display compile output and error details immediately |
| Mobile-first developer workflow | Designed for Android-oriented scripting and testing |

---

## 3. Current Features and Functional Scope

| Feature | Description |
|---|---|
| File selection | Open Pawn source files such as .pawn, .pwn, .p, and .inc |
| Recent file tracking | Remembers last selected file and last directory |
| Compiler options | Supports debug level, mandatory semicolons, parentheses checks, and custom flags |
| Include paths | Add custom include directories; trailing .pawn/.pwn/.p/.inc extensions are stripped automatically |
| Xed Editor | VS Code-style tab strip, floating workspace explorer, floating rectangle toolbar, jump-to-line, quick symbol bar |
| Workspace sessions | Up to 3 open folders, each with its own tree, opened editors and search hits |
| Workspace-wide tools | Find/replace across every file of a workspace, plus search-in-workspace |
| Localization | Indonesian and English strings served from `_dat/_extract.dat` |
| Editor appearance | Custom Xed editor background with 20 presets and a hex picker |
| Theme and language | Light/dark/system theme with an in-app language selector |
| Version selection | Switch between Pawn compiler versions |
| Native compilation | Execute Pawn compilation through the native layer |
| Output capture | Read compiler output and parse exit codes |
| Auto fallback | Retry using an alternate compiler version after repeated errors |
| Settings screen | Manage app configuration and compiler preferences |
| GitHub update check | Compare installed app version with latest release |
| APK installation flow | Download and prepare the latest APK for install |
| Self tests | Built-in self-test suite run from the main screen |

---

## 4. Current Repository Structure

### 4.1 Root Project Files

| File / Path | Role |
|---|---|
| README.md | Project overview, usage, installation, and build instructions |
| settings.gradle.kts | Gradle settings for project modules |
| build.gradle.kts | Root Gradle configuration |
| gradlew / gradlew.bat | Gradle wrapper binaries |
| gradle.properties | Global Gradle properties |
| LICENSE | License for the project |
| configuration/ | Documentation and project configuration guidance |
| compilers/ | Contains Pawn compiler binaries or packaged compiler versions |

### 4.2 Android App Module

| File / Path | Role |
|---|---|
| app/build.gradle.kts | Android app configuration, SDK, NDK, dependencies, versioning, `_dat` asset source |
| app/proguard-rules.pro | Release minification rules |
| app/src/main/AndroidManifest.xml | App permissions, activities, manifest entries, FileProvider |
| app/src/main/cpp/CMakeLists.txt | Native library build definition |
| app/src/main/cpp/Compiler.cpp | Native bridge for compiler interop |
| app/src/main/res/xml/file_paths.xml | FileProvider paths for APK install flow |
| _dat/_extract.dat | Single source of localisation data (id/en), packaged as an asset |

---

## 5. Source Files and Their Roles

### 5.1 Data Layer

| File / Path | Purpose |
|---|---|
| app/src/main/java/com/rvdjv/pawnmc/data/config/CompilerConfig.kt | Stores all compiler settings and app preferences in SharedPreferences; builds compile option lists; normalises include paths and strips script extensions |
| app/src/main/java/com/rvdjv/pawnmc/data/config/AppLocalization.kt | Loads and parses `_dat/_extract.dat`; no bundled copy of the strings |
| app/src/main/java/com/rvdjv/pawnmc/data/compiler/PawnCompiler.kt | Native compiler wrapper; compiles Pawn files, reads output, handles fallback strategy |
| app/src/main/java/com/rvdjv/pawnmc/data/update/UpdateManager.kt | GitHub release fetcher, semantic version comparison, APK download, install intent preparation |
| app/src/main/java/com/rvdjv/pawnmc/data/pawn/Registry.kt | Pawn keywords, directives, natives and forwards shared by completion and highlighting |

### 5.2 UI Layer

| File / Path | Purpose |
|---|---|
| app/src/main/java/com/rvdjv/pawnmc/ui/main/MainActivity.kt | Main app activity; opens MainScreen and launches settings |
| app/src/main/java/com/rvdjv/pawnmc/ui/main/MainScreen.kt | Primary compile UI, file browser handling, permission prompts, output logs |
| app/src/main/java/com/rvdjv/pawnmc/ui/main/MainViewModel.kt | State store for selected file, compile status, and output text |
| app/src/main/java/com/rvdjv/pawnmc/ui/settings/SettingsActivity.kt | Settings activity host |
| app/src/main/java/com/rvdjv/pawnmc/ui/settings/SettingsScreen.kt | Full settings UI for compiler options, app info, and update actions |
| app/src/main/java/com/rvdjv/pawnmc/ui/settings/SettingsViewModel.kt | Settings state handling and config updates |
| app/src/main/java/com/rvdjv/pawnmc/ui/filebrowser/FileBrowserDialog.kt | File/folder browser for selecting Pawn files or include directories |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedEditorActivity.kt | Hosts the Xed editor |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedEditorScreen.kt | Editor canvas, tab strip, floating compile/panel/toolbar, status bar, syntax palette |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedEditorViewModel.kt | Active file state, single-file save flow, owns the workspace session |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedWorkspaceViewModel.kt | Up to 3 workspaces with trees, open documents, search and replace |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedWorkspacePanel.kt | Floating workspace explorer: opened editors, folder tree, search hits |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedCodeEditor.kt | Thin wrapper around the Sora CodeEditor widget |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedToolPanel.kt | Search / replace panel for workspace-wide operations |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/pawn/PawnLanguage.kt | Pawn language implementation for the editor (completion, indent, symbols) |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/pawn/PawnManager.kt | Pawn analyzer feeding the syntax highlighter |
| app/src/main/java/com/rvdjv/pawnmc/ui/PawnIcons.kt | Hand-drawn vector icons: save, save-all, code edit, workspace, panel, folders, files, Wi-Fi signal |
| app/src/main/java/com/rvdjv/pawnmc/ui/theme/Color.kt | Colors for app theme |
| app/src/main/java/com/rvdjv/pawnmc/ui/theme/Theme.kt | Material3 theme configuration |
| app/src/main/java/com/rvdjv/pawnmc/ui/theme/Type.kt | Typography styles |

### 5.3 Tests

| File / Path | Purpose |
|---|---|
| app/src/test/java/com/rvdjv/pawnmc/data/update/UpdateManagerTest.kt | Regression tests for version comparison logic |
| app/src/test/java/com/rvdjv/pawnmc/data/config/AppLocalizationTest.kt | Localisation parsing and lookup regression tests |

---

## 6. Feature Map by Module

| Module | Key Files | Main Responsibility |
|---|---|---|
| App configuration | app/build.gradle.kts, gradle.properties | Build system, versioning, SDK setup |
| Native compiler | app/src/main/cpp/CMakeLists.txt, app/src/main/cpp/Compiler.cpp | JNI/native bridge and native compile support |
| Compiler config | app/src/main/java/com/rvdjv/pawnmc/data/config/CompilerConfig.kt | Persistent compile configuration |
| Compile engine | app/src/main/java/com/rvdjv/pawnmc/data/compiler/PawnCompiler.kt | Compile logic, output capture, fallback retry |
| Update flow | app/src/main/java/com/rvdjv/pawnmc/data/update/UpdateManager.kt | GitHub API interactions and APK update install pipeline |
| Main app UI | app/src/main/java/com/rvdjv/pawnmc/ui/main/MainScreen.kt | Main compile entry point |
| Editor UI | app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedEditorScreen.kt | Code canvas, tab strip, floating controls |
| Workspace model | app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedWorkspaceViewModel.kt | Folder trees, open documents, search/replace |
| Localization | app/src/main/java/com/rvdjv/pawnmc/data/config/AppLocalization.kt, _dat/_extract.dat | Single-source id/en strings |
| Settings UI | app/src/main/java/com/rvdjv/pawnmc/ui/settings/SettingsScreen.kt | App configuration and update controls |
| File browser | app/src/main/java/com/rvdjv/pawnmc/ui/filebrowser/FileBrowserDialog.kt | Storage browsing and file selection |

---

## 7. Major Workflows

### 7.1 App launch flow

| Step | Action |
|---|---|
| 1 | MainActivity starts |
| 2 | MainViewModel initializes |
| 3 | MainScreen is displayed |
| 4 | Recent file is restored if present |
| 5 | Storage permission check is performed |

### 7.2 File selection flow

| Step | Action |
|---|---|
| 1 | User selects a Pawn file from the file browser |
| 2 | File extension is validated |
| 3 | Selected path is stored in CompilerConfig |
| 4 | File is used as input for compile |

### 7.3 Compile flow

| Step | Action |
|---|---|
| 1 | MainViewModel.compileFile() runs |
| 2 | File validation and storage permission are checked |
| 3 | PawnCompiler.compile() is invoked |
| 4 | Compiler options are built from config |
| 5 | Native compile output is parsed |
| 6 | Output log and compile result are shown in UI |

### 7.4 Settings flow

| Step | Action |
|---|---|
| 1 | User opens Settings screen |
| 2 | Current config values are read from CompilerConfig |
| 3 | User updates compiler preferences |
| 4 | Preferences are saved to SharedPreferences |
| 5 | Restart may be required when switching compiler versions |

### 7.5 Update flow

| Step | Action |
|---|---|
| 1 | App checks GitHub releases latest endpoint |
| 2 | Version comparison logic compares installed and latest tags |
| 3 | If newer version is found, APK is downloaded |
| 4 | Android install permission is evaluated |
| 5 | APK install intent is started |

---

## 8. Manual Analysis and Engineering Notes

| Topic | Observation |
|---|---|
| Product focus | PawnMC is clearly a mobile-first Pawn compiler utility rather than a general-purpose IDE |
| Strength | Strong separation between UI, compiler logic, config, and native bridge |
| Risk area | Storage permissions are critical because the app reads and writes file-based compiler artifacts |
| Native lifecycle | PawnCompiler holds initialization state and version switching state, so restart logic matters |
| Update reliability | GitHub release checking and APK install flow require careful handling of network, invalid APKs, and Android install restrictions |
| Configuration integrity | CompilerConfig is central to compile behavior and must remain consistent with UI and native state |
| Include path hygiene | Only `.pawn`, `.pwn`, `.p` and `.inc` are treated as script extensions, and they are stripped from include paths and compile paths |
| Compose state in lists | The workspace tree must be remembered against `tree` and `collapsedPaths`, otherwise folder toggles never recompose |
| Single scrolling surface | The explorer keeps opened editors, tree and hits in one LazyColumn; competing scroll containers swallow taps |
| Single localisation source | `_dat/_extract.dat` is the only place strings live; AppLocalization must never duplicate them |
| Floating control geometry | Compile, workspace panel and toolbar share one geometry so they stack and centre by default |

---

## 9. Notable Implementation Details

| Detail | Explanation |
|---|---|
| Native library loading | System.loadLibrary is used with version-specific library names |
| Fallback retry logic | Additional compiler retry is triggered based on error threshold heuristics |
| Hidden version file | The update system stores the current app version in an app-private hidden file |
| FileProvider | Required for safe APK install via content URI |
| SharedPreferences | Used for persistent compiler settings and last-used paths |
| Include path normalisation | `normalizeIncludePathInput` strips a trailing script extension, so a path typed as a file still resolves as a folder |
| Compile path resolution | `resolveCompilePath` keeps a real file untouched and only strips an extension when nothing exists at the given path |
| Buffer synchronisation | The editor reload watcher compares document identity, path **and** content, and mirrors typing into the holder so the caret is never reset |
| Workspace registration | Files opened outside the editor are registered as the first tab of their folder workspace |

---

## 10. Recommended Development Priorities

| Priority | Recommendation |
|---|---|
| High | Stabilize GitHub update checking and APK installation flow |
| High | Improve network failure handling and APK validation |
| High | Review permission handling for Android storage access variations |
| Medium | Add stronger project folder browsing and multi-file management |
| Medium | Improve log export and compile result history |
| Medium | Extend workspace support: rename/move files, drag-and-drop in the explorer, richer replace previews |
| Low | Extend theme and app customization options (editor background presets, accent density) |
| Low | Keep localisation strings in sync in `_dat/_extract.dat` only, and add keys as the UI grows |

---

## 11. Conclusion

PawnMC is a mobile-first Android compiler tool for Pawn source files. The current codebase is structured around a clear separation of concerns: UI for user interaction, data/config for compiler behavior, and native C++ integration for actual compile execution. The repository already contains the essential pieces for a functional Pawn compilation workflow, and the update-related additions extend that workflow toward release management and self-updating installation.

The most relevant files to understand the project are:

| File / Path | Why it matters |
|---|---|
| README.md | Top-level overview |
| app/build.gradle.kts | App build and version metadata |
| app/src/main/AndroidManifest.xml | Permissions and install support |
| app/src/main/java/com/rvdjv/pawnmc/data/config/CompilerConfig.kt | Runtime compile settings |
| app/src/main/java/com/rvdjv/pawnmc/data/compiler/PawnCompiler.kt | Native compile engine |
| app/src/main/java/com/rvdjv/pawnmc/ui/main/MainScreen.kt | Main user workflow |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedEditorScreen.kt | Editor canvas, tab strip and floating controls |
| app/src/main/java/com/rvdjv/pawnmc/ui/editor/XedWorkspaceViewModel.kt | Workspace trees, open documents, search/replace |
| app/src/main/java/com/rvdjv/pawnmc/ui/settings/SettingsScreen.kt | Settings and update actions |
| app/src/main/java/com/rvdjv/pawnmc/data/update/UpdateManager.kt | Release update logic |
| app/src/main/cpp/Compiler.cpp | Native bridge implementation |
| _dat/_extract.dat | Localisation data for Indonesian and English |

---

This roadmap is based on a manual review of the current repository structure and source files available in the project at the time of analysis.
