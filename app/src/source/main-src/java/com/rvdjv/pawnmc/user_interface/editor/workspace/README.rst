Workspace session (``editor/workspace``)
=========================================

The model behind "several files and folders open at once", Visual Studio style. Nothing
in this folder draws a screen except ``Panel.kt``; everything else is state, scanning and
persistence that the editor screen reads.

It is documented because the ownership is ambiguous: the session is a ``ViewModel``, but
it is *not* the editor's view model (it is reachable from ``EditorViewModel.workspace``),
and it persists to ``Documents/PawnMC`` itself rather than through ``data_config``.


Responsibilities
----------------

``Session.kt`` (``WorkspaceSession``)
    The single source of truth for open folders, open tabs and the active document.
    Owns:

    * ``openWorkspaces`` — at most ``MAX_OPEN_WORKSPACES`` folders, each with its scanned
      tree and its documents;
    * ``documents`` / ``activePath`` — the tabs, with dirty flags;
    * save paths (one file, all files, one workspace, all workspaces);
    * workspace search and replace, with hard caps on entries, depth and hits;
    * persistence of the tab list, so re-entering the editor reopens what was open;
    * the folder watchers that refresh the tree after an external change.

    The scan caps (``MAX_WORKSPACE_ENTRIES``, ``MAX_WORKSPACE_DEPTH``, ``MAX_SEARCH_HITS``)
    are not tunables: they exist so opening a phone's root folder cannot hang the app.

``Panel.kt``
    The floating explorer panel: open editors, the folder tree of the active workspace and
    the search hits, all in one ``LazyColumn``. It renders session state and reports taps;
    it never changes a document itself.

``FolderPicker.kt``
    The SAF folder picker and the resolution of the returned tree ``Uri`` into a real
    ``File``. A folder that is not on local storage resolves to ``null``, which is what
    produces the "the editor cannot browse it" message rather than an empty tree.


Rules that are easy to break
----------------------------

* **The session outlives the editor screen.** It is scoped to the activity, so a compile
  started from the editor can finish after the screen is gone. Do not add screen-scoped
  callbacks to it.
* **Dirty state is per document, not per screen.** ``hasDirtyDocuments`` is what blocks
  back navigation; leaving without calling ``persistOpenTabs()`` loses the tab list.
* **The tree is a snapshot.** ``refreshTree`` is explicit; nothing watches the folder for
  you except the registered ``FileObserver``, which is best-effort and debounced.
* **Search and replace run off the UI thread.** They are launched from the screen's
  coroutine scope and touch the disk directly; calling them from a composable body would
  block the first frame.
* **Only genuinely local folders are openable.** SAF ``content://`` trees resolve through
  ``resolveTreeToFile``; a ``null`` result is normal on cloud providers and must stay a
  user-visible message, not a crash.
