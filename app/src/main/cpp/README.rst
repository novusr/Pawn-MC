C++ Native Compiler Bridge
==========================

Everything in ``app/src/main/cpp`` is PawnMC's own code. The Pawn compiler *itself* is not
here: it is compiled straight from the ``compilers/pawnc-3.10.7`` and
``compilers/pawnc-3.10.11`` git submodules, and this directory only supplies the
``pc_*`` back ends the compiler calls out to plus the JNI surface the Kotlin layer talks
to.

Two shared libraries are produced from this folder:

===============  =================  ==================================================
Library          Submodule          Loaded from Kotlin as
===============  =================  ==================================================
``pawnc3107``    ``pawnc-3.10.7``   ``System.loadLibrary("pawnc3107")``
``pawnc31011``   ``pawnc-3.10.11``  ``System.loadLibrary("pawnc31011")``
===============  =================  ==================================================

Both are built from *the same* bridge sources, so a fix here applies to both compiler
versions at once and the two builds cannot drift apart.


File map
--------

``Compiler.cpp``
    The JNI layer, and the only translation unit that carries ``JNIEXPORT``. It marshals
    the Kotlin ``Array<String>`` into the ``char**`` ``pc_compile`` expects, decides which
    argument is the source file, appends the ``-D<working dir>`` the compiler needs to
    resolve project-relative ``#include`` paths, and asks the diagnostics layer to run the
    build. It also implements ``JNI_OnLoad``.

``Bridge.h``
    The contract *between* the C++ files: ``MC_is_pawn_source_argument``,
    ``MC_compile_on_worker_thread``, the ``MC_buffer_*`` accessors, the ``MC_source_*``
    and ``MC_files_*`` helpers. Kept free of ``std::string`` and ``<vector>`` so including
    it never drags STL headers into a file that does not want them.

``Compiler.h``
    The shared defines: the ``mkstemp`` template (``MC_CACHE``), the worker stack size
    (``MC_STACK``), the log macros, and the output ceilings (``MC_MAX_WARNINGS``,
    ``MC_MAX_ERRORS``, ``MC_MAX_BUFFER_SIZE``).

``Diagnostics.cpp``
    ``MC_is_pawn_source_argument`` (what counts as a ``.pawn`` / ``.pwn`` / ``.p`` /
    ``.inc`` path rather than a flag) and ``MC_compile_on_worker_thread``, which runs
    ``pc_compile`` on a thread with the 8 MB stack the recursive Pawn parser needs. Every
    failure to create that thread falls back to a direct call, so a small stack costs speed
    rather than the whole build.

``Buffer.cpp``
    The compiler's two reporting sinks, ``pc_printf`` and ``pc_error``. It owns the output
    and error buffers, the warning/error counters, and the truncation notices that keep a
    single missing ``#include`` from filling the log with thousands of cascading errors.
    ``MC_buffer_result`` is what produces the ``"Exit code: N\n<log>"`` string that
    ``Runner.kt`` parses.

``Source.cpp``
    ``pc_opensrc`` and friends. Scripts are cached in an ``unordered_map`` for the duration
    of one compile, because a project re-reads the same ``#include`` many times and every
    read on Android shared storage is a round trip to the FUSE layer. The cache is cleared
    by ``MC_runner_compile`` at the start of each build, so a stale buffer can never leak
    into the next one.

``Files.cpp``
    The three stream back ends: ``pc_openasm`` (through the submodule's ``MEMFILE``),
    ``pc_openbin`` (a 1 MB-buffered ``FILE``), and ``pc_createtmpsrc`` (``mkstemp`` in the
    app cache, for the preprocessor's scratch file).

``CMakeLists.txt``
    Builds both libraries, configures ``version.h`` from each submodule, and lists the
    compiler sources. The bridge sources are collected once in ``MC_BRIDGE_SOURCES`` and
    linked into both targets.


Call flow of one compile
------------------------

1. Kotlin calls ``Runner.compile(args)``, which resolves to
   ``Java_com_rvdjv_pawnmc_data_compiler_Runner_compile`` in ``Compiler.cpp``.
2. The previous build's buffers and source cache are dropped, and
   ``n_apply_cache_dir`` asks the JVM for ``Context.getCacheDir()`` so ``mkstemp`` has a
   writable location.
3. ``MC_is_pawn_source_argument`` scans the arguments from the end to find the script
   being compiled, and ``dirname`` of that path is appended as ``-D``.
4. ``MC_compile_on_worker_thread`` starts ``pc_compile`` on a ``pthread`` with
   ``MC_STACK`` bytes of stack and joins it.
5. During the build the compiler calls back into ``Source.cpp`` (reads),
   ``Buffer.cpp`` (diagnostics) and ``Files.cpp`` (``.asm`` / ``.amx`` / scratch output).
6. ``MC_buffer_result`` formats the exit code and the captured log, and the JNI layer
   turns it into a ``jstring`` for ``Runner.parseCompilerOutput``.


Adding a back end
-----------------

The Pawn compiler owns the list of ``pc_*`` functions it expects; a new one is added by:

1. implementing the ``pc_*`` symbol in the file whose responsibility it matches, or in a
   new file if it opens a third kind of stream;
2. declaring the ``MC_*`` entry point in ``Bridge.h`` if another bridge file has to call it;
3. adding the new ``.cpp`` to ``MC_BRIDGE_SOURCES`` in ``CMakeLists.txt``.

Because both libraries share those sources, there is no per-version copy to keep in sync.
