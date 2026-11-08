PawnMC
======

**New Updates** link: Android 7/8: https://github.com/novusr/Pawn-MC/actions/runs/42448881857/artifacts/13604089045

**New Updates** link: Android 8-15: https://github.com/novusr/Pawn-MC/actions/runs/42448881857/artifacts/13604424517

*PawnMC* expands the workflow of the Pawn scripting language with a complete, native
compilation environment for your mobile device.

With this application, you can use on-the-go development techniques, manage local
libraries, and build your projects directly on Android to make programming in Pawn more
accessible, flexible, and efficient.

*PawnMC* introduces a fully portable workspace, supporting standard libraries, custom
includes, and complex project directories. Code can be compiled entirely offline with high
execution speed and instant error reporting. The compiler can be used to iterate on your
game modes and filterscripts wherever you are, without relying on a desktop workspace and
without interrupting your creative flow.

The underlying engine utilizes the native architecture of your Android device, removing
the need to transfer files to a PC just to verify your code in most cases.

Indonesia | Tutorial
---------------------

`YouTube #1 <https://www.youtube.com/watch?v=ym9E1QH1ABU>`_  `YouTube #2 <https://www.youtube.com/watch?v=sgG58JTytI4>`_  `YouTube #3 <https://www.youtube.com/watch?v=xSit9n80DoM>`_  `YouTube #4 <https://www.youtube.com/watch?v=B_TWmCZ8cvE>`_

Indonesia | Subtitles (EN) | Tutorial
-------------------------------------

`YouTube #1 <https://www.youtube.com/watch?v=ym9E1QH1ABU&hl=en&cc_lang_pref=en&cc_load_policy=1>`_  `YouTube #2 <https://www.youtube.com/watch?v=sgG58JTytI4&hl=en&cc_lang_pref=en&cc_load_policy=1>`_  `YouTube #3 <https://www.youtube.com/watch?v=xSit9n80DoM&hl=en&cc_lang_pref=en&cc_load_policy=1>`_  `YouTube #4 <https://www.youtube.com/watch?v=B_TWmCZ8cvE&hl=en&cc_lang_pref=en&cc_load_policy=1>`_

Notes
=====

Indonesian | Notes
-------------------

Selamat datang di repositori resmi PawnMC.

Di sini adalah tempat di mana PawnMC beroperasi, dan kami sangat menyambut kedatangan Anda
dengan penuh rasa syukur.

PawnMC adalah aplikasi Android yang secara khusus dibuat untuk mempermudah dan mempercepat
proses pengembangan Anda sebagai developer.

Kami juga ingin mengucapkan terima kasih yang sebesar-besarnya kepada para tester yang
telah meluangkan waktu, tenaga, dan masukan berharga untuk membantu meningkatkan kualitas
aplikasi ini.

Apresiasi kami juga ditujukan kepada seluruh pengguna PawnMC yang telah mempercayai,
menggunakan, dan mendukung perkembangan aplikasi ini dari waktu ke waktu.

Semoga PawnMC terus bermanfaat, berkembang, dan menjadi tools yang membantu Anda dalam
membuat proyek Pawn dengan lebih efisien.

Discord Kami: https://discord.gg/2YqkmDvTch

English | Notes
---------------

Welcome to the official PawnMC repository.

This is the place where PawnMC operates, and we warmly welcome you with great appreciation.

PawnMC is an Android application specifically designed to make your work easier, faster,
and more efficient.

We would also like to express our sincere gratitude to all testers who have given their
time, feedback, and support to help improve the quality of this app.

Our appreciation also goes to all PawnMC users who have trusted, used, and supported this
application throughout its development.

We hope PawnMC will continue to be useful, grow further, and become a valuable tool for
your Pawn development workflow.

Official Community: https://discord.gg/2YqkmDvTch

Need help?
----------

