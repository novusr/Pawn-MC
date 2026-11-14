Pawn symbol tables (``data_syntax``)
====================================

The *data* behind the editor's highlighting and completion. The highlighters themselves
live in ``user_interface/editor/syntax``; this folder only knows what a Pawn symbol,
keyword or ``server.cfg`` key is, where its text comes from, and how to translate it.

It is documented because the folder has one real source of truth spread over two data
files and three shapes of the same concept (``_item``, ``PawnInternalSymbol``,
``PawnIncludeSymbols``), and nothing in the code says which one a new feature should use.


Source of truth
---------------

``native/data/_internal.toml`` is the only place symbol and keyword text is written.
It ships in the APK as an asset and is also on disk at ``native/data/`` in the repo, so
the two cannot drift. Each entry carries an English description plus optional ID / ES /
RU / TL variants; a missing translation falls back to English rather than to an empty
string.

``Documents/PawnMC/config/`` can override the shipped files, which is what lets a user
add a library the app does not know about yet.


File map
--------

``Registry.kt``
    The vocabulary: ``PawnItemKind`` (keyword, directive, type, constant, function,
    callback, operator) and ``_item``, one row of the table with
    ``descriptionFor(language)`` as the only way to read its text.

``InternDat.kt``
    Loader for ``_internal.toml``. Normalises the two key spellings the file has used
    (``en = …`` and the older ``local_en = …``), builds ``InternDatset`` and loads it once
    per process. ``InternDat.dataset()`` is what every other type here calls.

``Index.kt``
    A *view* over the dataset: symbols grouped by the SA-MP include that declares them
    (``PawnIncludeSymbols.natives`` / ``.forwards``), for the "which include do I need?"
    path in the completion popup. It holds no data of its own.

``ServerConfig.kt``
    The ``server.cfg`` vocabulary: the extensions the language serves (``cfg`` and
    ``log``) and every key with its accepted values and default, checked against the
    server's own ``AddVariable`` registrations. Shared by the highlighter and the
    completion popup so the two cannot disagree about a key.


Rules that are easy to break
----------------------------

* **Add a symbol to the TOML, never to Kotlin.** A keyword compiled into the app cannot be
  overridden by the user and is invisible to the "your include is missing" path.
* **``_item`` and ``PawnInternalSymbol`` are not interchangeable.** ``_item`` is what the
  highlighter matches by name; ``PawnInternalSymbol`` carries the owning include and the
  four translations used by the completion and the include hint. Converting one to the
  other loses a field.
* **Name matching is case-sensitive.** Pawn is; the highlighter relies on it to tell
  ``MAX_PLAYERS`` from a user identifier called ``Max_Players``.
* **Don't load the dataset on the UI thread.** Parsing the TOML is warmed up in the
  background from the app entry point; a screen that calls ``InternDat.dataset()`` before
  that finishes pays for it in a dropped frame.
