PocketSAMP theme (``user_interface/theme``)
==========================================

The runtime's own colour system, kept separate from PawnMC's Material theme on purpose:
PocketSAMP can run with a theme that follows PawnMC's setting but is not derived from the
host's palette.

It is documented because the colours are *pair* declarations — a dark value and a light
value behind one accessor — and reading a raw ``Color`` from ``Color.kt`` instead of the
accessor is the most common way to get an unreadable screen in one theme.


File map
--------

``Color.kt``
    The palette constants: dark and light variants for background, surface, card, border,
    text, and the semantic accents (accent, success, warning, danger) plus the terminal
    surface used by the console and crash screens.

``Theme.kt``
    ``PocketSAMPTheme`` (the Material wrapper), ``SampThemeColors`` (every accessor such as
    ``bg``, ``surface``, ``textPrimary``, ``danger`` is a ``@Composable`` property that picks
    the dark or light variant) and ``pocketSampTypography``. There is no separate typography
    file: the type scale is built there and its line heights are tied to each role's own
    size, with a floor (``MIN_LABEL_SM`` …) so a 1.3× system font scale cannot shrink a
    label into a few pixels.


Rules that are easy to break
----------------------------

* **Use ``SampThemeColors.*``, not the constants in ``Color.kt``.** The constants are half
  a pair; the accessor is the pair.
* **Accessors are composable.** They read a composition local, so they cannot be used
  inside a non-composable helper or a ``remember { }`` block — capture the value in the
  composition and pass it down.
* **The terminal surface is deliberately not the same as ``surface``.** Console text,
  crash stack traces and scrollbar tracks are all tuned against it; swapping it for
  ``surface`` makes the output panel blend into the card.
* **Keep it independent of PawnMC's theme.** The host maps *its* theme setting onto
  PocketSAMP's ``Locales.Theme``; importing the host's Material palette here would make
  the runtime change appearance when only the editor theme changed.
