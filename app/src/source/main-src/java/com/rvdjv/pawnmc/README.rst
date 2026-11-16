com.rvdjv.pawnmc
================

Akar kode Kotlin PawnMC. Paket ini hanya berisi dua lapisan, dan aturannya
sederhana: ``apps_data`` tidak tahu soal UI, ``user_interface`` tidak menyentuh
disk tanpa perantara.

English
-------

Root of PawnMC's Kotlin code. Only two layers live here, with one simple rule:
``apps_data`` knows nothing about UI, ``user_interface`` never touches disk
directly.

Peta folder
-----------

``apps_data/README.rst``
    Konfigurasi, penyimpanan, kompiler native, library, log, runtime PocketSAMP,
    data sintaks, dan pembaruan. Tanpa Compose.

``user_interface/README.rst``
    Layar utama, pengaturan, penjelajah file, Xed Editor, tema, dan komponen
    bersama.

``user_interface/Icons.kt``
    Kumpulan ``ImageVector`` kecil yang dipakai lintas layar. (Folder
    ``com/rvdjv/pawnmc`` sendiri tidak menyimpan berkas Kotlin: seluruh kode
    berada di dua subpaket di atas.)

Alur besar aplikasi
-------------------

1. ``MainActivity`` memilih antara ``MainScreen`` (pilih & kompilasi) dan
   ``EditorScreen`` (Xed).
2. ``MainViewModel.compileFile`` menjalankan ``Compiler.compile`` (yang menuju
   ``Runner`` -> ``NativeBridge``) lalu ``Explainer.explainCompilerOutput``.
3. ``Explainer`` menulis ``compiler/injector_data.json`` lewat
   ``DiagnosticsStore``; Xed membaca file itu di ``ExplainInjector`` dan
   menempelkan komentar ``// [cause]`` pada baris yang rusak.
4. Komentar hanya hilang lewat *Discard explainer* (record jadi ``discarded``)
   atau ketika pengguna menyunting baris tersebut sendiri.

Berkas penunjuk
---------------

* ``XEDEDITOR.rst`` di akar repositori: panduan pengguna Xed Editor.
* ``README.rst`` di akar repositori: pengantar proyek dan catatan rilis.
