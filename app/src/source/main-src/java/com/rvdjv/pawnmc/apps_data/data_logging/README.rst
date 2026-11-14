Log stores (``data_logging``)
=============================

Three independent histories, one folder, and a shared export helper. It is documented
because the three are easy to confuse: they answer different questions, have different
retention, and live in different files — but all three are called "the log" in the UI.


Which store answers which question
----------------------------------

===========================  ===========================  ==========================================
Store                        File                         Question it answers
===========================  ===========================  ==========================================
``_compiler_store``          ``PawnMC/logs/compiler_logging.db``   "What did the compiler say for this build?"
``_pocket_store``            ``PawnMC/logs/console_logging.db``    "What did the running server print?"
``ActivityJournal``           ``PawnMC/crashreport/activity-*.log``  "What was the app doing before it died?"
===========================  ===========================  ==========================================

The crash journal does not live in this folder (it is in ``data_crash``) but is listed
here because it is the third member of the family and the one that is append-only text
rather than SQLite: it must survive a process that is about to die, which a database
insert may not.


File map
--------

``compiler/Store.kt`` (``_compiler_store``)
    One row per finished build: timestamp, source path and name, exit code, duration,
    compiler version and the full output. Capped at 500 rows, pruned after every write.
    ``Runner`` is its only writer.

``pocket/Store.kt`` (``_pocket_store``)
    The PocketSAMP console history, one row per line, capped at 2,000. Receives lines
    through the console sink installed by ``PocketSampGateway.prepare`` and mirrors the
    noteworthy ones into the activity journal.

``LogExport.kt`` (``_log_export``)
    Export only: builds the header block (date, compiler version, app version, device,
    exit code, duration, line count) that a downloaded or copied log starts with, and
    fills the clipboard in chunks so no device truncates silently. The on-screen log is
    never modified.




Rules that are easy to break
----------------------------

* **The three stores are not merged by design.** A compile log is evidence for a bug
  report; the console is a running service; the journal is a crash timeline. A feature
  that "just reads all logs" must say which one it means.
* **Retention is enforced inside the store.** Pruning happens on write, not on a timer,
  so a store left untouched keeps its last rows indefinitely — and a store written in a
  tight loop pays the prune cost every time.
* **Exports never mutate the stored text.** ``LogExport`` prepends a header; anything that
  needs the raw output reads the row, not the export.
* **The databases live under ``PawnMC/logs``, not in ``files/``.** They are meant to be
  reachable by a user copying them off the device with a file manager.
