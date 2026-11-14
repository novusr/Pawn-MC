Runtime service (``service_handler``)
====================================

The foreground service that keeps the server alive. It is the piece that lets a phone
lock its screen while an SA-MP server keeps running, restarts it after the system kills
the process, and discloses what it is doing through a permanent notification.

It is documented because the service is the app's real lifecycle owner — it outlives every
screen and even the task — and because two separate shutdown paths (user stop, system
kill) look identical from the console.


File map
--------

``RuntimeService.kt``
    The service. Owns:

    * the engine objects (server, MariaDB) and their start/stop sequencing;
    * ``serverStatus`` / ``isDbRunning`` as ``StateFlow`` for the UI;
    * ``_logFeed`` (the full, recent lines) and ``streamedFeed`` (the same lines paced for
      the console screen);
    * the console journal file and the sink that mirrors lines into the host app;
    * the notification and the LAN address shown on it;
    * the "recovery budget" — a run that stayed up long enough earns a fresh restart
      allowance, which is what stops a crash-looping gamemode from restarting forever.

    It also attaches an early application context so the ``[Prepare]`` lines produced
    before ``onCreate`` are not lost.


Two things that are easy to confuse
-----------------------------------

**Start vs prepare.** ``assetExtractorInitialised`` is false until the archives are
unpacked. The UI may show a running-looking console while nothing has been started yet;
the dashboard's start button is what actually starts the engines.

**Stop vs kill.** ``stopServers`` is the user's stop: it stops the engines, releases the
locks and updates the notification. A system kill (low memory, phantom-process) bypasses
all of that, leaves the "server was running" preference set, and is recovered on the next
launch. The preference is the marker the restart path reads, so it must be written before
the engines start and cleared as soon as they stop.


Rules that are easy to break
----------------------------

* **Never do engine work on the main thread.** Everything runs in ``serviceScope``; a
  synchronous start blocks the notification and makes the system consider the service
  unresponsive.
* **Locks must be released on every exit path**, including the failure branch — a leaked
  lock is why the next start reports "port already in use".
* **The console sink is optional.** ``consoleLogSink`` is installed by the host; the
  service must run identically without it (standalone builds, tests).
* **Status flows are the only UI contract.** Screens collect ``serverStatus``; they never
  ask the engines directly, because the engines may belong to a process that is not the
  one drawing the screen.
