PawnMC Roadmap & Architecture Overview
======================================


1. Project Summary
------------------

PawnMC is an Android application designed to simplify Pawn scripting workflows directly on mobile devices. The product is centered on native compilation, compiler configuration, project portability, and quick feedback for Pawn source files.

From a review of the repository, the current project focuses on:


.. list-table::
   :header-rows: 1
   :widths: 22 46

   * - Area
     - Description
   * - Pawn compilation
     - Compile Pawn source files directly on Android
   * - Mobile workflow
     - Work without a desktop environment for most compile tasks
   * - Compiler configuration
     - Control version, flags, debug modes, and include paths
   * - Editor (PawnMC)
     - Built-in Sora-based editor with tabs, workspace explorer, floating toolbars, Save / Save As / Save All modes, and an in-editor terminal portal
   * - Output feedback
     - Display compiler errors and build results clearly, annotated with local explanations
   * - Developer Portal
     - ``DeveloperPortal`` command line for docs, ``pawncc``, and compiler version switching
   * - Sandbox terminal
     - Full Ubuntu shell in the editor via PRoot, opened from the editor's sandbox button
   * - Release maintenance
     - Check GitHub releases and prepare update/install flow


Current app metadata (``app/build.gradle.kts``): ``applicationId`` ``com.rvdjv.pawnmc``, ``versionCode`` 6, ``versionName`` 1.5.1, ``minSdk`` 24, ``compileSdk``/``targetSdk`` 36, NDK 29.0.14206865, CMake 3.22.1, ABIs ``arm64-v8a``, ``armeabi-v7a``, ``x86_64``.




2. Core Objectives
------------------


.. list-table::
   :header-rows: 1
   :widths: 31 46

   * - Objective
     - Details
   * - Native Pawn compile support
     - Run compiler logic natively using Android library support
   * - Project portability
     - Keep source and config accessible from the mobile workspace
   * - Flexible compiler setup
     - Let users change compiler version and compilation flags
   * - Fast iteration
     - Display compile output and error details immediately
   * - Mobile-first developer workflow
     - Designed for Android-oriented scripting and testing





3. Current Features and Functional Scope
----------------------------------------


.. list-table::
   :header-rows: 1
   :widths: 25 46

   * - Feature
     - Description
   * - File selection
     - Open Pawn source files such as .pawn, .pwn, .p, and .inc
   * - SAF folder access
     - Workspace/include folders are granted through the system folder picker (``ACTION_OPEN_DOCUMENT_TREE``), started in Downloads
   * - Recent file tracking
     - Remembers last selected file and last directory
   * - Compiler options
     - Debug level, mandatory semicolons, parentheses checks, custom flags, forced compiler mode
   * - Forced compiler mode
     - Disables nearby-compiler auto-detection and fallback switching
   * - Nearby compiler detection
     - Finds a ``pawncc`` next to the source and reads its binary metadata/MD5
   * - Ignore-case conversion
     - Folder backup, lowercase file names, ``#include`` rewriting
   * - Include paths
     - Add/edit/remove include directories; trailing script extensions are stripped automatically
   * - Editor
     - VS Code-style tab strip, floating workspace explorer, floating rectangle toolbar, jump-to-line, quick symbol bar; standalone mode offers Save As while an explicitly opened folder workspace offers Save All
   * - Workspace sessions
     - Up to 3 open folders, each with its own tree, opened editors, and search hits
   * - Workspace-wide tools
     - Find/replace across every file of a workspace, plus search-in-workspace
   * - Editor terminal portal
     - In-editor command line (``help``, ``docs``, ``pawncc``, ``switch``, ``clear``) with sandboxed source resolution inside the workspace root
   * - Sandbox terminal
     - Real Ubuntu shell (PRoot + Termux VT emulator) opened from the editor's sandbox button; rootfs downloaded once, then fully offline
   * - Localization
     - Indonesian, English, Spanish (Argentina), and Russian strings served from ``data/_data_2026_ect.toml``
   * - Pawn symbol data
     - Keywords, natives, and forwards loaded from ``data/_data_2026_intern.toml`` (natives/forwards grouped by include)
   * - Editor appearance
     - Custom editor canvas background with presets and a hex picker
   * - Theme and language
     - Light/dark/system theme with an in-app language selector
   * - Version selection
     - Switch between Pawn compiler versions 3.10.7 and 3.10.11
   * - Native compilation
     - Execute Pawn compilation through the native layer
   * - Output capture
     - Read compiler output and parse exit codes and error counts
   * - Output explanations
     - Local hints for Pawn warning/error codes, stored in ``data/_data_2026_exp.toml``
   * - Auto fallback
     - Retry using an alternate compiler version after repeated errors
   * - Settings screen
     - Manage app configuration, include paths, and compiler preferences
   * - GitHub update check
     - Compare installed app version with the latest release assets
   * - APK installation flow
     - Download and prepare the latest APK for install
   * - Self tests
     - Built-in in-app self-test suite (dedupe paths, nearby compiler, settings integration, ignore-case workflow)
   * - CI
     - GitHub Actions running lint, debug build, and unit tests on push/PR





4. Current Repository Structure
-------------------------------

4.1 Root Project Files
----------------------


