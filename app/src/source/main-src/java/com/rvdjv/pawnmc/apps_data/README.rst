apps_data
=========

Layer aplikasi yang tidak punya tampilan: konfigurasi, penyimpanan, kompiler, dan
integrasi ke layanan luar. Semua kelas di sini dipanggil oleh ``user_interface``,
tidak ada satu pun yang menggambar UI.

English
-------

Presentation-free application layer: configuration, storage, the native compiler
pipeline and the integrations to outside services. Everything here is called by
``user_interface``; nothing in this folder draws UI.

Isi folder
----------

``data_compiler/``
    Pipeline kompilasi Pawn - ``Runner`` (memuat library native, menyusun argumen),
    ``NativeBridge`` (satu permukaan JNI), ``Compiler`` (fasad), ``Detection``
    (deteksi versi kompiler), ``CaseConversion`` (mode ignore case),
    ``Explainer`` + ``Explanations`` (pesan ``[cause]``), dan ``DiagnosticsStore``
    (``compiler/injector_data.json`` yang dibaca Xed), tanpa kolom state:
    satu kali discard menghapus seluruh record file itu.

``data_config/``
    ``ContentConfig`` (semua preferensi, cermin JSON di ``config/``),
    ``AppStorage`` (lokasi ``Documents/PawnMC`` dan cadangan privat),
    ``AppLocalization`` (``_extraction.toml``), ``PathStore``, ``TomlRuntimeData``,
    ``SystemNotification``.

``data_library/``
    ``LibraryStore`` - mengekstrak dan memverifikasi ``library/<abi>/``.

``data_logging/``
    Riwayat kompilasi (``compiler/Store.kt``), riwayat PocketSAMP
    (``pocket/Store.kt``), dan ekspor log (``LogExport.kt``).

``data_runtime/``
    ``PocketSampGateway`` - satu-satunya jalan menuju server PocketSAMP.

``data_syntax/``
    Data bahasa Pawn: ``Registry`` (kata kunci, fungsi, konstanta),
    ``InternDat``/``Index`` (``_internal.toml``), ``ServerConfig`` (direktif
    ``server.cfg``).

``data_update/``
    ``UpdateManager`` - cek rilis GitHub, unduh, dan serahkan ke installer sistem.

Catatan pemeliharaan
--------------------

* Folder ini tidak boleh memakai kelas Compose. Jika sebuah helper perlu
  ``@Composable``, tempatnya di ``user_interface``.
* Semua akses disk berjalan di ``Dispatchers.IO``; pemanggil dari UI bertanggung
  jawab memindahkan thread.
