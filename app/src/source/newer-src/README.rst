Modern flavor source set (``newer-src``)
========================================

The overlay compiled *only* into the ``newer`` product flavor — the build that links the
PocketSAMP module and runs on Android 8+. Its counterpart is ``nougat-src``.

It is documented because an empty-looking flavor overlay is the easiest place to put a
file in the wrong place: anything here is invisible to the Nougat build, and a reference
to it from shared code fails to compile there with an error that points at the wrong file.


What lives here
---------------

``com/pocketsamp/server/PocketSampRuntimeMarker.kt``
    The registration entry point ``registerNewerPocketSampHook`` and the hook
    implementation. The shared gateway looks the function up *by name* (reflection), which
    is why it can call into this source set without the ``main`` source set seeing the
    type. The hook maps PawnMC's language and theme onto PocketSAMP's own enums, installs
    the runtime, and mirrors its console.

``com/pocketsamp/server/PocketSampCrashBridge.kt``
    Routes PocketSAMP's crash reports into PawnMC's folder: its handler keeps the dialog,
    but the dump and the text report are written by ``data_crash.CrashHandler``, so a
    crash shares one format and one location with the rest of the app. Also supplies the
    history listing the runtime's crash screen shows.


Rules that are easy to break
----------------------------

* **Never reference these classes from ``main``.** ``main`` is compiled for both flavors;
  a direct import breaks the Nougat build. The gateway interface and the reflective
  lookup exist precisely to avoid this.
* **Keep the registration function name and ``@Keep``.** R8 cannot see a reflective
  lookup, so the function must stay reachable or the runtime silently disappears from the
  release build.
* **This overlay is not for UI.** Screens belong in ``main``; a composable here exists
  only in one flavor and cannot be previewed for the other.
* **Mirror anything behavioural into ``nougat-src`` as a no-op.** The shared code calls
  the same gateway on both flavors; only the absence of the runtime differs.
