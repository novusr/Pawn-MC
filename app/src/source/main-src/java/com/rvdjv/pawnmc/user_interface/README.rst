user_interface
==============

Semua layar Compose dan widget Android yang dipakai PawnMC. Setiap subfolder di
sini adalah satu permukaan UI, bukan satu fitur data - logika data hidup di
``apps_data``.

English
-------

All Compose screens and Android widgets of PawnMC. Each subfolder is one UI
surface, not one data feature - the data logic lives in ``apps_data``.

Isi folder
----------

``main/``
    Layar utama (``MainScreen``), host aktivitas (``MainActivity``), view model
    kompilasi, notifikasi sesi, dan kartu sorotan pengaturan.

``settings/``
    ``Activity``, ``Screen`` dan ``ViewModel`` pengaturan. Berisi seluruh
    preferensi, termasuk daftar include path beserta dialog informasi path.

``filebrowser/``
    ``FileBrowserDialog`` - penjelajah file/folder bawaan yang dipakai sebagai
    alternatif folder picker sistem.

``editor/``
    Xed Editor: ``xedapi/`` (layar, widget Sora, skema warna, scrollbar gutter,
    injeksi explainer), ``syntax/`` (highlighter Pawn dan ``server.cfg``),
    ``workspace/`` (sesi workspace, panel pohon file dan folder picker),
    ``portal/`` (Developer Portal / Pawn terminal).

``theme/``
    Warna, tipografi dan pembungkus tema Material 3.

``common/``
    Komponen yang dipakai lebih dari satu layar - saat ini
    ``VerticalScrollbar`` untuk layar utama, pengaturan, dan log kompiler.

Aturan singkat
--------------

* Satu layar = satu file ``Screen.kt``; state milik layar tinggal di
  ``remember``, state milik proses tinggal di ``ViewModel``.
* Jangan memanggil kompiler atau menulis file langsung dari composable: lempar
  ke ``viewModelScope`` / ``Dispatchers.IO``.
* Teks UI yang terlihat pengguna diambil lewat
  ``AppLocalization.get(key, language, fallback)``.
