PocketSAMP
==========

Portable SA-MP 0.3.7/0.3.DL-R1 server runtime with a local MariaDB instance, embedded in PawnMC
as the ``:pocketsamp`` Android library module. No root is required.

.. important::

   **File Manager dependency.** PocketSAMP works out-of-the-box using a 0.3.7 game mode
   template downloaded from GitHub (`archive <https://github.com/novusr/Pawn-MC/tree/main/thirdparty/PocketSAMP/runtime/samp>`__)
   and stored in your ``downloads`` directory. This template is accessible for quick
   testing purposes **only**. To run your own server, you can simply replace it with your
   custom game mode folder using a file manager (`File Manager guide <https://github.com/novusr/Pawn-MC/blob/main/POCKETSAMP.rst#file-manager>`__).
   Without the File Manager you cannot select, replace, or manage your own game mode folder
   — the built-in template is read-only for testing and is not intended as your production
   server root.

Architecture
------------

Android devices run 64-bit ARM. The official SA-MP server (``samp03svr``) is a
closed-source 32-bit x86 Linux executable. PocketSAMP closes that gap with a user-mode
translator rather than a virtual machine.

Two processes are started:

``qemu-i386``
    Translates the 32-bit x86 SA-MP server. The guest filesystem is
    ``rootfs_i386``, a minimal i386 glibc sysroot shipped with the runtime.

``linker64``
    Starts MariaDB, an Android/Bionic ARM64 build installed from the separate
    ``mariadb/r_mariadb.tar.gz`` runtime archive. Its bundled shared libraries are loaded
    from the extracted runtime directory; MariaDB does not use QEMU or the i386 glibc
    sysroot.

SA-MP plugins remain i386 and connect to MariaDB over TCP loopback at
``127.0.0.1:3306``. The in-app database manager is a JDBC client on the same address,
so no web server is involved.

Memory footprint stays near 150 MB by running MariaDB with
``innodb_buffer_pool_size = 16M`` and ``performance_schema = OFF``.

Runtime files
-------------

The archives are not packaged in the APK. They stay in the repository under
``thirdparty/PocketSAMP/runtime/`` so that raw GitHub URLs exist for them, and the app
downloads each one on demand into shared storage::

    /storage/emulated/0/Documents/PawnMC/storage/
        r_rootfs_i386.tar.gz
        r_samp.tar.gz
        r_mariadb.tar.gz
The repository keeps the same archives grouped by runtime type::

    thirdparty/PocketSAMP/runtime/rootfs/r_rootfs_i386.tar.gz
    thirdparty/PocketSAMP/runtime/samp/r_samp.tar.gz
    thirdparty/PocketSAMP/runtime/mariadb/r_mariadb.tar.gz
    thirdparty/PocketSAMP/runtime/ecosystem/r_ecosystem.tar.gz
``ecosystem/r_ecosystem.tar.gz`` holds the host-side ARM64 native helpers that cannot
be built on the device (the QEMU user-mode translator and its support libraries). It is
unpacked into ``filesDir`` and the translator is picked up from there when the APK does
not bundle it.
The app checks each download against its expected SHA-256 digest and replaces invalid
cached archives before extraction.
Each archive is unpacked independently into the app's private storage, so a missing or
corrupt file only disables the part that needs it::

    filesDir/rootfs_i386/    i386 glibc sysroot for QEMU
    filesDir/bin/            samp03svr and announce
    filesDir/mariadb/        bin/, lib/, share/ and the data directory

Downloading requires "All files access" on Android 11 and later. The transfer runs in
the background without notifications. The PocketSAMP screen refuses to open while the
permission is missing and shows only the grant action; there is no dismiss button,
because neither the SA-MP server nor the database can start without those files.

MariaDB bundle
--------------

``runtime/mariadb/r_mariadb.tar.gz`` contains the Android ARM64 daemon and the non-system
dependencies needed to run it with Bionic. The archive holds::

    bin/mariadbd
    lib/                Bionic ARM64 shared-library dependencies
    share/mariadb/      error messages and bootstrap system SQL files

The daemon uses Android system libraries (``libc.so``, ``libdl.so`` and ``libm.so``)
and libraries shipped in the archive, including ``libc++_shared.so``,
``libcrypto_3.so``, ``libssl_3.so``, ``libcrypt.so``, ``libpcre2-8.so``,
``libandroid-support.so`` and ``libz_1.so``. ``ThirdLauncher`` starts the ARM64
executable through ``/system/bin/linker64`` so it can be installed from the downloaded
runtime archive instead of shipping the daemon in the APK's ``jniLibs`` directory.
First-run system tables are created by running ``mariadbd --bootstrap`` with the
matching SQL scripts shipped in ``share/mariadb/``; no i386 installer or QEMU is used.
This bundle is MariaDB 10.4.3. Since the previous i386 bundle is MariaDB 10.11.18,
the runtime refuses to start against a newer existing data directory and leaves it
untouched; export and import the databases into a compatible runtime before switching.
The ``MariaDB ARM64 Runtime Bundle`` workflow validates the ELF architecture and
dependency files in the checked-in archive.

