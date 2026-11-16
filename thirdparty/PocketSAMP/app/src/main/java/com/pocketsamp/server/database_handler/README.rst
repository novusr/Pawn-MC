database_handler
================

Panel database PocketSAMP di atas MariaDB.

English
-------

PocketSAMP's database panel on top of MariaDB.

Berkas
------

``DatabaseManager.kt``
    Menyiapkan direktori data MariaDB, menyalakan/mematikan mysqld, membuat akun
    awal dari konfigurasi, dan menjalankan kueri untuk panel database. Semua
    panggilan berat berjalan di ``Dispatchers.IO``.
