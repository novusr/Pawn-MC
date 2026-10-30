PawnMC Roadmap
==============

Ringkasan
---------

PawnMC adalah editor dan compiler Pawn untuk Android. Aplikasi membawa dua
compiler Pawn (3.10.7 dan 3.10.11) sebagai library native, menandai sintaks
melalui mesin editor Sora, dan menyediakan penjelasan pesan compiler dalam
bahasa aplikasi. Sebuah runtime SA-MP + MariaDB opsional ikut disertakan
sebagai modul terpisah agar server lokal bisa dijalankan langsung dari HP.

Status saat ini
---------------

- Versi aplikasi: 1.5.2 (versionCode 6), tag terakhir ``v1.5.2``.
- compileSdk dan targetSdk 36, minSdk 24. Flavor ``nougat`` untuk Android 7/8
  dengan minSdk 26 dan nama paket ``.legacy``.

Yang sudah berjalan
-------------------

Editor dan workspace
  * Workspace folder dengan explorer, tab dokumen, pencarian dan replace
    seluruh workspace, serta pemantauan perubahan berkas.
  * Autocompletion dari tabel simbol Pawn, plus simbol ``native``/``forward``
    yang dideklarasikan di berkas yang sedang dibuka.
  * Developer portal internal: ``help``, ``docs``, ``pawncc``, ``switch``,
    ``which``, ``deviceinfo``, ``version`` dan perintah lain tanpa shell.

Compiler
  * Dua versi compiler native, deteksi ``pawncc`` di dekat berkas sumber,
    fallback otomatis antar versi saat error melewati batas.
  * Mode "Ignore case": backup folder, lowercase nama berkas dan referensi
    ``#include``.
  * Penjelasan pesan compiler dari ``data/_data_2026_exp.toml``, dengan
    override di ``Documents/PawnMC/config``.
  * Satu database log SQLite untuk seluruh kompilasi.

Penyimpanan dan pembaruan
  * Data pengguna di ``Documents/PawnMC``, dengan migrasi sekali jalan dan
    cermin JSON pengaturan yang bertahan setelah instal ulang.
  * Pemeriksa pembaruan GitHub dengan paket terpisah untuk Android 7/8 dan
    Android 8-15.

Bahasa
  * Lima bahasa antarmuka: Indonesia, Inggris, Spanyol, Rusia, Tagalog.
    Tagalog sudah diterjemahkan penuh. Aset ``.toml`` yang tidak memuat suatu
    bahasa otomatis jatuh ke bahasa Inggris.

Runtime SA-MP (thirdparty/PocketSAMP)
  * Server SA-MP 32-bit dijalankan lewat penerjemah QEMU i386, dengan MariaDB
    i386 di jalur yang sama.
  * Arsip runtime tidak dibundel di APK; diunduh sekali ke
    ``Documents/PawnMC/storage`` dan diurai per arsip.
  * Antarmuka bawaan: dashboard, konsol, basis data, log crash, dan editor
    ``server.cfg``; notifikasi, log, dan teks basis data ikut dwibahasa.

Berikutnya
----------

- Menambah tes untuk alur konversi "Ignore case" dan untuk logika deteksi
  versi compiler, agar regresi ketahuan sebelum rilis.
- Memperluas cakupan aset lokalisasi (``_data_2026_exp.toml``,
  ``_data_2026_term.toml``) supaya seluruh pesan, bukan hanya antarmuka,
  tersedia dalam kelima bahasa.
- Menjaga runtime SA-MP tetap di belakang satu antarmuka kecil, supaya
  pembaruan paket QEMU atau MariaDB tidak menyentuh kode antarmuka.

Catatan
-------

Modul ``thirdparty/PocketSAMP`` adalah komponen pihak ketiga dengan namespace
``com.pocketsamp.server``. Nama berkas internalnya memakai awalan ``third_``
dan paket ``configuration`` memakai ``Reader``/``Writer`` agar tidak
mengulang nama folder.
