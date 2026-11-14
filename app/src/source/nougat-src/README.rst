Nougat flavor source set (``nougat-src``)
=========================================

The overlay compiled *only* into the ``nougat`` product flavor — the Android 7/8 build,
which does **not** link the PocketSAMP module. Its counterpart is ``newer-src``.

It is documented because it is the smaller of the two overlays and therefore the one that
is easy to forget: a change in shared code that assumes the runtime exists compiles here
only if this source set still satisfies the same interface.


What it must guarantee
----------------------

* ``PocketSampGateway.isAvailable()`` returns ``false`` and ``launch`` returns ``false``,
  which is what makes the main screen show "PocketSAMP is not available in this build"
  instead of opening an empty screen.
* The media-API allowance that PocketSAMP needs on API 33+ does not apply here; the
  manifest overlay only carries what this flavor's Android version needs.
* Any resource the shared code expects but this flavor cannot use has a stub here.

The runtime resolution is skipped entirely when the application id ends with
``.legacy`` (the Nougat package suffix), so no hook is required — but the interface and the
reflective path must still link.


Rules that are easy to break
----------------------------

* **Do not add PocketSAMP types here.** The module is not on this flavor's compile
  classpath; importing one fails the build with a message about a missing dependency.
* **Keep the fallbacks honest.** A stub that pretends to succeed makes the Nougat build
  open a blank screen instead of explaining that the runtime is not included.
* **Shared code changes belong in both overlays or in ``main``.** If a feature needs
  behavior that differs per flavor, put the interface in ``main`` and implement it here
  *and* in ``newer-src``.
