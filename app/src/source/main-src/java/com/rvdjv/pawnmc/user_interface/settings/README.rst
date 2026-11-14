Settings screen (``user_interface/settings``)
=============================================

Three files, one screen: an activity that can also be the app's entry point, a view model
over ``ContentConfig``, and one very long scrolling page.

It is documented because the activity is a second entry point into the app with its own
startup work, and because the "restart to apply" flow — the part users notice — spans all
three files and the preferences file.


File map
--------

``Activity.kt``
    ``SettingsActivity``. Besides showing the screen it:
    applies a queued compiler switch from the previous process (``PENDING_SWITCH_FILE``)
    *before* anything reads the config, loads the explanation table (Settings can be the
    launcher), writes the version marker, and prepares the locale in
    ``attachBaseContext``. The focus section arrives as an enum *name* in an intent extra
    so callers do not have to import the enum.

``ViewModel.kt``
    A thin live view over ``ContentConfig``. Every property delegates to the shared
    settings object, so a value changed here is visible to the compiler and the editor
    without a restart; the view model exists for the few derived values (label lookups,
    include-path lists) rather than for its own state.

``Screen.kt``
    The page: categories, navigation rows, dialogs and the "Thank you" notes. It is one
    ``verticalScroll`` column, not a lazy list, because the scroll offset is used to jump
    to ``SettingsTarget`` sections on open.


The compiler-switch restart
---------------------------

Changing the compiler version cannot be done in place: the native library is loaded once
per process. The flow is therefore a small state machine spread over this folder:

1. the screen writes ``pending_compiler_switch.json`` with the requested version and mode;
2. it restarts the process;
3. ``SettingsActivity.onCreate`` — or the main entry point — replays the request through
   ``applyPendingCompilerSwitch`` before the first config read;
4. the file is deleted; if the new library cannot be loaded, the fallback in ``Runner``
   picks the other version and records why.

A change to any step silently reopens every install with the old compiler.


Rules that are easy to break
----------------------------

* **New settings go into ``ContentConfig``**, not into the view model. A preference kept
  only in the view model is not mirrored to ``content_config.json`` and is lost on an
  uninstall.
* **Localise at render time, not at remember time.** The screen re-reads ``localizer.get``
  on every pass so a language switch is visible immediately; a ``remember { }`` around a
  translated string freezes it until the next launch.
* **The activity is an entry point.** It must not assume ``MainActivity`` ran first: the
  tables it needs are loaded in its own ``onCreate``.
* **Section jumps use ``SettingsTarget`` from ``main/SettingsPromo.kt``.** Adding a
  category means adding a target, or the guide card cannot reach it.
