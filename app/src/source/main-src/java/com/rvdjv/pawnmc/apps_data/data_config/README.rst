data_config
===========

Sumber kebenaran untuk semua pengaturan dan lokasi penyimpanan aplikasi.

English
-------

The single source of truth for every setting and for the app's storage locations.

Berkas
------

``ContentConfig.kt``
    Semua preferensi (tema, bahasa, versi kompiler, opsi compiler, include path,
    ambang explainer, warna kanvas editor, mode auto-close). Setiap setter
    menulis SharedPreferences *dan* cermin JSON di ``Documents/PawnMC/config/``,
    jadi preferensi pulih setelah reinstall.

``AppStorage.kt``
    Akar ``Documents/PawnMC`` per varian (termasuk fallback folder privat),
    lokasi ``workspace/``, ``library/``, ``compiler/``, ``config/``, dan
    deteksi file starter internal.

``AppLocalization.kt``
    Pembaca ``_extraction.toml``: tabel bersarang menjadi kunci
    ``dotted.key.<lang>``, dengan fallback berbasis frasa antar bahasa.

``PathStore.kt``
    Membaca/menulis daftar path pada file kecil (dipakai include path manual).

``TomlRuntimeData.kt``
    Parser TOML minimal yang dipakai ``AppLocalization``.

``SystemNotification.kt``
    Notifikasi sistem singkat (mis. "kompiler berganti, restart diperlukan").

Catatan
-------

* Jangan menambah preferensi baru tanpa pasangan di cermin JSON; kalau tidak,
  nilainya hilang setelah aplikasi diinstal ulang.
* ``AppStorage`` selalu mengembalikan folder yang *bisa ditulis*; pemanggil
  tidak perlu membuat folder sendiri.
