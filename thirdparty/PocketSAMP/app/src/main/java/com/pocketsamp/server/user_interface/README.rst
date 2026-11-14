PocketSAMP UI (``user_interface``)
=================================

The embedded runtime's interface: seven tab screens, its own theme, its own scroll widget
and the shared localisation accessor. It looks like a separate app because it was one; it
now runs inside PawnMC's process and follows PawnMC's language and theme settings.

It is documented because the boundary with the host is one-directional and easy to cross
by accident: the runtime may *receive* settings from PawnMC, but it does not link PawnMC's
classes.


Subfolders
----------

``screens``
    One composable per tab. See its README for which state each screen reads.

``theme``
    ``PocketSAMPTheme`` and ``SampThemeColors``: the dark/light pairs behind one accessor.
    See its README before reading a raw colour from ``Color.kt``.

``widget``
    The scroll sidebar used by every scrollable runtime screen. It is a deliberate copy of
    the host's ``user_interface/widget`` (this module cannot import it); behaviour changes
    must be made in both.

``Locales``(``com/pocketsamp/server/Locales.kt``, one level up)
    The runtime's own string table and current language/theme, set by the host when the
    runtime is launched. Screens resolve text through ``Locales.text(key)`` at render time.


Rules that are easy to break
----------------------------

* **No host imports.** This module does not link ``com.rvdjv.pawnmc``; reaching into it
  breaks the modular build. The only host contract is the crash-bridge seam installed by
  the host's flavor hook.
* **Language and theme are set, not stored.** ``Locales.setLanguage`` / ``setTheme`` are
  called at launch from PawnMC's settings; the runtime does not render a settings screen of
  its own for them.
* **Screens are hosted, not launched.** ``MainActivity`` in this module starts on top of
  the PawnMC task and returns with the back button; nothing here declares a launcher.
