App unit and instrumented tests
===============================

The Kotlin/Android test sources, wired into Gradle from outside the app module:
``app/build.gradle.kts`` points the ``test`` source set at ``native/test/app/unit`` and
``androidTest`` at ``native/test/app/instrumented``.

It is documented because the location is surprising — these test Kotlin UI and data code
even though they live under ``native/`` — and because two ``.bak`` files sit among the live
tests and are **not** compiled, which no directory listing shows.


Layout
------

``app/unit/java/…``
    JVM tests, run with ``gradlew test``. They mirror the production packages, so a test
    can reach the ``internal`` declarations it verifies:

    * ``apps_data/data_config`` — ``ContentConfig``, the JSON mirror, localisation;
    * ``apps_data/data_compiler`` — version selection, case conversion, the explainer
      table and the ignore-case workflow;
    * ``apps_data/data_syntax`` — the ``_internal.toml`` reader and ``server.cfg`` keys;
    * ``apps_data/data_library`` / ``data_update`` — extraction checks and version compare;
    * ``user_interface/editor[/portal]`` — the markdown renderer and the panel's source
      rules.

``app/instrumented/java/…``
    Tests that need a device or emulator. Today this is only the generated
    ``ExampleInstrumentedTest``.

``ExampleUnitTest.kt``
    The template ``2 + 2`` test, kept as the smoke test that the test source set is wired
    at all.


Retired tests (not compiled)
----------------------------

* ``CompilerConfigFileTest.broken.bak`` (root of this folder)
* ``app/unit/java/…/data_config/CompilerConfigFileTest.pre-move.bak``

The ``.bak`` suffix keeps them out of the source set: Gradle compiles ``.kt`` and
``.java`` only. They are references to a configuration format that was replaced by the
JSON mirror. Renaming one to ``.kt`` turns it back into a compiled test.


Rules that are easy to break
----------------------------

* **Package must match the class under test.** Several tests reach ``internal`` members;
  moving a production file without moving its test fails the build with "cannot access",
  not with a failed assertion.
* **No ``android.*`` in ``app/unit``.** A JVM test that pulls in the framework fails at run
  time with "method not mocked" unless the project opts into unit-test stubs.
* **Path-dependent tests use the storage seams.** ``AppStorage.documentsRootOverride`` and
  ``publicWriteAccessOverride`` exist for this; a test writing to the real
  ``Documents/PawnMC`` passes locally and fails on CI.