.. list-table::
   :header-rows: 1
   :widths: 39 46

   * - File / Path
     - Role
   * - README.rst
     - Project overview, usage, installation, and build instructions
   * - ROADMAP.rst
     - This architecture and feature overview
   * - TODO.txt
     - Local working notes
   * - COMMIT.txt
     - Local commit notes
   * - settings.gradle.kts
     - Gradle settings for project modules
   * - build.gradle.kts
     - Root Gradle configuration
   * - gradle/libs.versions.toml
     - Version catalog (Android, Kotlin, Compose, Sora editor, JUnit, json)
   * - gradlew / gradlew.bat, gradle/wrapper/*
     - Gradle wrapper
   * - gradle.properties
     - Global Gradle properties
   * - LICENSE
     - License for the project
   * - .github/workflows/ci.yml
     - CI: lint + debug build + unit tests
   * - .gitmodules
     - Compiler submodules ``pawnc-3.10.11`` and ``pawnc-3.10.7``
   * - configuration/
     - Documentation and project configuration guidance
   * - compilers/
     - Pawn compiler sources as git submodules (3.10.11, 3.10.7)
   * - terminal/
     - Terminal feature ported from the Xed editor: ``emulator/``, ``view/``, ``proot/``, ``link2symlink/``, ``app/``
   * - data/
     - All shipped data assets (see 4.3)


4.2 Android App Module
----------------------


.. list-table::
   :header-rows: 1
   :widths: 46 46

   * - File / Path
     - Role
   * - app/build.gradle.kts
     - Android config, SDK/NDK, signing, ABI filters, dependencies, ``data`` asset source, APK renaming
   * - app/proguard-rules.pro
     - Release minification rules
   * - app/src/main/AndroidManifest.xml
     - Permissions, activities, FileProvider
   * - app/src/main/cpp/CMakeLists.txt
     - Native library build definition
   * - app/src/main/cpp/Compiler.cpp
     - Native bridge, JNI exports for ``Runner``
   * - app/src/main/cpp/Compiler.h
     - Native compiler declarations
   * - app/src/main/res/xml/file_paths.xml
     - FileProvider paths for the APK install flow
   * - app/src/main/res/xml/backup_rules.xml, data_extraction_rules.xml
     - Backup/data extraction rules
   * - app/src/main/res/values/strings.xml, themes.xml
     - Minimal resources (UI strings live in ``data/_data_2026_ect.toml``)
   * - app/src/main/res/mipmap-*, drawable/ic_launcher_*
     - Launcher icons


4.3 Data Assets (`data/`, packaged via `assets.srcDir`)
-------------------------------------------------------


.. list-table::
   :header-rows: 1
   :widths: 27 46

   * - File
     - Purpose
   * - data/_data_2026_ect.toml
     - Single source of localisation data (id/en/es), packaged as an asset
   * - data/_data_2026_exp.toml
     - Compiler message explanations (``code`` -> hint), packaged as an asset
   * - data/_data_2026_intern.toml
     - Pawn symbol data: item descriptions plus natives/forwards grouped by include
   * - data/_data_2026_term.toml
     - Localised strings for the editor terminal portal (``key.id`` / ``key.en``)





5. Source Files and Their Roles
-------------------------------

5.1 Data Layer
--------------


.. list-table::
   :header-rows: 1
   :widths: 31 46

   * - File / Path
     - Purpose
   * - data/config/CompilerConfig.kt
     - All compiler settings and app preferences in SharedPreferences; compile option lists; include path normalisation/dedupe; script extension handling
   * - data/config/TomlData.kt
     - Minimal TOML reader shared by every 2026 data file: tables, dotted/quoted keys, string leaves, inline tables
   * - data/config/AppLocalization.kt
     - Loads and parses ``data/_data_2026_ect.toml``; no bundled copy of the strings
   * - data/compiler/Compiler.kt
     - Facade of the compiler layer; forwards the public API to the focused compiler helpers
   * - data/compiler/CompilerRunner.kt
     - Legacy execution layer file; **not referenced by any caller** â€” the live entry is ``Runner``
   * - data/compiler/Runner.kt
     - Live execution layer: native library loading, compile execution, argument building, fallback retry, forced-mode handling
   * - data/compiler/Names.kt
     - Shared compiler names, extensions, folder variants, flags, thresholds
   * - data/compiler/Detection.kt
     - Nearby ``pawncc`` detection, binary metadata/MD5 reading, include path selection
   * - data/compiler/CaseConversion.kt
     - "Ignore case" conversion: folder backup, lowercase names, ``#include`` rewriting
   * - data/compiler/Explainer.kt
     - Annotates raw compiler output with local hints from ``data/_data_2026_exp.toml``
   * - data/compiler/Explanations.kt
     - Reads, parses and caches ``data/_data_2026_exp.toml``
   * - data/update/UpdateManager.kt
     - GitHub release fetcher, semantic version comparison, APK asset selection/download, install intent preparation
   * - data/syntax/Registry.kt
     - Curated Pawn keywords, directives, natives, forwards; merges include-derived symbols for completion and highlighting
   * - data/syntax/InternDat.kt
     - Parses ``data/_data_2026_intern.toml`` into items and per-include symbol groups (asset + filesystem fallbacks, cached)
   * - data/syntax/Index.kt
     - ``PawnIndex`` expose of the parsed include groups for native/forward lookups


5.2 UI Layer
------------


.. list-table::
   :header-rows: 1
   :widths: 42 46

   * - File / Path
     - Purpose
   * - interface/main/MainActivity.kt
     - Main app activity; hosts ``MainScreen`` and ``EditorScreen`` directly (no separate editor activity)
   * - interface/main/MainScreen.kt
     - Primary compile UI, file browser handling, permission prompts, output logs, self-test dialog and results card
   * - interface/main/MainViewModel.kt
     - State store for selected file, compile status, and output text
   * - interface/main/AppSelfTestRunner.kt
     - In-app self-test catalog and runners (dedupe paths, nearby compiler, settings integration, ignore-case workflow)
   * - interface/settings/Activity.kt
     - Settings activity host
   * - interface/settings/Screen.kt
     - Settings UI for compiler options, include paths, app info, update actions
   * - interface/settings/ViewModel.kt
     - Settings state handling, include path add/edit/remove with uniqueness checks
   * - interface/filebrowser/FileBrowserDialog.kt
     - File/folder browser for selecting Pawn files or include directories
   * - interface/editor/xedapi/EditorScreen.kt
     - Editor canvas, tab strip, floating compile/panel/toolbar, status bar, syntax palette, terminal portal host
   * - interface/editor/xedapi/EditorViewModel.kt
     - Active file state, standalone single-file save flow, owns the workspace session; main-screen Browse File selection is attached to this model when Xed opens
   * - interface/editor/Workspace/Session.kt
     - Up to 3 explicitly opened folder workspaces with trees, open documents, search and replace; workspace folders persist in ``CompilerConfig`` and are restored on launch
   * - interface/editor/Workspace/Panel.kt
     - Floating workspace explorer: opened editors, folder tree, search hits, in-panel folder picker
   * - interface/editor/Workspace/FolderPicker.kt
     - Builds ``ACTION_OPEN_DOCUMENT_TREE`` intents (Downloads start location) and resolves a tree URI to a real ``File``
   * - interface/editor/portal/DeveloperPortal.kt
     - Editor terminal: command tokenizer, ``pawncc`` invocation sandboxing, ``switch`` suggestions, ``_data_2026_term.toml`` lookup, and the floating portal UI; runs ``docs`` automatically on first open so the console is never empty
   * - interface/editor/xedapi/ToolPanel.kt
     - Search / replace panel for workspace-wide operations
   * - interface/editor/xedapi/CodeEditor.kt
     - Thin wrapper around the Sora CodeEditor widget
   * - interface/editor/syntax/Language.kt
     - Pawn language implementation for the editor (completion, indent, symbols)
   * - interface/editor/syntax/Manager.kt
     - Pawn analyzer feeding the syntax highlighter
   * - interface/editor/syntax/SourceSym.kt
     - Extracts function declarations from the current source (comment/string masked) for local completion
   * - interface/Icons.kt
     - Hand-drawn vector icons: save, save-all, code edit, workspace, panel, folders, files, Wi-Fi signal
   * - interface/theme/Color.kt
     - Colors for app theme
   * - interface/theme/Theme.kt
     - Material3 theme configuration
   * - interface/theme/Type.kt
     - Typography styles


5.3 Tests
---------


.. list-table::
   :header-rows: 1
   :widths: 46 46

   * - File / Path
     - Purpose
   * - app/src/test/.../data/update/UpdateManagerTest.kt
     - Version comparison / update regression tests
   * - app/src/test/.../data/config/AppLocalizationTest.kt
     - Localisation parsing and lookup regression tests
   * - app/src/test/.../data/config/CompilerConfigTest.kt
     - Include path normalisation, dedupe, path resolution
   * - app/src/test/.../data/compiler/ExplanationsTest.kt
     - ``data/_data_2026_exp.toml`` parsing regression tests
   * - app/src/test/.../data/compiler/SuccessTest.kt
     - Successful compile path expectations
   * - app/src/test/.../data/compiler/FailTest.kt
     - Failing compile path expectations
   * - app/src/test/.../data/compiler/IgnoreCaseWorkflowTest.kt
     - Ignore-case conversion workflow regression tests
   * - app/src/test/.../interface/editor/EditorTest.kt
     - Editor symbol/completion parsing, ``_data_2026_intern.toml`` descriptions
   * - app/src/test/.../ExampleUnitTest.kt
     - Template unit test
   * - app/src/androidTest/.../ExampleInstrumentedTest.kt
     - Template instrumented test




5.4 Terminal Module (`terminal/`, ported from the Xed editor)
-------------------------------------------------------------


.. list-table::
   :header-rows: 1
   :widths: 42 46

   * - File / Path
     - Purpose
   * - terminal/app/…/TerminalOverlay.kt
     - Compose entry point: binds ``TerminalService``, runs setup, shows install or terminal UI
   * - terminal/app/…/core/TerminalPaths.kt
     - Every private path (rootfs, bin, home, tmp) and the installed/empty checks
   * - terminal/app/…/core/RootfsSources.kt
     - Per-ABI Ubuntu base image URLs
   * - terminal/app/…/core/RootfsDownloader.kt
     - Streaming download with progress, atomic rename
   * - terminal/app/…/core/SandboxSetup.kt
     - One-time rootfs extraction by Android ``/system/bin/sh`` and ``tar``, using ``liblink2symlink.so``; setup state model and diagnostics
   * - terminal/app/…/core/SandboxEnvironment.kt
     - The environment array handed to the sandbox shell (PROOT, LOCAL, HOME, PAWNCC, …)
   * - terminal/app/…/core/ShellScripts.kt
     - Installs ``sandbox``/``setup``/``init``/``utils`` from assets and restores the exec bit
   * - terminal/app/…/shell/TerminalService.kt
     - Owns every PTY session so shells outlive the editor screen
   * - terminal/app/…/shell/TerminalBackend.kt
     - ``TerminalViewClient``/``TerminalSessionClient`` implementation
   * - terminal/app/…/ui/TerminalScreen.kt
     - PTY surface, extra key row, ANSI palette
   * - terminal/app/…/ui/TerminalSetupScreen.kt
     - Download/extract progress and failure screen
   * - terminal/app/…/ui/TerminalSurface.kt
     - Weak bridge from the backend to the live widget
   * - terminal/app/src/main/assets/terminal/*.sh
     - ``sandbox.sh`` (proot bind list), ``setup.sh`` (extraction), ``init.sh``, ``utils.sh``
   * - terminal/emulator/
     - Termux VT emulator; ``libtermux.so`` built with NDK 28.2 for 16 KB page alignment
   * - terminal/view/
     - ``TerminalView``, the Android View that renders a session
   * - terminal/proot/
     - PRoot sources; outputs ``libproot.so`` + ``libloader.so`` so Android may exec them
   * - terminal/link2symlink/
     - ``liblink2symlink.so``, the LD_PRELOAD fallback for kernels that refuse extraction under ptrace





6. Feature Map by Module
------------------------


.. list-table::
   :header-rows: 1
   :widths: 21 46 46

   * - Module
     - Key Files
     - Main Responsibility
   * - App configuration
     - app/build.gradle.kts, gradle/libs.versions.toml
     - Build system, versioning, SDK/NDK setup
   * - Native compiler
     - cpp/CMakeLists.txt, cpp/Compiler.cpp, cpp/Compiler.h
     - JNI bridge (``Runner_compile/getOutput/getErrors``) and native compile support
   * - Compiler config
     - data/config/CompilerConfig.kt
     - Persistent compile configuration
   * - Compile engine
     - data/compiler/Compiler.kt, Runner.kt, CompilerRunner.kt (legacy)
     - Compile logic, output capture, fallback retry
   * - Pawn symbol data
     - data/syntax/Registry.kt, InternDat.kt, Index.kt
     - Completion and highlighting data from ``data/_data_2026_intern.toml``
   * - Update flow
     - data/update/UpdateManager.kt
     - GitHub API interactions and APK update install pipeline
   * - Main app UI
     - interface/main/MainScreen.kt
     - Main compile entry point, self tests
   * - Editor UI
     - interface/editor/xedapi/EditorScreen.kt
     - Code canvas, tab strip, floating controls
   * - Editor terminal
     - interface/editor/portal/DeveloperPortal.kt
     - Command line, ``pawncc`` sandboxed invocation, version switch
   * - Sandbox terminal
     - terminal/app/…/TerminalOverlay.kt, shell/TerminalService.kt, core/*
     - PRoot sandbox bootstrap, PTY shell sessions, terminal UI opened from the sandbox button
   * - Terminal runtime
     - terminal/emulator, terminal/view, terminal/proot, terminal/link2symlink
     - Vendored Termux VT emulator plus the proot/loader native binaries
   * - Workspace model
     - interface/editor/Workspace/Session.kt
     - Folder trees, open documents, search/replace
   * - SAF access
     - interface/editor/Workspace/FolderPicker.kt
     - System folder picker intent and tree-URI to ``File`` resolution
   * - Localization
     - data/config/AppLocalization.kt, data/_data_2026_ect.toml
     - Single-source id/en/es strings
   * - Compiler explanations
     - data/compiler/Explanations.kt, data/_data_2026_exp.toml
     - Single-source hints for Pawn compiler messages
   * - Settings UI
     - interface/settings/Screen.kt
     - App configuration and update controls
   * - File browser
     - interface/filebrowser/FileBrowserDialog.kt
     - Storage browsing and file selection
   * - CI
     - .github/workflows/ci.yml
     - Lint, debug build, unit tests, artifact upload





7. Major Workflows
------------------

7.1 App launch flow
-------------------


.. list-table::
   :header-rows: 1
   :widths: 6 46

   * - Step
     - Action
   * - 1
     - MainActivity starts
   * - 2
     - MainViewModel and editor ViewModels initialize
   * - 3
     - MainScreen is displayed
   * - 4
     - Recent file is restored if present; if no valid file is available, the main screen waits until an action needs a source file rather than treating a stale temp file as the user's browse selection
   * - 5
     - Storage permission check is performed


7.2 File/folder selection flow
------------------------------


.. list-table::
   :header-rows: 1
   :widths: 6 46

   * - Step
     - Action
   * - 1
     - User selects a source using the main screen's Browse File action, or selects a file/folder from the editor's own picker
   * - 2
     - File extension (``.pawn``, ``.pwn``, ``.p``, ``.inc``) or folder URI is validated; the main-screen path is stored as the current compile selection
   * - 3
     - If the editor is opened, ``MainActivity`` observes ``selectedFilePath`` and calls ``EditorViewModel.attachFile()`` so the selected source, not an unrelated ``PawnMC/workspace/unit.pwn``, is loaded
   * - 4
     - With no folder workspace open, the source remains a standalone editor buffer (Save and Save As); an explicitly opened folder uses workspace tabs and Save All
   * - 5
     - In workspace mode, a selected real file can be registered as a tab; the generated starter file is excluded from workspace registration
   * - 6
     - The same selected path is used for compile, regardless of whether compilation is requested from the main screen or the editor


7.3 Compile flow
----------------


.. list-table::
   :header-rows: 1
   :widths: 6 46

   * - Step
     - Action
   * - 1
     - ``MainViewModel.compileFile()`` runs (or ``pawncc <file> [opts]`` in the terminal portal)
   * - 2
     - File validation and access are checked
   * - 3
     - ``Compiler.compile()`` is invoked; version chosen by forced mode or nearby detection
   * - 4
     - Compiler options are built from config
   * - 5
     - Native compile output is parsed (exit code, error count)
   * - 6
     - Output is annotated via ``Explainer`` with ``data/_data_2026_exp.toml`` hints
   * - 7
     - Output log and compile result are shown in UI
   * - 8
     - If errors repeat and fallback is allowed, compile is retried on the other version


7.4 Editor save modes
------------------

.. list-table::
   :header-rows: 1
   :widths: 44 15 46

   * - Editor state
     - Toolbar action
     - Effect
   * - No editor folder workspace is open
     - ``Save As``
     - Opens Android's document creation picker and writes the visible standalone editor buffer to the selected destination. The separate ``Save`` action writes back to the currently selected file.
   * - One or more editor folder workspaces are open
     - ``Save All``
     - Saves every modified open document in all open workspaces and refreshes the active workspace tree. The separate ``Save`` action saves the active document.
   * - A temporary ``PawnMC/workspace/unit.pwn`` starter is in use
     - Standalone mode
     - The starter exists only to give a new user a compilable example; it is not promoted to an editor workspace tab by opening the editor.


The mode is determined by ``WorkspaceSession.isWorkspaceOpen`` (there is an explicitly open/restored folder), not merely by whether a source file is selected. Opening a source via Browse File on the main screen updates ``selectedFilePath``; ``MainActivity`` attaches that path to the activity-scoped ``EditorViewModel``, and Xed loads that source when entered. If a folder workspace is already open, a real selected source may become a workspace tab; the temporary starter is excluded. The main screen continues to compile the same selected source independently of entering Xed.

7.5 Settings flow
-----------------


.. list-table::
   :header-rows: 1
   :widths: 6 46

   * - Step
     - Action
   * - 1
     - User opens Settings screen
   * - 2
     - Current config values are read from CompilerConfig
   * - 3
     - User updates compiler preferences and include paths (uniqueness enforced)
   * - 4
     - Preferences are saved to SharedPreferences
   * - 5
     - Restart may be required when switching compiler versions


7.6 Update flow
---------------


.. list-table::
   :header-rows: 1
   :widths: 6 46

   * - Step
     - Action
   * - 1
     - App checks the GitHub releases latest endpoint
   * - 2
     - A matching APK asset is selected and version comparison runs
   * - 3
     - If newer, the APK is downloaded
   * - 4
     - Android install permission is evaluated
   * - 5
     - APK install intent is started through the FileProvider





8. Sandbox Terminal: Runtime, Boundaries, and Development Guide
---------------------------------------------------------------
The editor sandbox button opens the ``:terminal:app`` Compose overlay. This terminal is a separate real Ubuntu userspace from ``DeveloperPortal``: the portal is an in-app command interface with its own command parser and Pawn compile validation; the sandbox is an interactive POSIX shell running under PRoot with a Termux VT/PTY front end. Both may expose Pawn compiler functionality, but they have different execution paths and trust boundaries.

8.1 What runs where
-------------------

.. list-table::
   :header-rows: 1
   :widths: 19 46

   * - Layer
     - Implementation and responsibility
   * - Editor UI
     - ``EditorScreen`` builds ``TerminalRequest`` from the active workspace root (or current file's parent) and selected Pawn compiler library, then overlays ``TerminalOverlay``. Back closes the overlay.
   * - Overlay/bootstrap
     - ``TerminalOverlay`` binds ``TerminalService``, checks ``TerminalPaths.isInstalled``, and invokes ``SandboxSetup.ensureReady`` when necessary. Setup state/progress/failure details are presented by ``TerminalSetupScreen``.
   * - Session owner
     - ``TerminalService`` owns ``TerminalSession`` instances independently of the composable. Sessions survive editor recomposition/navigation while the app process remains alive; the service is not foreground and Android may stop it with the app. A session is created lazily and reused by ID.
   * - Terminal I/O
     - ``terminal:view`` supplies the Android terminal view; ``terminal:emulator`` provides the Termux VT emulator/PTY machinery; ``TerminalBackend`` connects session callbacks, input, clipboard and rendering. The UI supports new sessions, clear, copy/download transcript and extra keys.
   * - Linux compatibility
     - ``terminal:proot`` packages ``libproot.so`` and ``libloader.so``; ``terminal:link2symlink`` provides the preload shim needed to preserve absolute symlinks while extracting Ubuntu. ``terminal/emulator`` builds ``libtermux.so``. These native libraries must be packaged uncompressed/executable from the app ``nativeLibraryDir`` (the app module uses legacy JNI packaging).
   * - Guest userspace
     - Ubuntu Base 24.04.3 is downloaded for the device ABI from ``RootfsSources``, then extracted into the app-private ``files/local/sandbox`` directory. It includes ``apt``, ``dpkg``, and ``dash``; it is not a VM or a separate Android process/container namespace.


8.2 First-open installation and later opens
-------------------------------------------
#. `TerminalPaths` defines all terminal data under app-private storage: `files/local` (also `$LOCAL`), `files/local/bin` (managed shell scripts and compiler link), `files/local/sandbox` (rootfs), `files/local/home` (host home bind source), and `files/tmp` (download/staging and PRoot temporary files). Native libraries live in Android's `applicationInfo.nativeLibraryDir`.
#. `SandboxSetup.ensureReady()` first trusts an installation only when the marker `.terminal_setup_ok_DO_NOT_REMOVE` exists and the rootfs has non-helper top-level directories. Otherwise `RootfsDownloader` chooses the matching supported ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`), streams the HTTPS archive into a `.part` file, checks HTTP status, non-empty/declared byte length, and atomically stages it as `files/tmp/sandbox.tar.gz`. Failed partial downloads are removed; unsupported ABIs fail clearly.
#. `ShellScripts` copies the packaged `setup.sh`, `sandbox.sh`, `init.sh`, and `utils.sh` assets into `files/local/bin` when content changes and restores executable mode. `setup.sh` is run by Android `/system/bin/sh` with the generated environment. Extraction is deliberately host-side, not through PRoot: Android `/system/bin/tar` plus `LD_PRELOAD=liblink2symlink.so` handles absolute symlinks. The script validates archive/tool/destination/free space, extracts the rootfs, verifies `/bin/sh` or `/bin/dash` and `/usr/bin/dpkg`, configures basic Ubuntu host/network/group files, then writes the marker and deletes the staged archive only after success. Failures retain diagnostic output and the staged archive for investigation/retry. Setup sets `PAWNMC_SETUP_ONLY=1` so the script returns after extraction instead of starting an interactive PRoot shell from the host-side bootstrap. Interactive launch and host-side extraction are now explicit modes: the setup process sets `PAWNMC_SETUP_ONLY=1`, while normal invocation launches the interactive sandbox. Keep this hand-off contract aligned if either bootstrap changes.
#. Later opens skip download/extraction when marker plus rootfs checks pass. Shell script assets are still refreshed by `ShellScripts.install`, so an app update can update managed scripts without re-downloading the rootfs. The Ubuntu rootfs is otherwise persistent and package changes made by the user remain in app-private data until app data is cleared/uninstalled.

8.3 Interactive shell startup and environment
---------------------------------------------
``TerminalService.createSession()`` installs/refreshes scripts, resolves the selected versioned ``libpawnc*.so`` from ``nativeLibraryDir``, maps the requested device working directory into a container path, builds environment variables, and starts a Termux ``TerminalSession`` with ``/system/bin/sh`` and the ``sandbox`` script. ``sandbox.sh`` is the single owner of the PRoot command and bind list. It invokes the PRoot shared library through the platform dynamic linker (``/system/bin/linker64`` when present, otherwise ``/system/bin/linker``), supplies ``libloader.so``, and starts the guest ``/bin/sh`` interactively. PRoot presents UID 0 inside the guest (``-0``); this is emulated identity, not Android root.

Important environment contracts from ``SandboxEnvironment.build()``:


.. list-table::
   :header-rows: 1
   :widths: 46 46

   * - Variable
     - Purpose
   * - ``PROOT``, ``PROOT_LOADER``, ``PROOT_TMP_DIR``, ``LINKER``
     - Native PRoot/loader/temp paths and platform linker used to launch a ``.so`` executable on Android.
   * - ``LOCAL``, ``PRIVATE_DIR``, ``NATIVE_LIB_DIR``, ``TMP_DIR``, ``TMPDIR``, ``EXT_HOME``, ``PUBLIC_HOME``
     - App-private script/rootfs/home/temp paths, native library location, and external-files path consumed by scripts.
   * - ``PRIMARY_ABI``, ``ANDROID_API_LEVEL``
     - Device metadata used for setup diagnostics.
   * - ``WKDIR``
     - Container-side initial directory selected by the editor. ``/sdcard/...`` and ``/storage/...`` paths are preserved; private/non-shared paths fall back to ``/home``; no selection defaults to ``/home``.
   * - ``HOME=/home``, ``TERM=xterm-256color``, ``COLORTERM=truecolor``, ``LANG=C.UTF-8``, ``TZ=UTC``
     - Guest shell identity, terminal capabilities and locale/time settings.
   * - ``PAWNCC``
     - Optional absolute path to the selected compiler library; ``init.sh`` links it as ``$LOCAL/bin/pawncc`` and adds that directory to ``PATH``. A missing library means no compiler link is exposed.
   * - Android runtime variables
     - ``ANDROID_DATA``, ``ANDROID_ROOT``, ART roots and boot classpaths are forwarded for Android-side binary compatibility.


On each interactive shell start, ``init.sh`` sets a POSIX-compatible PATH and ``/bin/sh``, sources the managed POSIX ``utils``, warns if setup marked the rootfs degraded, sets timezone links, links the selected compiler, ensures the common ``/sdcard`` to ``/storage/emulated/0`` mapping where possible, prints a PawnMC banner, changes to ``$WKDIR`` (fallback ``/home``, then ``/``), and finally sources ``/etc/pawnmc/initrc`` if the user created one. It does not silently install extra runtimes/packages. ``sandbox.sh`` binds Android ``/sdcard``, ``/storage``, ``/dev``, ``/data``, ``/proc``, ``/sys`` and available system/apex paths; maps app home to guest ``/home`` and ``/root``; binds app-private storage; overlays selected proc/fd entries; and binds rootfs ``tmp`` as ``/dev/shm`` with mode 1777. PRoot uses ``--link2symlink``, ``--sysvipc``, and ``-L`` for the guest layout.

8.4 Storage access and security caveats
---------------------------------------
This is a userspace compatibility environment, not a security sandbox that isolates commands from Android resources. PRoot does not grant real root, bypass Android's Linux UID/SELinux rules, or create VM/kernel namespaces. Commands can access only what the app process can access, but the scripts intentionally bind broad Android paths (notably ``/sdcard``, ``/storage``, ``/data``, and app-private storage). A process in the guest may modify/delete files visible through those binds subject to the app's Android permissions and filesystem rules. Treat commands, downloaded scripts, and ``apt`` packages as code executing with the app's available access. Do not describe this terminal as safe for untrusted code without stronger OS-level isolation and a narrower mount policy.

The editor passes a filesystem path, not a persisted SAF capability. ``SandboxEnvironment.containerWorkingDirectory()`` can only start in paths the guest can resolve through its mounts; SAF-only documents or paths outside ``/sdcard``/``/storage`` may not be usable as the shell's cwd. Main compile permission handling and Xed's SAF folder picker do not themselves give arbitrary shell access. Keep this distinction explicit when extending storage integration.

8.5 Extension checklist / current follow-up work
------------------------------------------------
- Add device/emulator tests for each packaged ABI, PRoot launch through the correct linker, real session startup, working-directory mapping, and selected compiler visibility. Unit tests alone cannot validate Android `exec`/SELinux/native linker behavior.
- Review and, where product requirements allow, narrow the PRoot bind list. Any change to `/sdcard`, `/storage`, `/data`, `$PRIVATE_DIR`, proc/dev/sys, shared memory, or home binds changes user-visible reach and must be documented and tested.
- Improve path/SAF handling deliberately: either document that shell access targets ordinary shared-storage paths, or implement a controlled bridge. Do not imply that a SAF tree grant is automatically mounted into Ubuntu.
- Test download interruption, corrupt/truncated archive, low storage, marker-with-missing-rootfs, unsupported ABI, missing native library, 32-bit linker fallback, and setup retry. Preserve actionable output and never create the success marker before rootfs validation.
- Decide whether package/rootfs upgrades are supported. Today the rootfs is persistent and setup is one-time; versioning/migrations need an explicit marker/upgrade strategy that does not redownload or destroy user packages unexpectedly.
- Revisit service/background policy if long-running shell processes must survive backgrounding or process pressure. The service currently is not foreground and the app process can be stopped by Android.
- Keep `sandbox.sh`, `setup.sh`, `init.sh`, `utils.sh`, `SandboxEnvironment`, `TerminalPaths`, and this section in sync. A shell environment variable or mount can appear correct in Kotlin while being absent from the script contract.




9. Manual Analysis and Engineering Notes
----------------------------------------


.. list-table::
   :header-rows: 1
   :widths: 33 46

   * - Topic
     - Observation
   * - Product focus
     - PawnMC is clearly a mobile-first Pawn compiler utility rather than a general-purpose IDE
   * - Strength
     - Strong separation between UI, compiler logic, config, and native bridge
   * - Risk area
     - Storage access is critical because the app reads and writes file-based compiler artifacts; modern Android requires SAF tree URIs for folder access
   * - Native lifecycle
     - ``Runner`` holds initialization state, initialised version, and one-shot fallback mutation, so restart/session reset logic matters
   * - Update reliability
     - GitHub release checking and APK install flow require careful handling of network, invalid APKs, and Android install restrictions
   * - Configuration integrity
     - CompilerConfig is central to compile behaviour and must stay consistent with UI and native state
   * - Include path hygiene
     - Only ``.pawn``, ``.pwn``, ``.p`` and ``.inc`` are treated as script extensions and stripped from include/compile paths
   * - Compose state in lists
     - The workspace tree must be remembered against ``tree`` and ``collapsedPaths``, otherwise folder toggles never recompose
   * - Single scrolling surface
     - The explorer keeps opened editors, tree and hits in one LazyColumn; competing scroll containers swallow taps
   * - Single localisation source
     - ``data/_data_2026_ect.toml`` is the only place UI strings live; ``AppLocalization`` must never duplicate them
   * - Single explanation source
     - ``data/_data_2026_exp.toml`` is the only place compiler message hints live; ``Explanations`` parses it once per process
   * - Single symbol source
     - Pawn keywords/natives/forwards come from ``data/_data_2026_intern.toml`` via ``InternDat``/``PawnIndex``; ``Registry`` only curates and merges
   * - Terminal portal data
     - Portal text comes from ``data/_data_2026_term.toml`` with per-language ``key.id`` / ``key.en`` / ``key.es`` lookup and English fallback
   * - Assets packaging
     - ``assets.srcDir(rootProject.file("data"))`` ships the whole folder, so all four ``_data_2026_*.toml`` files reach the app; JVM tests fall back to reading ``data/...`` from the filesystem
   * - Compiler layer split
     - ``Compiler`` stays a thin facade while implementation is split by single responsibility
   * - Dead file
     - ``data/compiler/CompilerRunner.kt`` duplicates ``Runner.kt`` and has no references; it should be deleted to avoid two divergent execution layers
   * - Naming inside a package
     - Files in ``data/compiler`` drop the redundant ``Compiler`` prefix because the package already states it
   * - JNI symbol names follow the owner
     - Native ``compile``/``getOutput``/``getErrors`` live in ``Runner``, so JNI names are ``..._data_compiler_Runner_*`` and ProGuard keeps that class
   * - Editor host
     - The editor is a composable inside ``MainActivity``, not a separate activity, so editor and main state share one lifecycle
   * - Terminal portal sandboxing
     - ``PortalCommandParser.pawnccInvocation`` canonicalises the source and rejects anything outside the workspace root or with a non-script extension; this is portal command validation, distinct from the PRoot Ubuntu shell and not an OS security boundary
   * - Build/CI contract
     - CI builds with JDK 17, ``platforms;android-36``, NDK 29.0.14206865, CMake 3.22.1 and recursive submodules; release builds are minified and shrink resources
   * - Release signing
     - Signing is optional and driven by ``local.properties`` credentials plus ``release-key.jks``; without them the release build falls back to unsigned





10. Notable Implementation Details
----------------------------------


.. list-table::
   :header-rows: 1
   :widths: 26 46

   * - Detail
     - Explanation
   * - Native library loading
     - ``System.loadLibrary`` with version-specific library names
   * - Fallback retry logic
     - Additional compiler retry triggered by error-count heuristics and guarded by forced mode
   * - Forced compiler mode
     - When enabled, auto-detection and fallback switching are skipped and the selected version is used
   * - Hidden version file
     - The update system stores the current app version in an app-private hidden file
   * - FileProvider
     - Required for safe APK install via content URI
   * - SharedPreferences
     - Used for persistent compiler settings and last-used paths
   * - Include path normalisation
     - ``normalizeIncludePathInput`` strips a trailing script extension, so a path typed as a file still resolves as a folder
   * - Compile path resolution
     - ``resolveCompilePath`` keeps a real file untouched and only strips an extension when nothing exists at the given path
   * - Buffer synchronisation
     - The editor reload watcher compares document identity, path **and** content, and mirrors typing into the holder so the caret is never reset
   * - Workspace registration
     - A file selected on the main screen remains in standalone mode if no editor folder workspace is open; with an active workspace, a real selected file is registered as a tab. A generated ``PawnMC/workspace/unit.pwn`` starter is never registered as a project file
   * - Folder picker
     - ``buildFolderPickerIntent`` adds ``EXTRA_INITIAL_URI`` for Downloads on API 26+ because ``OpenDocumentTree`` cannot preset a location
   * - APK output naming
     - ``assemble``/``package`` tasks rename outputs to ``pawnmc-<version>-<variant>.apk``
   * - In-app diagnostics
     - ``AppSelfTestCatalog`` exposes per-test and run-all entries surfaced in a dialog and a results card on the main screen





11. Recommended Development Priorities
--------------------------------------


.. list-table::
   :header-rows: 1
   :widths: 8 46

   * - Priority
     - Recommendation
   * - High
     - Delete or consolidate the unused ``CompilerRunner.kt`` so only one execution layer exists
   * - High
     - Stabilize GitHub update checking and APK installation flow
   * - High
     - Improve network failure handling and APK validation
   * - High
     - Verify sandbox startup and mount behavior on physical devices for each supported ABI; review broad bind mounts before presenting the terminal as a security boundary
   * - High
     - Keep SAF/tree-URI handling correct across Android versions, since folder access is permission-sensitive
   * - Medium
     - Extend the terminal portal (``docs`` content source, richer ``switch`` handling, output history); keep portal command validation distinct from the PRoot shell
   * - Medium
     - Add terminal integration tests for rootfs setup failure/retry, working-directory mapping, compiler exposure, and PTY/service lifecycle
   * - Medium
     - Improve log export and compile result history
   * - Medium
     - Extend workspace support: rename/move files, drag-and-drop in the explorer, richer replace previews
   * - Medium
     - Grow ``data/_data_2026_intern.toml`` symbol coverage and add tests guarding the data-file parsers
   * - Low
     - Extend theme and app customisation options (editor background presets, accent density)
   * - Low
     - Keep localisation strings in sync in ``data/_data_2026_ect.toml`` only, and add keys as the UI grows
   * - Low
     - Extend ``data/_data_2026_exp.toml`` with more Pawn message codes and keep it as the only place hints are maintained





12. Conclusion
--------------

PawnMC is a mobile-first Android compiler tool for Pawn source files. The codebase is structured around a clear separation of concerns: Compose UI for interaction, ``data/`` for config, compiler, symbols and updates, and a C++ layer for actual compile execution. Xed supports a standalone file flow as well as explicit folder workspaces: the main-screen Browse File selection is the file Xed opens, standalone mode provides Save As, and workspace mode provides Save All across modified workspace documents. The editor also hosts a separate Ubuntu userspace shell under PRoot; its bootstrap, mounts, compiler hand-off, PTY lifecycle, storage boundaries, known setup-script hand-off check, and current follow-up work are documented below.

The most relevant files to understand the project are:


.. list-table::
   :header-rows: 1
   :widths: 46 46

   * - File / Path
     - Why it matters
   * - README.rst
     - Top-level overview
   * - .github/workflows/ci.yml
     - Build/test contract
   * - app/build.gradle.kts
     - App build and version metadata
   * - app/src/main/AndroidManifest.xml
     - Permissions and install support
   * - app/src/main/java/com/rvdjv/pawnmc/data/config/CompilerConfig.kt
     - Runtime compile settings
   * - app/src/main/java/com/rvdjv/pawnmc/data/compiler/Compiler.kt
     - Public compile engine entry point (facade)
   * - app/src/main/java/com/rvdjv/pawnmc/data/compiler/Runner.kt
     - Live native execution and fallback logic
   * - app/src/main/java/com/rvdjv/pawnmc/data/syntax/InternDat.kt
     - Pawn symbol data loading
   * - app/src/main/java/com/rvdjv/pawnmc/interface/main/MainScreen.kt
     - Main user workflow
   * - app/src/main/java/com/rvdjv/pawnmc/interface/main/AppSelfTestRunner.kt
     - In-app diagnostics
   * - app/src/main/java/com/rvdjv/pawnmc/interface/editor/xedapi/EditorScreen.kt
     - Editor canvas, tab strip, floating controls
   * - app/src/main/java/com/rvdjv/pawnmc/interface/editor/Workspace/Session.kt
     - Workspace trees, open documents, search/replace
   * - app/src/main/java/com/rvdjv/pawnmc/interface/editor/portal/DeveloperPortal.kt
     - Editor command portal, tokenization and workspace-confined ``pawncc`` command validation
   * - terminal/app/src/main/java/com/pawnmc/terminal/TerminalOverlay.kt
     - The editor's interactive Ubuntu/PRoot terminal overlay and setup-to-session handoff
   * - terminal/app/src/main/java/com/pawnmc/terminal/shell/TerminalService.kt
     - PTY-backed shell session ownership, PRoot launch and selected compiler resolution
   * - terminal/app/src/main/assets/terminal/sandbox.sh
     - PRoot rootfs configuration, Android bind mounts and guest shell launch
   * - terminal/app/src/main/assets/terminal/setup.sh
     - Rootfs validation/extraction, Ubuntu setup and installation marker lifecycle
   * - terminal/app/src/main/java/com/pawnmc/terminal/core/SandboxEnvironment.kt
     - Host-to-guest environment and initial working-directory contract
   * - app/src/main/java/com/rvdjv/pawnmc/interface/settings/Screen.kt
     - Settings and update actions
   * - app/src/main/java/com/rvdjv/pawnmc/data/update/UpdateManager.kt
     - Release update logic
   * - app/src/main/cpp/Compiler.cpp
     - Native bridge implementation
   * - data/_data_2026_ect.toml
     - Localisation data for Indonesian, English, Spanish (Argentina), and Russian
   * - data/_data_2026_exp.toml
     - Local explanations of Pawn compiler messages
   * - data/_data_2026_intern.toml
     - Pawn natives/forwards/descriptions
   * - data/_data_2026_term.toml
     - Terminal portal strings




This roadmap reflects the repository and implementation reviewed for the current development update. Treat source files and tests as authoritative when implementation changes; keep the sandbox workflow and boundary documentation in sync whenever its bootstrap, mount list, rootfs, ABI support, shell startup, or service lifecycle changes.
