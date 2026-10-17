Contributors
============

PawnMC is built and maintained by the people listed here. The list is generated from the
repository's own commit history rather than kept by hand, so the same person may appear
under more than one identity below: git records whatever name and address the commit was
made with, and several of the accounts here are the same person on different machines.

Maintainers
-----------

.. list-table::
   :header-rows: 1
   :widths: 25 45 30

   * - GitHub
     - Role
     - Contact
   * - `novusr <https://github.com/novusr>`_
     - Project owner. Architecture, native compiler bridge, Xed editor, compiler
       configuration, releases.
     - radjaxyz09@gmail.com
   * - `machijine <https://github.com/machijine>`_
     - Interface design and implementation, localisation data, in-app documentation and
       configuration guide.
     - machijine@google.com

Committers
----------

Everyone with commits in ``git shortlog``, in descending order of contribution:

.. list-table::
   :header-rows: 1
   :widths: 30 20 50

   * - Name
     - Commits
     - Committed as
   * - machijine
     - 102
     - ``machijine@google.com``, ``machijine@users.noreply.github.com``,
       ``gitgat@google.com``
   * - novusr
     - 82
     - ``radjaxyz09@gmail.com``, ``novusr@gmail.com``, ``novusr@example.com``
   * - gskeleton
     - 5
     - ``222417345+gskeleton@users.noreply.github.com``

Automation
----------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Account
     - Contribution
   * - `github-actions[bot] <https://github.com/apps/github-actions>`_
     - Continuous integration: lint, debug build, and unit tests on every push and pull
       request (``.github/workflows/ci.yml``). It also renames release artefacts.
   * - `Copilot <https://github.com/features/copilot>`_
     - Automated commits attributed to ``copilot@local``; reviewed and adjusted by the
       maintainers before merging.

Testers and community
---------------------

Reported bugs, tested pre-release builds, and shaped the compiler defaults:

* The testers acknowledged in the application's own **Thank You** section, which is
  rendered from the notes in ``README.rst`` and shown inside Settings.
* Everyone who filed issues or helped in the official community Discord:
  https://discord.gg/2YqkmDvTch

Compiler defaults review (October 2026)
---------------------------------------

The compile defaults that ship in this release were reworked after feedback from the
community: debug level now starts at ``-d0`` (was ``-d3``), optimization at ``-O0``
(was ``-O1``), and the behind-the-scenes invocation runs with ``-C+``, ``-v0``, and
``-w217`` unless the user's own custom flags name the same option, in which case the
user's spelling always wins. The ``[explain]`` hint marker in the compiler output is
now highlighted in a bright ``0xFFF070`` yellow in both the main output panel and the
workspace panel so explained messages stand out at a glance.

Community discussion that drove the change happened in the Discord server above; the
implementation was carried out by ``novusr`` and reviewed against the existing test
suite in ``test/app/unit``.

Third-party work PawnMC builds on
---------------------------------

PawnMC would not exist without these projects. None of them are contributors *to* this
repository; they are the upstream work it depends on.

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Project
     - What PawnMC uses from it
   * - `pawn-lang/compiler <https://github.com/pawn-lang/compiler>`_ and
       `openmultiplayer/compiler <https://github.com/openmultiplayer/compiler>`_
     - The Pawn compiler itself, vendored as the ``pawnc-3.10.7`` and ``pawnc-3.10.11``
       git submodules.
   * - `Rosemoe/sora-editor <https://github.com/Rosemoe/sora-editor>`_
     - The code editor widget the PawnMC editor is built on.
   * - `pocketsamp <https://github.com/novusr/PocketSAMP>`_
     - The embedded SA-MP server runtime adopted into ``thirdparty/PocketSAMP``.

Adding yourself
---------------

Open a pull request that touches the project, and your commit will appear in the history
this file is derived from. If you are listed under more than one name and want them
merged, say so in the pull request description.
