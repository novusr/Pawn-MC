# PawnMC Roadmap & Architecture Overview

## 1. Project Summary

PawnMC is an Android application designed to simplify Pawn scripting workflows directly on mobile devices. The product is centered on native compilation, compiler configuration, project portability, and quick feedback for Pawn source files.

From a review of the repository, the current project focuses on:

| Area | Description |
|---|---|
| Pawn compilation | Compile Pawn source files directly on Android |
| Mobile workflow | Work without a desktop environment for most compile tasks |
| Compiler configuration | Control version, flags, debug modes, and include paths |
| Xed code editor | Built-in Sora-based editor with tabs, workspace explorer, floating toolbars, and an in-editor terminal portal |
| Output feedback | Display compiler errors and build results clearly, annotated with local explanations |
| AI terminal portal | `XedMCPortal` command line for docs, `pawncc`, and compiler version switching |
| Release maintenance | Check GitHub releases and prepare update/install flow |

Current app metadata (`app/build.gradle.kts`): `applicationId` `com.rvdjv.pawnmc`, `versionCode` 6, `versionName` 1.5.1, `minSdk` 24, `compileSdk`/`targetSdk` 36, NDK 29.0.14206865, CMake 3.22.1, ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`.

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
| SAF folder access | Workspace/include folders are granted through the system folder picker (`ACTION_OPEN_DOCUMENT_TREE`), started in Downloads |
| Recent file tracking | Remembers last selected file and last directory |
| Compiler options | Debug level, mandatory semicolons, parentheses checks, custom flags, forced compiler mode |
| Forced compiler mode | Disables nearby-compiler auto-detection and fallback switching |
| Nearby compiler detection | Finds a `pawncc` next to the source and reads its binary metadata/MD5 |
| Ignore-case conversion | Folder backup, lowercase file names, `#include` rewriting |
| Include paths | Add/edit/remove include directories; trailing script extensions are stripped automatically |
| Xed Editor | VS Code-style tab strip, floating workspace explorer, floating rectangle toolbar, jump-to-line, quick symbol bar |
| Workspace sessions | Up to 3 open folders, each with its own tree, opened editors, and search hits |
| Workspace-wide tools | Find/replace across every file of a workspace, plus search-in-workspace |
| Editor terminal portal | In-editor command line (`help`, `docs`, `pawncc`, `switch`, `clear`) with sandboxed source resolution inside the workspace root |
| Localization | Indonesian, English, Spanish (Argentina), and Russian strings served from `_dat/_dat_extract.dat` |
| Pawn symbol data | Keywords, natives, and forwards loaded from `_dat/_dat_internal.dat` (natives/forwards grouped by include) |
| Editor appearance | Custom Xed editor background with presets and a hex picker |
| Theme and language | Light/dark/system theme with an in-app language selector |
| Version selection | Switch between Pawn compiler versions 3.10.7 and 3.10.11 |
| Native compilation | Execute Pawn compilation through the native layer |
| Output capture | Read compiler output and parse exit codes and error counts |
| Output explanations | Local hints for Pawn warning/error codes, stored in `_dat/_dat_explain.dat` |
| Auto fallback | Retry using an alternate compiler version after repeated errors |
| Settings screen | Manage app configuration, include paths, and compiler preferences |
| GitHub update check | Compare installed app version with the latest release assets |
| APK installation flow | Download and prepare the latest APK for install |
| Self tests | Built-in in-app self-test suite (dedupe paths, nearby compiler, settings integration, ignore-case workflow) |
| CI | GitHub Actions running lint, debug build, and unit tests on push/PR |

---

## 4. Current Repository Structure

### 4.1 Root Project Files

