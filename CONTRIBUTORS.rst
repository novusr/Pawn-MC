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
       configuration, sandbox terminal integration, releases.
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
   * - `termux/termux-app <https://github.com/termux/termux-app>`_
     - The terminal emulator and ``TerminalView`` behind the sandbox terminal.
   * - `proot-me/proot <https://github.com/proot-me/proot>`_
     - PRoot, which provides the Ubuntu userspace the sandbox shell runs in.
   * - `Ubuntu Base <https://cdimage.ubuntu.com/ubuntu-base/>`_
     - The root filesystem downloaded on first use of the sandbox terminal.

Adding yourself
---------------

Open a pull request that touches the project, and your commit will appear in the history
this file is derived from. If you are listed under more than one name and want them
merged, say so in the pull request description.
