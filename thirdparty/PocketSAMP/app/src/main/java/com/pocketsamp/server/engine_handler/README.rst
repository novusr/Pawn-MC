engine_handler
==============

Mesin PocketSAMP: menjalankan binary server, MariaDB, PHP, dan mengurus semua
proses native yang dipakai layar aplikasi. Semua nama berkas berawalan
``third_`` menandai "lapisan pihak ketiga" - binary yang dipaketkan, bukan kode
kita sendiri.

English
-------

PocketSAMP's engine: running the server binary, MariaDB, PHP, and managing every
native process the screens rely on. The ``third_`` prefix marks "third-party
layer" files - packaged binaries, not our own code.

Berkas
------

``third_server.kt``
    Menyalakan/mematikan proses ``samp03svr`` dan memantau kode keluarnya.

``third_runtime.kt``
    Pemeriksaan runtime (arsitektur, izin eksekusi, versi glibc) sebelum server
    dijalankan.

``third_process.kt``
    Pembungkus ``ProcessBuilder``: argumen, environment, dan pembacaan stdout.

``third_launcher.kt``
    Titik masuk untuk memulai sesi server lengkap dari layar mana pun.

``third_mariadb.kt``
    Menginisialisasi dan menjalankan MariaDB untuk panel database.

``third_extractor.kt``
    Mengekstrak arsip binary apa pun ke direktori kerja.

``third_unpacker.kt``
    Membongkar paket runtime (PHP/web panel) ke folder yang dapat dieksekusi.

``third_multi_archive.kt``
    (dulu ``ArchiveExtractor.kt``) Ekstraksi multi-format - ZIP, TAR, TAR.GZ,
    7z/XZ bila library tersedia - dan pemilihan arsip yang cocok dengan isi
    folder kerja.

``third_phantom_guard.kt``
    (dulu ``PhantomGuard.kt``) Penjaga proses "hantu": memastikan tidak ada
    server/MariaDB lama yang masih hidup sebelum sesi baru dimulai, dan merapikan
    proses yatim ketika aplikasi berhenti.

Catatan
-------

Nama berkas ``third_multi_archive.kt`` dan ``third_phantom_guard.kt`` sengaja
dipilih supaya daftar folder langsung terbaca urutannya tanpa membuka isinya;
nama kelas di dalamnya tidak berubah.
