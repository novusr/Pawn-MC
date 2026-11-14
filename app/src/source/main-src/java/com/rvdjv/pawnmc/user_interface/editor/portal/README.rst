Developer Portal (``editor/portal``)
====================================

A console inside the editor. The user types a command, the portal answers with text or
markdown, and ``pawncc`` commands are handed to the real compiler. It is the only place
in the editor that can run a build with hand-written flags.

It is documented because the folder is six files that all sound like "the console" and
because the split between *parsing* a command and *executing* it is the contract a new
command has to follow.


File map
--------

``PortalMain.kt``
    The overlay: transcript, input line, drag handle, run loop. ``submit()`` is the single
    entry point; it parses, dispatches, appends the result as a ``PortalEntry`` and keeps
    at most one command in flight (``isRunning`` guards re-entry).

``PortalCommandParser.kt``
    Pure parsing, no IO: splits a line on whitespace while honouring quotes, resolves
    aliases, and turns a ``pawncc`` line into a ``PawnccInvocation`` (source file plus
    option list). It is the only file that knows the command vocabulary, so ``help`` and
    completion cannot list a command the dispatcher does not handle.

``PortalDocs.kt``
    The text behind ``help`` and ``docs``: per-command usage and the flag reference. Kept
    apart from the transcript so the wording can change without touching the run loop.

``PortalText.kt``
    Localised labels of the portal (prompt, headers, errors), resolved through
    ``AppLocalization`` with an English default.

``MarkdownOutput.kt``
    Renders an entry whose ``markdown`` flag is set (``help``, ``docs``) as simple spans:
    headings, bullets and inline code, in the same monospace face as the plain output.

``CommandSuggestionGrid.kt``
    The tappable command list, so a phone user never has to type a command name.


Adding a command
----------------

1. add the name to ``PortalCommandParser.commandNames`` (the alias map lives right below
   it);
2. add its usage text in ``PortalDocs``;
3. handle it in the dispatch in ``PortalMain.kt`` — return a plain string for text, or an
   entry with ``markdown = true`` for formatted output.

Nothing else needs to change: ``help``, the suggestion grid and the parser all read the
same list.


Rules that are easy to break
----------------------------

* **Only one command runs at a time.** The guard is ``isRunning``; a command that does not
  complete leaves the portal stuck, so an IO failure must still produce an entry.
* **``pawncc`` compiles through the same path as the Compile button.** The portal does not
  save files first — it compiles what is on disk, on purpose, because the whole point is
  reproducing a build with custom flags.
* **Parsing stays pure.** ``PortalCommandParser`` is unit-tested without Android; putting
  a file read or a compiler call in it makes the parser untestable and the run loop
  impossible to follow.
* **The transcript is in memory only.** It is cleared when the overlay closes and is not
  written to the compiler log; a build run from here appears in the logs only through
  ``Runner``.
