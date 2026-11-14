Main interface (``user_interface/main``)
========================================

The first screen: one long scrolling page that compiles a file, shows the compiler output
and offers the way into the editor, Settings and PocketSAMP. It also hosts the activity
that owns *every* screen in the app.

It is documented because the folder mixes three layers — the activity, the screen, and a
view model that is really the app's session — and because the one-page layout hides the
fact that this is where almost all app-level wiring happens.


File map
--------

``MainActivity.kt``
    The host activity. Beyond showing ``MainScreen`` it is the app's entry point for:
    compiler host attachment, background startup loads, the PocketSAMP gateway, the
    permission prompts, the deep links that open a file or a workspace in Xed, and the
    decision of which screen is composed. It also owns the editor view model, deliberately
    scoped to the activity so an open workspace survives leaving the editor.

``MainScreen.kt``
    The page. Sections, in order: crash notice, header with the action menu, compile card,
    notices, compiler output with its scroll sidebar, settings guide. It also holds the
    composables that are shared with other screens (``OutputPanel``,
    ``StatusChip``, ``ScreenHeader``, ``explainHighlightedText``).

``MainViewModel.kt``
    Session state of the main screen: the selected file, the last output, the compile
    state, the persistence of "last selected file", the PocketSAMP console mirror and the
    filesystem preparation. A compile started here completes even if the user leaves the
    screen.

``MainNotification.kt``
    The ongoing session notification (compile running, server running). Silently does
    nothing when the notification permission was refused.

``StoragePermission.kt``
    The all-files-access request and the two launch paths: the manage-storage intent on
    API 30+ and the legacy read/write permissions below it.

``MainScreenPreviews.kt``
    Preview-only wiring, so every screen part can be rendered in Studio without the
    activity.

``SettingsPromo.kt``
    The guide under the output: one row per settings section, each opening Settings at
    that section. ``SettingsTarget`` lives here too, because it is the vocabulary both
    screens share.

``CrashReportCard.kt``
    The notice shown once after a crash, with the dump name, folder and a copy-path
    action. Reads the report folder directly; there is no index to keep in sync.

``FileBrowserDialog`` (``user_interface/filebrowser``)
    The in-app browser used to pick the script to compile. Switched between file and
    folder mode by ``FileBrowserMode``.


Rules that are easy to break
----------------------------

* **A compile must not be abandoned by navigation.** ``MainViewModel`` is activity-scoped
  and its compile job lives in its own scope; a screen-scoped job would be cancelled the
  moment the user opens the editor.
* **``headerActionLocked`` guards the header buttons**, not the compile. It exists to stop
  double taps from opening two screens; it is reset on the activity's ``ON_RESUME``, not
  on composition, which is why it is re-set from ``editorOpen``.
* **The startup work in ``MainActivity`` is on ``Dispatchers.IO`` on purpose.** Loading
  the localisation, symbol and explanation tables synchronously was what made the first
  frame take seconds on a low-RAM device.
* **The output scroll state is animated on every change of ``viewModel.outputText``.**
  Replacing that with a lazy list changes the scrollbar's contract: ``OutputPanel`` reads
  a plain ``ScrollState``.
* **``MainScreen`` is composable from previews and tests.** Keep new state in the
  view model or behind defaulted parameters, not in ``MainActivity``.
