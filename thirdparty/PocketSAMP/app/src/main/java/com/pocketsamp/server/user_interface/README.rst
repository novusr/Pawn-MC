user_interface
==============

Seluruh layar Compose PocketSAMP.

English
-------

Every Compose screen of PocketSAMP.

Folder
------

``screens/``
    Layar per fitur (``FileManager``, server, database, pengaturan, dll.).
    ``FileManager`` adalah yang paling sering bersinggungan dengan PawnMC: ia
    membuka Xed lewat intent ``OPEN_XED_PATH`` dan memilih folder server lewat
    ``ACTION_OPEN_DOCUMENT_TREE``. Sebelum picker dibuka, layar ini menawarkan
    picker sistem atau file manager pihak ketiga (ZArchiver) bila terpasang.

``theme/``
    ``SampThemeColors`` dan pembungkus tema PocketSAMP (dipakai semua layar di
    ``screens/``).

Catatan
-------

Tidak ada folder ``navigation/`` atau ``components/``: navigasi ditangani
aktivitas di ``service_handler``, dan komponen kecil tinggal di file layar yang
memakainya.
