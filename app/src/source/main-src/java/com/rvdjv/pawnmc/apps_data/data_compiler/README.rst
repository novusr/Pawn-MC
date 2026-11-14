Compiler layer (``data_compiler``)
==================================

The Kotlin half of PawnMC's compiler: everything between the screens and the native
libpawnc builds staged in ``Documents/PawnMC/library/<abi>/``. The native half lives in
``native/cpp`` and has its own README; this folder never calls a ``pc_*`` symbol
directly, it goes through the JNI surface declared in ``NativeBridge``.

It is documented because the boundary here is genuinely ambiguous: three types look like
"the compiler" from outside (``Compiler``, ``Runner``, ``NativeBridge``), the explanation
feature is split across four files, and the "ignore case" feature rewrites the user's
files on disk.


Who calls whom
--------------

::

    MainViewModel / EditorScreen
        |
        v
    Compiler          facade - the only type outside this folder should name
        |
        v
    Runner            version choice, arguments, native call, output parsing, log row
        |    \
        |     `--> CaseConversion   (rewrites the project folder, only on request)
        v
    NativeBridge      loads the bridge library, declares the JNI entry points


File map
--------

``Compiler.kt``
    The facade. Every public method is a one-line forward to ``Runner``. It exists so the
    view models and the editor never depend on how a compile is executed, and so the
    version-autoswitch helpers can be reached from Settings without pulling the runner in.

``Runner.kt``
    The actual sequence of one compile: pick the version (forced mode, detected ABI,
    fallback after a failed load), extract the libraries on first use, build the argument
    array (``-D`` working directory, includes, user flags), call the native ``compile``,
    parse ``"Exit code: N\\n<log>"``, record the row in ``data_logging`` and apply the
    one-shot auto assists. Change this file when a compile behaves wrongly but the
    compiler itself is fine.

``NativeBridge.kt``
    The JNI boundary and nothing else. Declares the ``external`` functions, loads the
    bridge library from ``nativeLibraryDir`` (the one that owns ``JNI_OnLoad``), and
    explains why a staged ``libpawnc3*.so`` in Documents cannot be loaded. The per-version
    libraries are *dependent*: they export ``pc_*`` for the compiler, not ``Java_*`` for
    the app, so this object dispatches by version instead of loading one library per
    version.

``Detection.kt``
    Finds a native compiler that ships next to the opened script (the ``pawno``/``qawno``
    layout many users copy from Windows) and classifies it by product version and size —
    this is what makes "auto" mode possible when the file is not one of ours.

``Names.kt``
    Every literal name of the layer (binary names, folder names, extensions, include path
    variants) in one place, so ``Detection`` and ``Runner`` cannot drift apart.

``Explainer.kt``
    Turns raw compiler lines into structured diagnostics: the ``path(line) : error 017:``
    shape is parsed here, and each hit is written to the diagnostics store for the editor.

``Explanations.kt``
    The prose half of the explainer: loads ``_explainer.toml`` (asset default, override in
    ``Documents/PawnMC/config/``) once per process and looks a message code up in the
    user's language.

``CaseConversion.kt``
    The "ignore case" feature: rewrites a project's file and folder names plus its
    ``#include`` directives to match the casing Pawn expects, after making a backup. Its
    status enum is the contract the settings screen reports.

``DiagnosticsStore`` / ``ExplainInjector`` (editor side)
    Not in this folder: the store's JSON is written by ``Explainer`` and read by
    ``user_interface/editor/xedapi/ExplainInjector.kt``, which places the ``[explain]``
    comments in the open document.


Rules that are easy to break
----------------------------

* **No ``System.loadLibrary`` outside ``NativeBridge``.** The version library is checked
  for a matching ELF ABI and staged before loading; a direct load bypasses that check and
  produces the "library not found" failures the readiness report exists to prevent.
* **Shared storage can be mounted ``noexec``.** The staged library is copied into the app
  code cache before loading. Handing a Documents path straight to the loader works on
  some devices and fails on others, which is the worst kind of bug to chase.
* **A compile never runs on the UI thread.** ``MainViewModel`` and the editor both
  dispatch to ``Dispatchers.IO``; ``Runner`` assumes it is already off the main thread.
* **``Runner`` owns the compile-log row.** Recording a compile anywhere else gives two
  entries per build in the logs screen.
* **``CaseConversion`` writes to the user's project.** It is the only type in this folder
  that may modify files the user owns; keep it opt-in and keep the backup.
