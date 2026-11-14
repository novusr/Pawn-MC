Process engines (``engine_handler``)
===================================

How PocketSAMP starts, feeds and stops the three Linux binaries that make up an SA-MP
server on Android: the emulator, the server itself and MariaDB. A phone cannot execute an
i386 binary directly, so every start goes through a launcher decision and a runtime
unpack before the first byte of server output exists.

It is documented because the folder is eight "third_" files with near-identical names and
because the dependency order between them is implicit: choosing the wrong launcher, or
launching before the runtime is unpacked, fails in ways that look like a broken server.


The order of a start
--------------------

1. ``third_launcher.kt`` reads the ELF header of the candidate binary and decides how to
   run it (native i386 under qemu, or directly if the device already matches).
2. ``third_runtime.kt`` makes sure the rootfs and the loader paths exist and assembles the
   environment the launcher needs.
3. ``third_process.kt`` spawns it, drains stdout/stderr line by line and reports the exit
   code — including a readable signal name for the abnormal exits that are really crashes.
4. ``third_server.kt`` is the SA-MP side of that: it picks the binary out of the server
   folder, validates it, and relays its log.
5. ``third_mariadb.kt`` does the same for ``mariadbd``, on its own process runner.


File map
--------

``third_launcher.kt``
    ELF detection (``e_machine``), qemu discovery and the argument list for each kind.
    The only place that decides *how* a binary is executed.

``third_process.kt``
    The generic process wrapper: ``ProcessBuilder``, line reader, exit notification. Every
    engine goes through it, which is why the crash journal's abnormal-exit line is
    produced here once rather than three times.

``third_server.kt``
    The SA-MP engine: chooses ``samp03svr``, validates it is a real ELF (not a placeholder
    from a half-extracted archive), sets up library paths for the staged runtime and
    starts it through ``third_launcher``.

``third_mariadb.kt``
    The database engine. Same shape as the server engine; kept separate because its
    readiness check and its shutdown differ.

``third_runtime.kt``
    The emulated runtime: rootfs location, loader search paths and the private library
    staging that makes a ``noexec`` shared storage workable.

``third_extractor.kt`` / ``third_unpacker.kt`` / ``ArchiveExtractor.kt``
    Getting the archives out of the APK assets onto disk, then unpacking each engine's
    archive. ``ArchiveExtractor`` is the generic archive walker; the two ``third_`` files
    are the per-bundle policies (what counts as "already extracted", what to skip).
    ``assetExtractorInitialised`` in ``RuntimeService`` is the flag the UI reads: until
    the unpack has finished there is nothing to start.

``PhantomGuard.kt``
    Best-effort mitigation of Android 12+'s phantom process killer, which otherwise kills
    the child processes of an app in the background. Every attempt is silent and optional;
    a device where none of them works behaves exactly as before.


Rules that are easy to break
----------------------------

* **Never start an engine before the runtime is unpacked.** The launcher resolves the
  loader from the rootfs; a missing file there surfaces as "Exec format error", which
  looks like a broken binary and is not one.
* **The filename never decides executable kind.** ``kindOf`` reads the ELF header on
  purpose: the extracted template can contain a text placeholder named ``samp03svr``.
* **Exit codes 128+N mean signal N.** Those are the native crashes; the journal line is
  the only trace, because nothing in Kotlin throws.
* **stdout and stderr are merged by the launcher.** A change to that breaks the console
  ordering, which is what the user reads to diagnose a server that will not boot.
* **Processes outlive the activity.** They are owned by the service; an engine started
  from the dashboard keeps running after the screen closes, and that is intended.
