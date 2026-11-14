Data layer (``apps_data``)
==========================

Everything the UI does not have to know about: configuration and storage paths, the
compiler bridge, the symbol tables, the log stores, the crash writer, the update check and
the embedded runtime gateway. Screens call into this package; this package never imports
a screen.

It is documented because the folder is where the app's *hard boundaries* are enforced —
public storage vs app-private, Kotlin vs native, host app vs Sora/PocketSAMP — and a new
file placed in the wrong subpackage silently crosses one of them.


Subpackages
-----------

``data_compiler``
    Kotlin side of the Pawn compiler and the explain feature. See its README.

``data_config``
    Preferences, the JSON mirror, storage paths, localisation and the TOML reader. See its
    README — this is the subpackage every other one imports.

``data_syntax``
    Pawn and ``server.cfg`` vocabulary loaded from ``native/data/_internal.toml``.

``data_logging``
    The compiler-log and server-console SQLite stores plus the export helper. See its
    README for which store answers which question.

``data_crash``
    The crash handler, the ``.dmp`` writer and the activity journal. See its README.

``data_library``
    ``LibraryStore``: aspects of the per-ABI compiler archives out of the APK into
    ``Documents/PawnMC/library/<abi>/``, with ELF header verification and an idempotent
    re-check.

``data_update``
    ``UpdateManager``: reads the update manifest, compares versions and writes the
    version marker the updates screen reads.

``data_runtime``
    ``PocketSampGateway`` — the *only* place the shared source set reaches the runtime
    module, through a hook interface plus a reflective lookup. See below.


The flavor boundary (``data_runtime``)
--------------------------------------

The app is built for two flavors. Only the modern one links the PocketSAMP module; the
Nougat flavor has no runtime at all and must still compile. A source set cannot see a
sibling flavor's classes, so:

* the shared code names only ``PocketSampGateway.PocketSampHook``;
* each flavor supplies an implementation in its own source set;
* the gateway resolves the implementation reflectively, once per process, and skips the
  lookup entirely on the legacy application id.

Anything that needs a PocketSAMP *type* in shared code is in the wrong place.


Rules that are easy to break
----------------------------

* **No UI imports.** If a file here needs a ``Composable``, it belongs in
  ``user_interface`` instead.
* **Public storage is optional.** ``AppStorage.documentsRoot`` is ``null`` before
  all-files access is granted; every writer either handles that or has a documented
  private fallback.
* **The data layer owns its notifications.** ``SystemNotification`` is attached from the
  entry point, not from a screen, because the compiler can finish while no screen is up.
* **Load heavy tables off the main thread.** The localisation, symbol and explanation
  tables are warmed up from ``MainActivity``'s startup scope; a subpackage that loads its
  table eagerly in a top-level initialiser would put that cost back on the first frame.
