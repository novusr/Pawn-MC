Editor languages (``editor/syntax``)
====================================

Sora ``Language`` implementations: the Pawn highlighter/completion and the ``server.cfg``
one, plus the selector that maps an open file to the right language. The *vocabulary*
they highlight comes from ``apps_data/data_syntax``; this folder only turns it into
spans and completion candidates.

It is documented because the selection rule is not obvious (Pawn is the default and the
config language is the exception), and because the analyzers run on a background parse
thread whose contract is easy to violate.


File map
--------

``CodeLanguages.kt``
    The selector. ``forFile(file, language)`` returns ``ServerConfigLanguage`` for
    ``.cfg`` / ``.log`` and ``CodeLanguage`` (Pawn) for everything else. Called once per
    active file; the editor screen installs the result only when the instance changed.

``CodeLanguage.kt``
    The Pawn language: wiring of the analyzer and the completion provider for Sora.

``CodeAnalyzer.kt``
    Tokenises a Pawn buffer into highlight spans: comments (including the ``[explain]``
    markers the injector adds), strings, numbers, preprocessor lines, keywords and the
    known natives/callbacks from the symbol table.

``CodeSymbols.kt``
    Completion candidates: keywords, natives and callbacks filtered by the text before
    the caret, plus the include hint when a used native is not imported.

``ServerConfigLanguage.kt`` / ``ServerConfigAnalyzer.kt``
    The ``server.cfg`` pair. They share nothing with the Pawn pair except Sora's
    ``Language`` interface: a config file is line-oriented key/value text, not code.


Rules that are easy to break
----------------------------

* **Analyzers must not touch Android resources or the disk.** They run on Sora's parse
  thread, repeatedly, while the user types; the symbol tables are loaded once and then
  read-only.
* **Return only what changed, when Sora asks for a range.** Re-analysing the whole buffer
  on every keystroke is what makes a 4,000-line script stutter.
* **The language is chosen by extension, not by content.** A ``.p`` file containing a
  config is still highlighted as Pawn; that is intentional and the selector is the only
  place to change it.
* **Do not cache an analyzer instance across files.** Each editor owns its analyzer
  through the ``Language`` instance; sharing one leaks one file's spans into another.
