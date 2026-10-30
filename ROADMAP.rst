PawnMC Roadmap
==============

Overview
--------

PawnMC is a Pawn editor and compiler for Android. It ships two Pawn compilers
(3.10.7 and 3.10.11) as native libraries, highlights syntax through the Sora
editor engine, and explains compiler messages in the app language. An optional
SA-MP + MariaDB runtime is included as a separate module so a local server can be
run straight from the phone.

Current status
--------------

- App version 1.5.2 (versionCode 6), latest tag ``v1.5.2``.
- compileSdk and targetSdk 36, minSdk 24. The ``nougat`` flavor serves Android
  7/8 with minSdk 26 and the ``.legacy`` package suffix.

What works today
----------------

Editor and workspace
  * Workspace folders with an explorer, document tabs, workspace-wide search
    and replace, and live file watching.
  * Autocompletion from the Pawn symbol tables, plus ``native`` and ``forward``
    symbols declared in the file being edited.
  * Built-in developer portal: ``help``, ``docs``, ``pawncc``, ``switch``,
    ``which``, ``deviceinfo``, ``version`` and the other non-shell commands.

Compiler
  * Two native compiler versions, ``pawncc`` detection next to the source file,
    and automatic fallback between versions when the error count passes the
    threshold.
  * "Ignore case" mode: folder backup, lowercased file names and ``#include``
    references.
  * Compiler message explanations read from ``data/_data_2026_exp.toml`` with an
    override in ``Documents/PawnMC/config``.
  * One SQLite log database for every compile.

Storage and updates
  * User data lives in ``Documents/PawnMC``, with a one-time migration and a
    JSON settings mirror that survives a reinstall.
  * GitHub update check with separate packages for Android 7/8 and Android
    8-15.

Languages
  * Five interface languages: Indonesian, English, Spanish, Russian and Tagalog.
    Tagalog is fully translated. A ``.toml`` asset that lacks a language falls
    back to English.

SA-MP runtime (thirdparty/PocketSAMP)
  * The 32-bit SA-MP server runs through the QEMU i386 translator, with MariaDB
    i386 on the same path.
  * Runtime archives are not bundled in the APK; they are downloaded once into
    ``Documents/PawnMC/storage`` and unpacked per archive.
  * Built-in screens: dashboard, console, database, crash logs and the
    ``server.cfg`` editor. Notification, log and database text follow the app
    language too.

Next
----

- Add tests for the "Ignore case" conversion and the compiler version detection
  so regressions surface before a release.
- Extend language coverage of the localization assets
  (``_data_2026_exp.toml``, ``_data_2026_term.toml``) so every message, not just
  the interface, exists in all five languages.
- Keep the SA-MP runtime behind a small interface so a QEMU or MariaDB package
  update never touches the UI code.

Notes
-----

``thirdparty/PocketSAMP`` is a third-party component under the
``com.pocketsamp.server`` namespace. Its internal files use the ``third_``
prefix and the ``configuration`` package uses ``Reader``/``Writer`` instead of
repeating the folder name.
