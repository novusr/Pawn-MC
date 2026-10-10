PawnMC
======

**New Updates** link: https://github.com/novusr/Pawn-MC/actions/runs/37277191792/artifacts/11330498815

*PawnMC* expands the workflow of the Pawn scripting language with a complete, native
compilation environment for your mobile device.

.. image:: https://raw.githubusercontent.com/machijine/mc-content/refs/heads/main/content/PAWNMC2.png
   :alt: PawnMC running on Android

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

Indonesian | Tutorial
---------------------

* https://youtu.be/ym9E1QH1ABU?si=xcL-mrqrx3RknBYs
* https://youtu.be/sgG58JTytI4?si=r6r3TukcnwxD58SP
* https://youtu.be/xSit9n80DoM?si=tDVehEI6KHBvSZME

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

Documentation
-------------

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
     - The starter script (``unit.pwn``) created when you open the editor without having
       chosen a file yet. It is a normal editable file: any tool on the device can open,
       copy or replace it.
   * - ``Android/data/com.rvdjv.pawnmc/files/.pawnmc``
     - App-private state (the extracted Ubuntu rootfs marker, the update version marker).
       Not user editable, and removed when the app is uninstalled.

Android deletes the whole ``Android/data/com.rvdjv.pawnmc`` tree when the app is
uninstalled, so anything you want to keep should be copied out of the ``PawnMC`` folder
first. The app never removes that folder on its own.

Building
--------

Use Gradle to build the project from source on your environment. Requires Git for cloning
submodules.

.. code-block:: bash

   git clone --recursive https://github.com/novusr/Pawn-MC.git
   cd Pawn-MC
   ./gradlew assembleDebug
