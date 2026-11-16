data_update
===========

Pemeriksa dan pengunduh pembaruan aplikasi.

English
-------

The app's update checker and downloader.

Berkas
------

``UpdateManager.kt``
    Membaca rilis terbaru dari GitHub, memilih aset sesuai varian Android,
    mengunduhnya ke ``Documents/Pawn-MC``, lalu menyerahkan APK ke installer
    sistem. Tidak pernah menghapus aplikasi lama dan tidak memasang apa pun
    tanpa izin pengguna (``REQUEST_INSTALL_PACKAGES`` + dialog sistem).

``UpdateStatus.kt``
    Hasil pemeriksaan: tersedia / terbaru / gagal, beserta catatan rilis.
