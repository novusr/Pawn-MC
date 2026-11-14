Xed editor screen (``editor/xedapi``)
=====================================

The Sora-based editor: one Compose screen, one view model, one custom editor widget, and
the hint injector that writes ``[explain]`` comments into the open document.

It is documented because this is the largest screen in the app (a single
``EditorScreen.kt`` of several thousand lines) and because three different mechanisms
decide what the editor shows — the workspace session, the Sora widget, and the compose
state around them — with no boundary between them visible from a call site.


Who owns what
-------------

``Session.kt`` (in ``editor/workspace``) owns the documents. The screen never holds a
document itself: it renders ``workspace.activeDocument`` and pushes that text into the
widget. When a file, tab or workspace changes, the session changes and the screen
re-renders; there is no second copy of the buffer inside the screen.

``XedCodeEditor.kt`` owns the display. It is a ``CodeEditor`` subclass that adds the
row-height-aware column suffix, the auto-close modes and the line jumps the sidebar uses.

``EditorScreen.kt`` owns the *composition*: toolbar, tabs, floating buttons, panels and
dialogs. Every action here either calls the session (open/close/save), the widget
(undo, jump, search) or a launcher (file pickers, compile).

``EditorViewModel.kt`` holds only what survives outside a workspace: the single-file
mode used when no folder is open, and the compile request plumbing.


File map
--------

``EditorScreen.kt``
    The screen. Sections, in order: localisation and theme, the explain sync loop,
    workspace and file pickers, save/compile paths, dialogs, the toolbar, the tab strip,
    the canvas with its right-edge scroll sidebar, the floating action stack, the symbol
    bar and the status bar.

``EditorViewModel.kt``
    Single-file state (path, name, content, dirty flag) and the factory that scopes one
    instance to the activity.

``XedCodeEditor.kt``
    The widget. Anything that needs the document's rendered geometry lives here.

``ExplainInjector.kt``
    Applies the diagnostics from ``data_compiler`` as ``[explain]`` comments: reads the
    JSON written by a compile, matches lines to the current document and inserts or
    refreshes the hint comments every six minutes and once after each compile.

``EditorScheme.kt``
    Editor colour schemes derived from the Material theme, plus the holders that let the
    screen swap a scheme (and a language) only when the instance actually changed.

``ToolPanel.kt``
    The search / replace / replace-in-all-files overlay. It only collects the query and
    reports the action; the actual workspace scan runs in ``Session``.


The two buffer paths (the easiest thing to break)
-------------------------------------------------

The screen supports two modes that share every line of UI but not the same state:

* **workspace mode** — a folder is open; the active document lives in ``Session``, and the
  text is pushed into the widget when ``absolutePath`` changes;
* **single-file mode** — no folder; the text lives in ``EditorViewModel.fileContent``.

``activeFilePath`` resolves which mode is in force and is the key for the language, the
tab title, the status bar and the journal. A new feature that reads "the open file" must
go through it rather than through ``viewModel.filePath``, or it will silently do nothing
whenever a workspace is open.


Rules that are easy to break
----------------------------

* **Never call ``editor.text = …`` from a composition.** Writing the buffer inside a
  composable resets the caret on every pass. The update block compares a stored buffer
  token and only writes when the file really changed.
* **``hideAutoCompletion()`` before any toolbar action.** A completion popup left open
  keeps focus and swallows the next tap.
* **The widget is released on dispose.** ``editorRef`` must be nulled there; a leaked
  ``CodeEditor`` keeps its native text measurement alive for the whole process.
* **The explain loop is periodic by design.** It re-reads the diagnostics file rather than
  being notified, because the file can be rewritten by a compile that ran outside the
  screen (the Developer Portal).
