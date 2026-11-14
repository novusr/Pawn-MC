Crash handling (``crash_handler``)
=================================

Upstream PocketSAMP's own crash path: an uncaught-exception handler, a report file, and a
dialog shown in an isolated process so the report survives the app being dead.

It is documented because, inside PawnMC, this folder no longer decides *where* reports go
— the host does — and reading only these files gives the wrong answer about the file
layout. In short: the dialog is still PocketSAMP's, the files are PawnMC's.


File map
--------

``Handler.kt``
    The ``Thread.UncaughtExceptionHandler``. Formats the report, asks ``Manager`` to save
    it, and opens ``CrashDialog`` in the ``:crash`` process before killing this one. It
    also guards against writing twice: inside PawnMC, the host's handler is installed
    first and chains into this one, so both see the same exception. ``beginReport()`` is
    what makes exactly one of them write.

``Manager.kt``
    The store. Two things decide where a report lands:

    * a **host reporter** may be installed through ``installReporter``; inside PawnMC it
      writes the ``.dmp`` and the text report into ``Documents/PawnMC/crashreport`` and the
      private folder is skipped;
    * without one (standalone PocketSAMP, unit tests) reports go to
      ``files/crash_reports/crash_*.txt`` exactly as upstream.

    ``getAllCrashes`` merges both locations, so the history screen shows reports written
    before the host integration too. ``dumpFor`` pairs a report with its ``.dmp`` by
    crash id.

``Dialog.kt``
    ``CrashDialog``: a minimal activity in the ``:crash`` process that shows the report and
    offers restart/copy/delete. Isolated on purpose — the main process is being killed at
    the same moment.

``RuntimeJournal.kt``
    The seam the runtime uses to add its own lines to the host's activity journal (server
    start failures, abnormal exits). A no-op when nothing is installed.

``Report.kt``
    The data class shared by all of the above.


Rules that are easy to break
----------------------------

* **One report per crash.** ``beginReport()`` exists for the chained-handler case; removing
  it produces two dumps for one exception, written into the same folder.
* **The text format is an interface.** ``Manager`` writes the
  ``===POCKETSAMP_CRASH_FIELD===`` layout that the upstream history reads; the host writes
  a compatible one. Adding a field means appending, never reordering.
* **The dialog must not assume the main process is alive.** Everything it shows comes from
  the saved file, not from in-memory state.
* **A host reporter is optional.** Never make it required: the module must still build and
  behave as a standalone app.
