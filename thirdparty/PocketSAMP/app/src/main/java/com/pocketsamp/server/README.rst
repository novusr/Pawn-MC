com.pocketsamp.server
=====================

Kode PocketSAMP - modul di dalam aplikasi PawnMC yang menjalankan server SA-MP
secara lokal di Android.

English
-------

PocketSAMP's code - the module inside the PawnMC app that runs a SA-MP server
locally on Android.

Peta paket
----------

``engine_handler/``
    Proses native: server, MariaDB, PHP, ekstraksi arsip, dan penjaga proses.
    Semua berkas yang berawalan ``third_`` adalah lapisan binary pihak ketiga
    (termasuk ``third_multi_archive.kt`` dan ``third_phantom_guard.kt``).

``user_interface/``
    Layar Compose (file manager, server, database, pengaturan).

``configuration/``
    Model konfigurasi server dan pembacaan/penulisan ``server.cfg``.

``service_handler/``
    Foreground service yang menahan server tetap hidup saat aplikasi di latar
    belakang.

``database_handler/``
    Memulai MariaDB dan menjalankan kueri untuk panel database.

``crash_handler/``
    Penangkap crash (termasuk crash proses native), penyusun laporan, dan dialog
    pemulihan.

Catatan penting
---------------

* Layar file manager menawarkan pemilihan picker (sistem atau ZArchiver) sebelum
  membuka ``ACTION_OPEN_DOCUMENT_TREE``; blok ``<queries>`` di manifest modul ini
  diperlukan agar deteksi paket tidak selalu kosong di Android 11+.
* PawnMC membuka Xed lewat intent ``com.rvdjv.pawnmc.action.OPEN_XED_PATH``;
  tidak ada layar PocketSAMP yang menyentuh editor secara langsung.
