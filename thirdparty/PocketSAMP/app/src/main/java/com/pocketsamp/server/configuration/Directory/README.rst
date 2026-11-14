Server directory (``configuration/Directory``)
=============================================

The folder the server runs in: how it is chosen, remembered, validated and created.

It is documented because this is a one-file subpackage that looks like it belongs with the
other configuration files but holds a different kind of thing — the *storage permission*
and the *remembered path*, neither of which is a ``server.cfg`` concern.


File map
--------

``Manager.kt`` (``_DirectoryManager``)
    The server folder. Resolves the default location (``/storage/emulated/0/PocketSAMP``
    or the PawnMC folder, depending on the flavor's layout), the remembered custom one,
    the all-files-access check, and the test that decides whether a folder is a server
    directory. Also the entry point for the folder picker and for the permission intent.

``PathStore.kt``
    The remembered path as JSON in ``Documents/PawnMC/filemanager/filemanager.json``. A
    deliberate re-implementation of the host's ``data_config/PathStore``, since the module
    does not link PawnMC's code. Dead paths are dropped on read.


Rules that are easy to break
----------------------------

* **Permission is checked here, not by callers.** A failed check must return "no
  permission" so the UI can prompt; an empty file list is indistinguishable from an empty
  folder and produces the wrong fix from the user.
* **The remembered path and the live preference are two stores.** ``Manager`` reads the
  preference on every start and the JSON is only a reinstall mirror; writing one without
  the other splits the two after a reinstall.
* **The JSON format is shared with the host app.** Keep it identical or one of the two
  surfaces loses its folder without any error.
