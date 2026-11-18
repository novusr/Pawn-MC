settings
========

Layar pengaturan. Satu file ``Screen.kt`` besar yang berisi semua bagian
(umum, bahasa, tema, kompiler, opsi, include path, pembaruan, tentang), satu
``ViewModel`` yang membaca/menulis ``ContentConfig``, dan satu ``Activity``
pembungkus.

English
-------

The settings screen. One large ``Screen.kt`` that holds every section (general,
language, theme, compiler, options, include paths, updates, about), one
``ViewModel`` that reads and writes ``ContentConfig``, and a thin ``Activity``
wrapper.

Berkas
------

``Screen.kt``
    Seluruh UI pengaturan, termasuk alur include path: tap baris = dialog
    informasi path (path lengkap, folder induk, maksimal 4 folder + 3 file isi),
    lalu tombol *Open folder picker* yang membuka picker tepat di folder itu.
    ``VerticalScrollbar`` menempel di sisi kanan layar.

``ViewModel.kt``
    ``SettingsViewModel``: ekspos preferensi sebagai state, sediakan
    ``addIncludePath`` / ``updateIncludePathAt`` / ``removeIncludePathAt`` dan
    pembersihan path yang hilang.

``Activity.kt``
    ``SettingsActivity`` - menerima ``EXTRA_FOCUS_TARGET`` dari layar utama.

Catatan
-------

* Teks diambil dari ``_extraction.toml`` lewat ``AppLocalization``; key include
  path ada di ``settings.include.*``.
* Folder picker dibuka langsung lewat ``Intent.createChooser`` di atas
  ``ACTION_OPEN_DOCUMENT_TREE``, jadi ZArchiver dan file manager lain ikut
  tersedia di pemilih sistem tanpa daftar pilihan dari aplikasi.
