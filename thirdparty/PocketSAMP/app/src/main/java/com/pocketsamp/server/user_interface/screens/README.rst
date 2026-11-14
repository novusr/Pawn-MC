PocketSAMP screens (``user_interface/screens``)
==============================================

One file per tab of the runtime UI: dashboard, console, files, database, config, crashes
and help. The tab switching, the floating rail and the phpMyAdmin overlay live in
``MainActivity``, not here.

It is documented because seven self-contained screens with no shared layer is exactly
where a shared pattern gets re-invented per file — and because "where does this screen read
its state from" has a different answer for almost every one of them.


File map and state source
-------------------------

``Dashboard.kt``
    Server status, LAN address, start/stop. Reads ``RuntimeService.serverStatus`` and
    ``lanIp``; starts and stops through the activity's callbacks.

``Console.kt``
    The live log. Reads ``RuntimeService.streamedFeed`` (the paced copy of ``_logFeed``),
    colours lines by keyword, and exports or clears through the service.

``FileManager.kt``
    The file manager over the server folder. Reads its path from ``_DirectoryManager``;
    operations go through the ``configuration`` package.

``Database.kt``
    Schemas, tables, a free SQL field and import. Reads through ``DatabaseManager`` and
    the ``isDbRunning`` flow.

``Config.kt``
    The read-only ``server.cfg`` view. Reads ``Reader``/``ConfigParameters``.

``CrashInfo.kt``
    The crash list and detail, used both as a tab and as the dialog shown by the isolated
    ``:crash`` process. Reads through ``crash_handler.Manager`` and shows the ``.dmp``
    paired with a report.

``Help.kt``
    The static guide, generated from ``HelpContent`` per language. No state at all.

``PhpMyAdmin.kt``
    The WebView overlay, opened from the database tab when the engine is running.


Rules that are easy to break
----------------------------

* **Screens never start an engine.** They call the activity/service; the service owns the
  process lifecycle. A screen that starts ``Server`` directly leaves the notification and
  the recovery state out of sync.
* **Log lines arrive paced.** ``streamedFeed`` exists so a burst of output scrolls
  readably; collecting ``_logFeed`` instead makes the console jump while a gamemode boots.
* **Every screen must work while the engine is stopped.** The dashboard is the only one
  that offers start; the others must render an empty or "offline" state rather than
  assume a running server.
* **Localised strings come from ``Locales``**, resolved at render time. A ``remember``
  around a translated label freezes it across a language change.
