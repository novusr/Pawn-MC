Settings, storage paths and localisation (``data_config``)
============================================================

The one package that decides *where things live* and *what the app remembers about
itself*. Every other package imports from here; this one imports only Android and
``org.json``.

It is documented because the persistence story is genuinely ambiguous. There are three
stores and they look interchangeable from a call site:

=========================================  ==========================================================
Store                                      Role
=========================================  ==========================================================
``SharedPreferences``                      live values, fastest to read, wiped by an uninstall
``content_config.json`` (``ContentConfigFile``)  human-readable mirror, survives an uninstall
``Documents/PawnMC/config/*.toml``         user-editable overrides (explainer, `_internal.toml`)
``workspace.json`` / ``filemanager.json``  remembered folders, via ``PathStore``
=========================================  ==========================================================


File map
--------

``ContentConfig.kt``
    The settings object. Reads and writes ``SharedPreferences`` and mirrors every change
    into ``ContentConfigFile``. A preference-change listener re-reads the mirror so a
    second writer (a settings screen, an external editor of the JSON) is reflected
    everywhere. ``suppressMirror`` stops the setters from re-triggering their own listener
    while the mirror is being written.

``ContentConfigFile.kt``
    The JSON mirror: ``Documents/PawnMC/config/content_config.json``, with fallbacks in
    app files and the private root. This file is what makes a reinstall recoverable — on
    first launch ``ContentConfig`` calls ``restoreFromFile()`` and repopulates the
    preferences. Bump ``SCHEMA_VERSION`` when the meaning of a key changes, not when a key
    is added.

``AppStorage.kt``
    Every path PawnMC owns, in one place: the public ``Documents/PawnMC`` tree, the
    app-private fallback, ``config/``, ``logs/``, ``crashreport/`` and ``workspace/``.
    Also owns the one-time migration away from ``Android/data``.

``PathStore.kt``
    Generic "remembered folder" persistence, one JSON file per surface. Used by the Xed
    workspace and by the PocketSAMP file manager. Dead paths are dropped on read, so the
    restore pass never burns IO on folders that no longer exist.

``AppLocalization.kt``
    The localisation table. Loads the single TOML asset once per process, exposes
    ``get(key, language, default)``, and builds the reverse maps (EN → ID/ES/RU/TL) used to
    translate compiler messages that arrive already in English. Also provides
    ``localizedContext()`` for the two activities that prepare their locale in
    ``attachBaseContext``.

``TomlRuntimeData.kt``
    The minimal TOML reader shared by ``AppLocalization``, ``Explanations`` and
    ``InternDat``. It handles the subset the shipped files use: tables, dotted keys,
    quoted keys, basic and literal strings, integers and booleans. It is not a general
    TOML parser and is not meant to become one.

``SystemNotification.kt``
    The app-wide notification channel and the heads-up used when the compiler has to
    switch versions under the user. Holds an application context attached from the entry
    point, because notifications outlive any screen.


Rules that are easy to break
----------------------------

* **Never read a preference before the entry point runs.** ``ContentConfig`` is created
  on first access and performs the restore in its ``init``; touching it from a content
  provider or a broadcast receiver that runs before ``PawnMCApplication`` will read
  defaults and then mirror those defaults over the saved file.
* **``AppStorage.documentsRoot`` returns ``null`` without all-files access.**
  ``dataRoot`` falls back silently, but anything that must be user-visible
  (``crashReportRoot``, the config mirror) has to handle the ``null`` case explicitly
  rather than writing to a private folder the user cannot find.
* **Language is applied in ``attachBaseContext``**, not in ``onCreate``. A screen that
  resolves strings earlier renders its first frame in the wrong language.
* **The reverse localisation maps are keyed on the *English text*, lowercased.** Compiler
  messages are matched against it verbatim; changing an English string in the table
  silently breaks its translation.
* **One writer per JSON store.** ``PathStore`` is not transactional; the workspace and the
  file manager each own their file, and a third caller should add a surface, not share
  one.
