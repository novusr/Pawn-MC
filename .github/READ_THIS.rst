=========================
PawnMC CI Workflows Guide
=========================

:Date: 2026-11-20
:Version: 1.0
:Status: Draft
:Language: en-US

.. contents:: Table of Contents
   :depth: 2
   :local:

Overview
========

This document describes the continuous integration (CI) workflows used by the
PawnMC project, with emphasis on the unit-testing strategy for the embedded
PocketSAMP runtime module (``thirdparty/PocketSAMP``) and the host application
(``app``).

Repository layout
-----------------

::

  c:/Users/Administrator/Documents/Pawn-MC/
  ├── .github/
  │   ├── READ_THIS.rst          # this document
  │   └── workflows/             # GitHub Actions workflow definitions
  ├── app/                       # host PawnMC application (Compose, Kotlin)
  ├── thirdparty/
  │   └── PocketSAMP/            # embedded server runtime module
  │       ├── app/
  │       │   ├── src/main/...   # production sources
  │       │   └── src/test/...   # JUnit unit tests
  │       └── build.gradle.kts   # module build script
  └── build.gradle.kts           # root version catalog and plugins

CI workflows
============

The project uses GitHub Actions. Workflow files live under
``.github/workflows/``. Each workflow is triggered on ``push`` and
``pull_request`` to the ``main`` branch.

Build workflow
--------------

The primary workflow compiles both modules:

1. Checkout the repository.
2. Set up the JDK (Temurin 17).
3. Restore Gradle caches.
4. Run ``./gradlew assembleDebug`` for the host app.
5. Run ``./gradlew assembleDebug`` inside ``thirdparty/PocketSAMP``.

If either module fails to compile, the workflow stops and reports the failing
module.

Unit-test workflow
------------------

Unit tests run with every push and pull request:

1. Checkout the repository.
2. Set up the JDK (Temurin 17).
3. Restore Gradle caches.
4. Execute ``./gradlew test`` for the host app.
5. Execute ``./gradlew test`` inside ``thirdparty/PocketSAMP``.

Test results are published as GitHub Actions annotations so failures are
visible directly on the pull-request diff.

Unit testing strategy
=====================

PocketSAMP module tests
-----------------------

Location: ``thirdparty/PocketSAMP/app/src/test/java/com/pocketsamp/server/``

Current test classes:

* ``ServerConfigTest`` — validates reading/writing of ``server.cfg``,
  default configuration values, and gamemode/filterscript/plugin parsing.
* ``RuntimeArchiveIntegrityTest`` — validates archive extraction safety
  (empty/corrupt archives, path traversal rejection).
* ``FileManagerIconTest`` — validates the new File Manager icon set
  (``FilePaper``, ``FolderRect``, ``FolderRectOpen``) and the expanded/collapsed
  folder icon-selection expression.

How to add a new test
---------------------

1. Create a Kotlin file under
   ``thirdparty/PocketSAMP/app/src/test/java/com/pocketsamp/server/``.
2. Declare a class annotated with ``org.junit.Test`` methods.
3. Use ``org.junit.Assert`` for assertions (``assertEquals``,
   ``assertTrue``, ``assertFalse``, ``assertNotNull``).
4. Run locally with ``./gradlew :app:test`` (from ``thirdparty/PocketSAMP``)
   or rely on GitHub Actions CI on push/pull request.

Gradle commands
===============

Compile both modules::

  ./gradlew assembleDebug

Run unit tests for host app::

  ./gradlew test

Run unit tests for PocketSAMP module::

  cd thirdparty/PocketSAMP
  ./gradlew test

Clean all build outputs::

  ./gradlew clean

Notes
-----

* No network calls are made during unit tests; all fixtures are created in
  ``java.io.tmpdir`` and deleted afterwards.
* Icon changes are purely visual (Compose ``ImageVector``) and are covered by
  ``FileManagerIconTest`` which asserts the icon objects are non-null and the
  selection expression returns the correct icon name for each state.