Gamemode directory
------------------

The server reads the user's SA-MP files directly, without copying. The default is
``/storage/emulated/0/PocketSAMP/``; a folder can be chosen from the Dashboard or
File Manager.
The same selected folder is used by the PocketSAMP File Manager, Dashboard, Config
screen and server process. The File Manager can browse folders, create files and
directories, rename items and delete them after confirmation. It does not copy files
into the app's private storage, and its operations are restricted to the selected
folder and its descendants. Stop the server before changing its selected folder.

Configuration screen and server.cfg
-----------------------------------

.. important::

   **Configuration tab role changed.** Back then (v1.5.2 and earlier, see
   `Pawn-MC releases <https://github.com/novusr/Pawn-MC/releases/tag/v1.5.2>`__)
   the configuration tab was for editing. Now it has switched careers into a
   comprehension booster for the game mode's `server.cfg
   <https://sampwiki.blast.hk/wiki/Server.cfg>`__ in the file manager. Settings now
   make full use of the main ``server.cfg`` in the file manager — the implementation
   lives in `Config.kt <https://github.com/novusr/Pawn-MC/blob/main/thirdparty/PocketSAMP/app/src/main/java/com/pocketsamp/server/configuration/Parameters/Config.kt>`__.
   Read and write operations target the selected folder's ``server.cfg`` directly;
   saving updates the settings managed by the screen while keeping other directives
   and comment lines in the file. A folder without ``server.cfg`` remains browsable,
   but cannot be used to start the server until a configuration is added.

Expected contents:

- ``server.cfg``
- ``gamemodes/`` compiled ``.amx`` files
- ``filterscripts/``
- ``plugins/`` 32-bit x86 Linux plugins (``.so``); x86_64 and arm64 plugins will not load
- ``scriptfiles/``
- ``server_log.txt`` written by the server

Building
--------

Build PawnMC, not this module directly. PocketSAMP is a library and has no launcher
activity; it is opened from the PawnMC main interface and returns to it with the back
button.

Local build: open the PawnMC project in Android Studio, sync Gradle and build the
``app`` module. Continuous integration builds the debug APK in
``.github/workflows/ci.yml``.

Only SA-MP needs the x86 user-mode translator. MariaDB is ARM64-native and PRoot is
not part of either database launch path.

Source layout
-------------

::

    com/pocketsamp/server/
        MainActivity.kt          embedded UI entry, permission gate, tab layout
        ServerApp.kt               install() and launch() hooks used by PawnMC
        Locales.kt               ID/EN/ES/RU/TL string table
        config/
            Config.kt            server.cfg read and write
            Directory/Manager.kt  gamemode folder, Documents/PawnMC, permissions
        crash/
            Handler.kt           global uncaught-exception handler
            Dialog.kt            isolated :crash process UI
            Manager.kt           crash report storage
            Report.kt            crash report model
        db/
            DatabaseManager.kt   JDBC client for the local MariaDB
        engine/
            Extractor.kt         unpacks the runtime archives
            Downloader.kt        fetches the archives into Documents/PawnMC/storage
            Launcher.kt          ELF detection, Android linker versus QEMU
            MariaDb.kt           MariaDB lifecycle
            Samp.kt              SA-MP server lifecycle
            Process.kt           process supervision and log streaming
            Unpacker.kt          tar/gzip extraction
        service/
            RuntimeService.kt    foreground service, wakelocks, notification
        ui/
            screens/             Dashboard, Console, Database, Crash logs, Configuration,
                                 phpMyAdmin browser
            theme/               colour ramp and typography

Cross-references
----------------

- `POCKETSAMP.rst <POCKETSAMP.rst>`__ — top-level PocketSAMP documentation in the repository root
- `File Manager <https://github.com/novusr/Pawn-MC/blob/main/POCKETSAMP.rst#file-manager>`__ — managing game mode folders and server.cfg
- `SA-MP Server.cfg wiki <https://sampwiki.blast.hk/wiki/Server.cfg>`__ — official server.cfg reference
- `Config.kt source <https://github.com/novusr/Pawn-MC/blob/main/thirdparty/PocketSAMP/app/src/main/java/com/pocketsamp/server/configuration/Parameters/Config.kt>`__ — server.cfg read/write implementation
- `Pawn-MC releases <https://github.com/novusr/Pawn-MC/releases>`__ — release history including v1.5.2

Licence
-------

- SA-MP binaries and sample gamemodes remain copyrighted by the SA-MP Team.
- PRoot is licensed under GPL-2.0.
- QEMU is licensed under GPL-2.0.
- MariaDB is licensed under GPL-2.0.
