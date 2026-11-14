Configuration (``configuration``)
================================

Reading and writing what makes a folder a server folder: the directory itself
(``Directory/``), ``server.cfg`` (``Reader`` / ``Config`` / ``Writer``) and the remembered
path.

It is documented because ``server.cfg`` is parsed here *and* described in the host app's
``data_syntax/ServerConfig.kt`` — two files, two purposes, easily mistaken for duplicates —
and because the remembered server folder is stored twice, in different places, for two
different reasons.


File map
--------

``Config.kt``
    ``Configuration``: the ``server.cfg`` file as a typed object, with the defaults PawnMC
    ships. A folder without a ``server.cfg`` still shows and writes a complete
    configuration instead of an empty form.

``Reader.kt``
    Reads an existing ``server.cfg`` into ``Configuration``. Tolerant on purpose: a
    malformed line is skipped, not fatal, because the server itself is tolerant too.

``Writer.kt``
    Writes a ``Configuration`` back. When the file does not exist it first writes the full
    default file, so a save never leaves half a configuration next to nothing.

``ConfigParameters.kt``
    The parameter table used by the read-only config screen: name, accepted value shape and
    default. This is the *display* side; ``ServerConfig`` in the host app is the
    *highlighting* side and they are kept separately on purpose (one is prose, the other
    is spans).

``Directory/_DirectoryManager.kt``
    The server folder: the default location, the remembered one, the all-files-access
    check and the "is this a server directory" test. Also the entry point for opening
    Android's folder picker.

``Directory/PathStore.kt``
    The remembered path, in ``Documents/PawnMC/filemanager/filemanager.json``. Same file
    format as the host app's ``data_config/PathStore``, re-implemented because this module
    does not link the host's code. Dead paths are dropped on read.


The two ways the folder is remembered
-------------------------------------

* **SharedPreferences** (``pocketsamp_prefs`` / ``custom_server_directory``) — the live
  value the service reads on every start.
* **``filemanager.json``** — the mirror that survives an uninstall, restored by the file
  manager.

A change to the server folder must write both, or the runtime starts in the old folder
after a reinstall while the file manager shows the new one.


Rules that are easy to break
----------------------------

* **The two ``PathStore`` implementations must stay compatible.** They write the same
  file; a format change in one has to be made in the other.
* **``server.cfg`` is user-editable text.** Never rewrite the whole file when only one key
  changed where it can be avoided; comments and ordering belong to the user.
* **All-files access is checked here, not in the screens.** A folder that cannot be read
  must surface as "no permission" so the UI can ask, not as an empty file list that looks
  like an empty folder.
