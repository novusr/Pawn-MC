PocketSAMP
==========

Portable SA-MP 0.3.7 server runtime with a local MariaDB instance, embedded in PawnMC
as the ``:pocketsamp`` Android library module. No root is required.

Architecture
------------

Android devices run 64-bit ARM. The official SA-MP server (``samp03svr``) is a
closed-source 32-bit x86 Linux executable. PocketSAMP closes that gap with a user-mode
translator rather than a virtual machine.

Two processes are started:

``qemu-i386``
    Translates the 32-bit x86 SA-MP server. The guest filesystem is
    ``rootfs_i386``, a minimal i386 glibc sysroot shipped with the runtime.

``qemu-i386`` (second instance)
    Runs the MariaDB daemon. MariaDB is an ordinary Debian i386 build, not a native
    Android build: it links glibc, which the same ``rootfs_i386`` already provides.
    There is no arm64 MariaDB path and none is planned, because MariaDB depends on
    glibc facilities (``nsswitch``, ``getpwent``, ``dlopen`` semantics) that Android's
    bionic libc does not implement.

Both guests reach the database over TCP loopback at ``127.0.0.1:3306``. The in-app
database manager is a JDBC client on the same address, so no web server is involved.

Memory footprint stays near 150 MB by running MariaDB with
``innodb_buffer_pool_size = 16M`` and ``performance_schema = OFF``.

Runtime files
-------------

The archives are not packaged in the APK. They are downloaded once from the upstream
repository into shared storage and unpacked from there::

    /storage/emulated/0/Documents/PawnMC/storage/
        rootfs_i386.tar.gz
        samp_base.tar.gz
        mariadb_i386.tar.gz

The unpacked trees live in the app's private storage::

    filesDir/rootfs_i386/    i386 glibc sysroot for QEMU
    filesDir/bin/            samp03svr and announce
    filesDir/mariadb/        bin/, lib/, share/ and the data directory

Downloading requires "All files access" on Android 11 and later. The transfer runs in
the background without notifications. The PocketSAMP screen refuses to open while the
permission is missing and shows only the grant action; there is no dismiss button,
because neither the SA-MP server nor the database can start without those files.

MariaDB bundle
--------------

``mariadb_i386.tar.gz`` must be self-contained. The archive holds::

    bin/mariadbd
    bin/mariadb-install-db
    lib/            every shared object mariadbd needs but the rootfs lacks
    share/mariadb/  error messages (lc-messages-dir)

``rootfs_i386`` provides ``libc``, ``libm``, ``libz``, ``libgcc_s`` and ``libstdc++``
only. The bundle must therefore carry the remaining entries of the daemon's ``NEED``
list:

- ``libcrypto.so.3``
- ``libssl.so.3``
- ``libpcre2-8.so.0``
- ``libaio.so.1``
- ``libnuma.so.1``
- ``liburing.so.2``
- ``libsystemd.so.0``
- ``libcrypt.so.1``

The ``MariaDB i386 Bundle`` workflow in ``.github/workflows`` assembles the archive
from Debian i386 packages, verifies every ``NEED`` entry and fails the job rather than
publishing an incomplete bundle.

Gamemode directory
------------------

The server reads the user's SA-MP files directly, without copying. The default is
``/storage/emulated/0/PocketSAMP/``; any folder can be chosen from the Dashboard.
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

Native translators
~~~~~~~~~~~~~~~~~~

Two arm64 shared objects must be present in
``thirdparty/PocketSAMP/app/src/main/jniLibs/arm64-v8a/`` before the runtime can start
a guest. They are not in the repository; the ``Native Translators`` workflow builds
them and publishes each as an artifact.

``libqemu-i386.so``
    Required. User-mode translator for 32-bit x86 guests. Both ``samp03svr`` and
    ``mariadbd`` are i386, so without this file neither process starts.

``libproot.so``
    Optional. Provides ``/dev``, ``/proc`` and the gamemode bind mount to the guest.
    The runtime works without it, so a failed PRoot build does not block a release.

Both are command-line programs renamed to ``lib*.so``: Android only executes files
from the APK's ``lib/<abi>/`` directory, and ``nativeLibraryDir`` is where the engines
look. They must be statically linked; a dynamically linked build fails at startup with
``error while loading shared libraries``.

Trigger the workflow, download ``libqemu-i386-arm64`` and ``libproot-arm64``, and copy
the two files into ``jniLibs/arm64-v8a/``. Verify with
``llvm-readelf -h libqemu-i386.so`` that the machine is ``AArch64`` and
``llvm-readelf -l`` shows no ``INTERP`` segment.

Source layout
-------------

::

    com/pocketsamp/server/
        MainActivity.kt          embedded UI entry, permission gate, tab layout
        SampApp.kt               install() and launch() hooks used by PawnMC
        Locales.kt               ID/EN/ES/RU string table
        config/
            Config.kt            server.cfg read and write
            DirectoryManager.kt  gamemode folder, Documents/PawnMC, permissions
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
            Launcher.kt          ELF detection, native exec versus QEMU
            MariaDb.kt           MariaDB lifecycle
            Samp.kt              SA-MP server lifecycle
            Process.kt           process supervision and log streaming
            Unpacker.kt          tar/gzip extraction
        service/
            RuntimeService.kt    foreground service, wakelocks, notification
        ui/
            screens/             Dashboard, Console, Database, Crash logs, Config,
                                 phpMyAdmin browser
            theme/               colour ramp and typography

Licence
-------

- SA-MP binaries and sample gamemodes remain copyrighted by the SA-MP Team.
- PRoot is licensed under GPL-2.0.
- QEMU is licensed under GPL-2.0.
- MariaDB is licensed under GPL-2.0.