| File / Path | Role |
|---|---|
| README.md | Project overview, usage, installation, and build instructions |
| ROADMAP.md | This architecture and feature overview |
| TODO.txt | Local working notes |
| COMMIT.txt | Local commit notes |
| settings.gradle.kts | Gradle settings for project modules |
| build.gradle.kts | Root Gradle configuration |
| gradle/libs.versions.toml | Version catalog (Android, Kotlin, Compose, Sora editor, JUnit, json) |
| gradlew / gradlew.bat, gradle/wrapper/* | Gradle wrapper |
| gradle.properties | Global Gradle properties |
| LICENSE | License for the project |
| .github/workflows/ci.yml | CI: lint + debug build + unit tests |
| .gitmodules | Compiler submodules `pawnc-3.10.11` and `pawnc-3.10.7` |
| configuration/ | Documentation and project configuration guidance |
| compilers/ | Pawn compiler sources as git submodules (3.10.11, 3.10.7) |
| _dat/ | All shipped data assets (see 4.3) |

### 4.2 Android App Module

| File / Path | Role |
|---|---|
| app/build.gradle.kts | Android config, SDK/NDK, signing, ABI filters, dependencies, `_dat` asset source, APK renaming |
| app/proguard-rules.pro | Release minification rules |
| app/src/main/AndroidManifest.xml | Permissions, activities, FileProvider |
| app/src/main/cpp/CMakeLists.txt | Native library build definition |
| app/src/main/cpp/Compiler.cpp | Native bridge, JNI exports for `Runner` |
| app/src/main/cpp/Compiler.h | Native compiler declarations |
| app/src/main/res/xml/file_paths.xml | FileProvider paths for the APK install flow |
| app/src/main/res/xml/backup_rules.xml, data_extraction_rules.xml | Backup/data extraction rules |
| app/src/main/res/values/strings.xml, themes.xml | Minimal resources (UI strings live in `_dat/_dat_extract.dat`) |
| app/src/main/res/mipmap-*, drawable/ic_launcher_* | Launcher icons |

### 4.3 Data Assets (`_dat/`, packaged via `assets.srcDir`)

| File | Purpose |
|---|---|
| _dat/_dat_extract.dat | Single source of localisation data (id/en/es), packaged as an asset |
| _dat/_dat_explain.dat | Compiler message explanations (`code` -> hint), packaged as an asset |
| _dat/_dat_internal.dat | Pawn symbol data: item descriptions plus natives/forwards grouped by include |
| _dat/_dat_simulation.dat | Localised strings for the editor terminal portal (`key.id` / `key.en`) |

---

## 5. Source Files and Their Roles

### 5.1 Data Layer

| File / Path | Purpose |
|---|---|
| data/config/CompilerConfig.kt | All compiler settings and app preferences in SharedPreferences; compile option lists; include path normalisation/dedupe; script extension handling |
| data/config/AppLocalization.kt | Loads and parses `_dat/_dat_extract.dat`; no bundled copy of the strings |
| data/compiler/Compiler.kt | Facade of the compiler layer; forwards the public API to the focused compiler helpers |
| data/compiler/CompilerRunner.kt | Legacy execution layer file; **not referenced by any caller** â€” the live entry is `Runner` |
| data/compiler/Runner.kt | Live execution layer: native library loading, compile execution, argument building, fallback retry, forced-mode handling |
| data/compiler/Names.kt | Shared compiler names, extensions, folder variants, flags, thresholds |
| data/compiler/Detection.kt | Nearby `pawncc` detection, binary metadata/MD5 reading, include path selection |
| data/compiler/CaseConversion.kt | "Ignore case" conversion: folder backup, lowercase names, `#include` rewriting |
| data/compiler/Explainer.kt | Annotates raw compiler output with local hints from `_dat/_dat_explain.dat` |
| data/compiler/Explanations.kt | Reads, parses and caches `_dat/_dat_explain.dat` |
| data/update/UpdateManager.kt | GitHub release fetcher, semantic version comparison, APK asset selection/download, install intent preparation |
| data/pawn/Registry.kt | Curated Pawn keywords, directives, natives, forwards; merges include-derived symbols for completion and highlighting |
| data/pawn/InternDat.kt | Parses `_dat/_dat_internal.dat` into items and per-include symbol groups (asset + filesystem fallbacks, cached) |
| data/pawn/Index.kt | `PawnIndex` expose of the parsed include groups for native/forward lookups |

### 5.2 UI Layer

| File / Path | Purpose |
|---|---|
| interface/main/MainActivity.kt | Main app activity; hosts `MainScreen` and `XedEditorScreen` directly (no separate editor activity) |
| interface/main/MainScreen.kt | Primary compile UI, file browser handling, permission prompts, output logs, self-test dialog and results card |
| interface/main/MainViewModel.kt | State store for selected file, compile status, and output text |
| interface/main/AppSelfTestRunner.kt | In-app self-test catalog and runners (dedupe paths, nearby compiler, settings integration, ignore-case workflow) |
| interface/settings/SettingsActivity.kt | Settings activity host |
| interface/settings/SettingsScreen.kt | Settings UI for compiler options, include paths, app info, update actions |
| interface/settings/SettingsViewModel.kt | Settings state handling, include path add/edit/remove with uniqueness checks |
| interface/filebrowser/FileBrowserDialog.kt | File/folder browser for selecting Pawn files or include directories |
| interface/editor/XedEditorScreen.kt | Editor canvas, tab strip, floating compile/panel/toolbar, status bar, syntax palette, terminal portal host |
| interface/editor/XedEditorViewModel.kt | Active file state, single-file save flow, owns the workspace session |
| interface/editor/XedWorkspaceViewModel.kt | Up to 3 workspaces with trees, open documents, search and replace |
| interface/editor/XedWorkspacePanel.kt | Floating workspace explorer: opened editors, folder tree, search hits |
| interface/editor/WorkspaceFolderPicker.kt | Builds `ACTION_OPEN_DOCUMENT_TREE` intents (Downloads start location) and resolves a tree URI to a real `File` |
| interface/editor/XedMCPortal.kt | Editor terminal: command tokenizer, `pawncc` invocation sandboxing, `switch` suggestions, `_dat_simulation.dat` lookup, and the floating portal UI |
| interface/editor/XedToolPanel.kt | Search / replace panel for workspace-wide operations |
| interface/editor/XedCodeEditor.kt | Thin wrapper around the Sora CodeEditor widget |
| interface/editor/pawn/PawnLanguage.kt | Pawn language implementation for the editor (completion, indent, symbols) |
| interface/editor/pawn/PawnManager.kt | Pawn analyzer feeding the syntax highlighter |
| interface/editor/pawn/PawnSourceSymbols.kt | Extracts function declarations from the current source (comment/string masked) for local completion |
| interface/PawnIcons.kt | Hand-drawn vector icons: save, save-all, code edit, workspace, panel, folders, files, Wi-Fi signal |
| interface/theme/Color.kt | Colors for app theme |
| interface/theme/Theme.kt | Material3 theme configuration |
| interface/theme/Type.kt | Typography styles |

### 5.3 Tests

| File / Path | Purpose |
|---|---|
| app/src/test/.../data/update/UpdateManagerTest.kt | Version comparison / update regression tests |
| app/src/test/.../data/config/AppLocalizationTest.kt | Localisation parsing and lookup regression tests |
| app/src/test/.../data/config/CompilerConfigTest.kt | Include path normalisation, dedupe, path resolution |
| app/src/test/.../data/compiler/ExplanationsTest.kt | `_dat/_dat_explain.dat` parsing regression tests |
| app/src/test/.../data/compiler/SuccessTest.kt | Successful compile path expectations |
| app/src/test/.../data/compiler/FailTest.kt | Failing compile path expectations |
| app/src/test/.../data/compiler/IgnoreCaseWorkflowTest.kt | Ignore-case conversion workflow regression tests |
| app/src/test/.../interface/editor/XedEditorTest.kt | Editor symbol/completion parsing, `_dat_internal.dat` descriptions |
| app/src/test/.../ExampleUnitTest.kt | Template unit test |
| app/src/androidTest/.../ExampleInstrumentedTest.kt | Template instrumented test |

---

## 6. Feature Map by Module

| Module | Key Files | Main Responsibility |
|---|---|---|
| App configuration | app/build.gradle.kts, gradle/libs.versions.toml | Build system, versioning, SDK/NDK setup |
| Native compiler | cpp/CMakeLists.txt, cpp/Compiler.cpp, cpp/Compiler.h | JNI bridge (`Runner_compile/getOutput/getErrors`) and native compile support |
| Compiler config | data/config/CompilerConfig.kt | Persistent compile configuration |
| Compile engine | data/compiler/Compiler.kt, Runner.kt, CompilerRunner.kt (legacy) | Compile logic, output capture, fallback retry |
| Pawn symbol data | data/pawn/Registry.kt, InternDat.kt, Index.kt | Completion and highlighting data from `_dat/_dat_internal.dat` |
| Update flow | data/update/UpdateManager.kt | GitHub API interactions and APK update install pipeline |
| Main app UI | interface/main/MainScreen.kt | Main compile entry point, self tests |
| Editor UI | interface/editor/XedEditorScreen.kt | Code canvas, tab strip, floating controls |
| Editor terminal | interface/editor/XedMCPortal.kt | Command line, `pawncc` sandboxed invocation, version switch |
| Workspace model | interface/editor/XedWorkspaceViewModel.kt | Folder trees, open documents, search/replace |
| SAF access | interface/editor/WorkspaceFolderPicker.kt | System folder picker intent and tree-URI to `File` resolution |
| Localization | data/config/AppLocalization.kt, _dat/_dat_extract.dat | Single-source id/en/es strings |
| Compiler explanations | data/compiler/Explanations.kt, _dat/_dat_explain.dat | Single-source hints for Pawn compiler messages |
| Settings UI | interface/settings/SettingsScreen.kt | App configuration and update controls |
| File browser | interface/filebrowser/FileBrowserDialog.kt | Storage browsing and file selection |
| CI | .github/workflows/ci.yml | Lint, debug build, unit tests, artifact upload |

---

## 7. Major Workflows

### 7.1 App launch flow

| Step | Action |
|---|---|
| 1 | MainActivity starts |
| 2 | MainViewModel and editor ViewModels initialize |
| 3 | MainScreen is displayed |
| 4 | Recent file is restored if present |
| 5 | Storage permission check is performed |

### 7.2 File/folder selection flow

| Step | Action |
|---|---|
| 1 | User triggers the file browser or the system folder picker |
| 2 | File extension (`.pawn`, `.pwn`, `.p`, `.inc`) or folder URI is validated |
| 3 | Tree URI is resolved to a real `File` path when required |
| 4 | Selected path is stored in CompilerConfig / workspace session |
| 5 | File is used as input for compile |

### 7.3 Compile flow

| Step | Action |
|---|---|
| 1 | `MainViewModel.compileFile()` runs (or `pawncc <file> [opts]` in the terminal portal) |
| 2 | File validation and access are checked |
| 3 | `Compiler.compile()` is invoked; version chosen by forced mode or nearby detection |
| 4 | Compiler options are built from config |
| 5 | Native compile output is parsed (exit code, error count) |
| 6 | Output is annotated via `Explainer` with `_dat/_dat_explain.dat` hints |
| 7 | Output log and compile result are shown in UI |
| 8 | If errors repeat and fallback is allowed, compile is retried on the other version |

### 7.4 Settings flow

| Step | Action |
|---|---|
| 1 | User opens Settings screen |
| 2 | Current config values are read from CompilerConfig |
| 3 | User updates compiler preferences and include paths (uniqueness enforced) |
| 4 | Preferences are saved to SharedPreferences |
| 5 | Restart may be required when switching compiler versions |

### 7.5 Update flow

| Step | Action |
|---|---|
| 1 | App checks the GitHub releases latest endpoint |
| 2 | A matching APK asset is selected and version comparison runs |
| 3 | If newer, the APK is downloaded |
| 4 | Android install permission is evaluated |
| 5 | APK install intent is started through the FileProvider |

---

## 8. Manual Analysis and Engineering Notes

| Topic | Observation |
|---|---|
| Product focus | PawnMC is clearly a mobile-first Pawn compiler utility rather than a general-purpose IDE |
| Strength | Strong separation between UI, compiler logic, config, and native bridge |
| Risk area | Storage access is critical because the app reads and writes file-based compiler artifacts; modern Android requires SAF tree URIs for folder access |
| Native lifecycle | `Runner` holds initialization state, initialised version, and one-shot fallback mutation, so restart/session reset logic matters |
| Update reliability | GitHub release checking and APK install flow require careful handling of network, invalid APKs, and Android install restrictions |
| Configuration integrity | CompilerConfig is central to compile behaviour and must stay consistent with UI and native state |
| Include path hygiene | Only `.pawn`, `.pwn`, `.p` and `.inc` are treated as script extensions and stripped from include/compile paths |
| Compose state in lists | The workspace tree must be remembered against `tree` and `collapsedPaths`, otherwise folder toggles never recompose |
| Single scrolling surface | The explorer keeps opened editors, tree and hits in one LazyColumn; competing scroll containers swallow taps |
| Single localisation source | `_dat/_dat_extract.dat` is the only place UI strings live; `AppLocalization` must never duplicate them |
| Single explanation source | `_dat/_dat_explain.dat` is the only place compiler message hints live; `Explanations` parses it once per process |
| Single symbol source | Pawn keywords/natives/forwards come from `_dat/_dat_internal.dat` via `InternDat`/`PawnIndex`; `Registry` only curates and merges |
| Terminal portal data | Portal text comes from `_dat/_dat_simulation.dat` with per-language `key.id` / `key.en` / `key.es` lookup and English fallback |
| Assets packaging | `assets.srcDir(rootProject.file("_dat"))` ships the whole folder, so all four `_dat_*.dat` files reach the app; JVM tests fall back to reading `_dat/...` from the filesystem |
| Compiler layer split | `Compiler` stays a thin facade while implementation is split by single responsibility |
| Dead file | `data/compiler/CompilerRunner.kt` duplicates `Runner.kt` and has no references; it should be deleted to avoid two divergent execution layers |
| Naming inside a package | Files in `data/compiler` drop the redundant `Compiler` prefix because the package already states it |
| JNI symbol names follow the owner | Native `compile`/`getOutput`/`getErrors` live in `Runner`, so JNI names are `..._data_compiler_Runner_*` and ProGuard keeps that class |
| Editor host | The editor is a composable inside `MainActivity`, not a separate activity, so editor and main state share one lifecycle |
| Terminal sandboxing | `XedTerminalCommandParser.pawnccInvocation` canonicalises the source and rejects anything outside the workspace root or with a non-script extension |
| Build/CI contract | CI builds with JDK 17, `platforms;android-36`, NDK 29.0.14206865, CMake 3.22.1 and recursive submodules; release builds are minified and shrink resources |
| Release signing | Signing is optional and driven by `local.properties` credentials plus `release-key.jks`; without them the release build falls back to unsigned |

---

## 9. Notable Implementation Details

| Detail | Explanation |
|---|---|
| Native library loading | `System.loadLibrary` with version-specific library names |
| Fallback retry logic | Additional compiler retry triggered by error-count heuristics and guarded by forced mode |
| Forced compiler mode | When enabled, auto-detection and fallback switching are skipped and the selected version is used |
| Hidden version file | The update system stores the current app version in an app-private hidden file |
| FileProvider | Required for safe APK install via content URI |
| SharedPreferences | Used for persistent compiler settings and last-used paths |
| Include path normalisation | `normalizeIncludePathInput` strips a trailing script extension, so a path typed as a file still resolves as a folder |
| Compile path resolution | `resolveCompilePath` keeps a real file untouched and only strips an extension when nothing exists at the given path |
| Buffer synchronisation | The editor reload watcher compares document identity, path **and** content, and mirrors typing into the holder so the caret is never reset |
| Workspace registration | Files opened outside the editor are registered as the first tab of their folder workspace |
| Folder picker | `buildOpenFolderIntent` adds `EXTRA_INITIAL_URI` for Downloads on API 26+ because `OpenDocumentTree` cannot preset a location |
| APK output naming | `assemble`/`package` tasks rename outputs to `pawnmc-<version>-<variant>.apk` |
| In-app diagnostics | `AppSelfTestCatalog` exposes per-test and run-all entries surfaced in a dialog and a results card on the main screen |

---

## 10. Recommended Development Priorities

| Priority | Recommendation |
|---|---|
| High | Delete or consolidate the unused `CompilerRunner.kt` so only one execution layer exists |
| High | Stabilize GitHub update checking and APK installation flow |
| High | Improve network failure handling and APK validation |
| High | Keep SAF/tree-URI handling correct across Android versions, since folder access is permission-sensitive |
| Medium | Extend the terminal portal (`docs` content source, richer `switch` handling, output history) |
| Medium | Improve log export and compile result history |
| Medium | Extend workspace support: rename/move files, drag-and-drop in the explorer, richer replace previews |
| Medium | Grow `_dat/_dat_internal.dat` symbol coverage and add tests guarding the data-file parsers |
| Low | Extend theme and app customisation options (editor background presets, accent density) |
| Low | Keep localisation strings in sync in `_dat/_dat_extract.dat` only, and add keys as the UI grows |
| Low | Extend `_dat/_dat_explain.dat` with more Pawn message codes and keep it as the only place hints are maintained |

---

## 11. Conclusion

PawnMC is a mobile-first Android compiler tool for Pawn source files. The codebase is structured around a clear separation of concerns: Compose UI for interaction, `data/` for config, compiler, symbols and updates, and a C++ layer for actual compile execution. The editor (Xed) now carries a full workspace model plus an in-editor terminal portal, and all textual/symbol data lives outside Kotlin sources in `_dat/`, packaged as assets.

The most relevant files to understand the project are:

| File / Path | Why it matters |
|---|---|
| README.md | Top-level overview |
| .github/workflows/ci.yml | Build/test contract |
| app/build.gradle.kts | App build and version metadata |
| app/src/main/AndroidManifest.xml | Permissions and install support |
| app/src/main/java/com/rvdjv/pawnmc/data/config/CompilerConfig.kt | Runtime compile settings |
| app/src/main/java/com/rvdjv/pawnmc/data/compiler/Compiler.kt | Public compile engine entry point (facade) |
| app/src/main/java/com/rvdjv/pawnmc/data/compiler/Runner.kt | Live native execution and fallback logic |
| app/src/main/java/com/rvdjv/pawnmc/data/pawn/InternDat.kt | Pawn symbol data loading |
| app/src/main/java/com/rvdjv/pawnmc/interface/main/MainScreen.kt | Main user workflow |
| app/src/main/java/com/rvdjv/pawnmc/interface/main/AppSelfTestRunner.kt | In-app diagnostics |
| app/src/main/java/com/rvdjv/pawnmc/interface/editor/XedEditorScreen.kt | Editor canvas, tab strip, floating controls |
| app/src/main/java/com/rvdjv/pawnmc/interface/editor/XedWorkspaceViewModel.kt | Workspace trees, open documents, search/replace |
| app/src/main/java/com/rvdjv/pawnmc/interface/editor/XedMCPortal.kt | Terminal portal, `pawncc` sandbox, simulation strings |
| app/src/main/java/com/rvdjv/pawnmc/interface/settings/SettingsScreen.kt | Settings and update actions |
| app/src/main/java/com/rvdjv/pawnmc/data/update/UpdateManager.kt | Release update logic |
| app/src/main/cpp/Compiler.cpp | Native bridge implementation |
| _dat/_dat_extract.dat | Localisation data for Indonesian, English, Spanish (Argentina), and Russian |
| _dat/_dat_explain.dat | Local explanations of Pawn compiler messages |
| _dat/_dat_internal.dat | Pawn natives/forwards/descriptions |
| _dat/_dat_simulation.dat | Terminal portal strings |

---

This roadmap reflects a review of the current repository structure and source files available in the project at the time of analysis.