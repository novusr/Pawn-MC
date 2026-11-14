Crash reports (``data_crash``)
==============================

Everything that turns a fatal error into a file: the process-wide handler, the real
``.dmp`` writer, the text report beside it, and the activity journal that explains how
the app got there.

It is documented because three different writers produce files in the *same* folder
(``Documents/PawnMC/crashreport/``) and none of them says so in its own name — and
because the handler runs on a dying process, where the usual rules about IO do not hold.


What lands in ``Documents/PawnMC/crashreport/``
-----------------------------------------------

==========================================  =============  ==========================================
File                                        Written by     Content
==========================================  =============  ==========================================
``crash_<time>_<id>.dmp``                   ``Minidump``   real ``MDMP`` container, see below
``crash_<time>_<id>.txt``                   ``CrashHandler``  the same crash as parseable text
``activity-<session>.log``                  ``ActivityJournal``  append-only timeline of the run
``.acknowledged``                           ``CrashReportCard``  id of the last crash the user saw
==========================================  =============  ==========================================

The ``.dmp`` and the ``.txt`` share their base name; that pairing is how the UI finds one
from the other, so renaming either breaks the link.


File map
--------

``CrashHandler.kt``
    Installed from ``PawnMCApplication`` before any activity exists. Writes the dump and
    the text report, flushes the journal first, then chains to the previous handler so
    upstream behaviour (and logcat) still happens. Also the parser for the text format,
    used by the notice card and by PocketSAMP's history.

``Minidump.kt``
    The binary writer. Emits header, stream directory and the streams every reader
    supports: ``SystemInfo``, ``MiscInfo``, ``Exception``, ``ThreadList``,
    ``ModuleList`` and ``CommentW`` with the full readable report embedded (and appended
    verbatim at the end of the file, so it can be read on the phone with no tooling).
    Memory regions are deliberately not captured — see the class comment.

``ActivityJournal.kt``
    The timeline: append-only, one line at a time, size-capped, written on a single
    background thread, degrading to a no-op when storage is unavailable. This is the file
    that answers "what was the app doing" and the only one that can be written *before*
    the crash.


Where the pieces plug in
------------------------

* ``PawnMCApplication`` installs the handler and starts a journal session.
* ``MainActivity``, ``SettingsActivity``, ``EditorScreen`` and ``MainScreen`` journal
  screen and document changes.
* ``Runner`` journals compiler starts and exits; ``_compiler_store`` journals the finished
  build; ``_pocket_store`` samples the server console.
* ``PocketSampCrashBridge`` (in ``newer-src``) routes PocketSAMP's own handler into
  ``CrashHandler``, so one exception produces one dump and not two.

That last point is the important one: PawnMC's handler is installed first and runs first,
then chains into PocketSAMP's. Both can see the same exception, so the runtime's handler
guards against writing a second report.


Rules that are easy to break
----------------------------

* **Nothing here may throw.** Every write is wrapped, and a failure is logged rather than
  propagated; the one thing a crash handler must never do is add a second crash.
* **Read before write.** The journal is flushed *before* the dump is built, because
  building the dump takes the longest and a platform-level kill can arrive at any point.
* **The public folder needs all-files access.** Without it the dump goes to
  ``files/crashreport`` and is moved out by ``Minidump.migratePrivateDumps`` on the next
  launch. Never assume the public path exists.
* **The text format is an interface.** ``Manager`` in PocketSAMP and the notice card both
  parse it with the ``===PAWNMC_CRASH_FIELD===`` delimiter; adding a field means appending
  a block, never reordering the existing seven.
