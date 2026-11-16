screens
=======

Satu berkas per layar. Tidak ada logika proses di sini: setiap layar membaca
state dari pengelola proses di ``engine_handler`` dan ``service_handler``, lalu
menampilkan hasilnya.

English
-------

One file per screen. No process logic lives here: each screen reads state from
the managers in ``engine_handler`` and ``service_handler`` and renders it.

Berkas
------

``FileManager.kt``
    Penjelajah folder server: pilih folder root, buat file/folder, ganti nama,
    hapus, ekstrak arsip, dan buka file di Xed. Tombol folder root menawarkan
    picker sistem atau file manager terpasang (ZArchiver) sebelum
    ``ACTION_OPEN_DOCUMENT_TREE`` dibuka.

``Dashboard.kt``
    Ringkasan: status server, alamat IP, dan pintasan ke layar lain.

``Console.kt``
    Konsol server: log ``server_log.txt`` yang bergulir dan perintah.

``Database.kt`` / ``PhpMyAdmin.kt``
    Panel MariaDB: status, akun, kueri, dan akses phpMyAdmin.

``Configuration.kt``
    Editor ``server.cfg`` berformat formulir (memakai ``configuration/Reader``
    dan ``Writer``).

``CrashInfo.kt``
    Menampilkan laporan dari ``crash_handler``.

``Help.kt``
    Bantuan singkat untuk setiap layar.