If you need help, `create an Issue <https://github.com/novusr/Pawn-MC/issues/new/choose>`_
in the PawnMC repository and fill in the available form so we can understand and address
your request.

Butuh bantuan?
--------------

Jika membutuhkan bantuan, `buat Issue <https://github.com/novusr/Pawn-MC/issues/new/choose>`_
di repositori PawnMC dan isi formulir yang tersedia agar kami dapat memahami serta
menindaklanjuti kebutuhan Anda.

Documentation
-------------

We do not ask for donations or other financial support. If you use assets provided by PawnMC,
including the Pawn compiler builds distributed with the app, please preserve the appropriate
credit to their creators. Credit may be included in your source code or shown outside the
source, such as in your project's output or documentation. That is more than enough to help
others discover and recognize the work behind PawnMC and the assets it provides.

See the `configuration folder <//github.com/novusr/Pawn-MC/tree/main/configuration>`_ for
documentation and guides on how to use this application and set up your mobile workspace.

See the project roadmap in ``ROADMAP.rst`` for a full architecture overview, feature
breakdown, and file-by-file analysis of the current codebase.

Installation
------------

Download the latest `release <//github.com/novusr/Pawn-MC/releases/latest>`_ for your
Android device and install the provided APK.

Configuration
-------------

This application allows you to optionally configure a number of compilation features, such
as specific compiler versions, custom include paths, and build flags. These are fully
adjustable directly within the app interface to ensure compatibility with different SA-MP
or open.mp project structures.

See: https://github.com/novusr/Pawn-MC/tree/main/configuration

Settings and the starter workspace are stored on the app's shared directory, in the
``PawnMC`` folder, so they can be inspected and edited outside the app and survive an
update. See `Where PawnMC keeps your files`_ below.

Where PawnMC keeps your files
-----------------------------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Location
     - Contents
   * - ``Android/data/com.rvdjv.pawnmc/files/PawnMC/config``
     - The ``compiler_config.json`` mirror of every setting: compiler version and mode,
       debug and optimization level, include paths, code style, theme, language, and the
       editor canvas colour. Written on every settings change and read back on the next
       launch, so a manual reinstall behaves like an update.
   * - ``Android/data/com.rvdjv.pawnmc/files/PawnMC/workspace``
     - The starter script (``blank.pwn``) created when you open the editor without having
       chosen a file yet. It is a normal editable file: any tool on the device can open,
       copy or replace it.
   * - ``Android/data/com.rvdjv.pawnmc/files/.pawnmc``
     - App-private state (the update version marker).
       Not user editable, and removed when the app is uninstalled.

Android deletes the whole ``Android/data/com.rvdjv.pawnmc`` tree when the app is
uninstalled, so anything you want to keep should be copied out of the ``PawnMC`` folder
first. The app never removes that folder on its own.

Testing
-------

Test sources are kept outside the Android modules under ``native/test/``. Gradle source sets
point there so the existing tasks still work:

* ``native/test/app/unit`` and ``native/test/app/instrumented``

Repository layout
-----------------

Everything that is not the Android app itself lives in one place, so the repository root
stays short:

* ``app/src/source/main-src`` — the shared app code (Kotlin, resources, manifest)
* ``app/src/source/nougat-src`` and ``app/src/source/newer-src`` — the per-flavor trees
* ``native/cpp`` — the JNI compiler bridge (C++) and its ``CMakeLists.txt``
* ``native/data`` — localisation data (``.toml``) shipped as app assets
* ``native/test`` — unit and instrumented test sources
* ``compilers`` — the Pawn compiler git submodules, deliberately kept at the root:
  they are upstream checkouts with their own build layout, not PawnMC sources

Building
--------

Use Gradle to build the project from source on your environment. Requires Git for cloning
submodules.

.. code-block:: bash

   git clone --recursive https://github.com/novusr/Pawn-MC.git
   cd Pawn-MC
   ./gradlew assembleNougatRelease
   ./gradlew assembleNewerRelease
