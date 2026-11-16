data_library
============

Pengelola library native ``pawncc``.

English
-------

Manager for the native ``pawncc`` libraries.

Berkas
------

``LibraryStore.kt``
    Mengekstrak ``library/<abi>/<abi>.zip`` ke ``Documents/PawnMC/library/<abi>/``,
    memeriksa apakah setiap file sudah ada dengan ukuran dan ABI yang benar, dan
    mengulang ekstraksi bila kompiler hilang atau berasal dari arsitektur lain.
    ``deviceAbi()`` memilih folder ABI perangkat.

Dipakai oleh
------------

``data_compiler/Runner.kt`` sebelum setiap kompilasi; kegagalan ekstraksi
dilaporkan sebagai pesan kompiler, bukan crash.
