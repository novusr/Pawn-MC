data_logging
============

Penyimpanan riwayat dan ekspor log. Semua penulisan berjalan di
``Dispatchers.IO`` dan dibatasi jumlah entrinya agar folderlog tidak tumbuh
tanpa batas.

English
-------

History storage and log export. Every write runs on ``Dispatchers.IO`` and the
entry count is capped so the log folders cannot grow without bound.

Berkas
------

``compiler/Store.kt``
    ``_compiler_store`` - riwayat kompilasi: waktu, path sumber, exit code,
    durasi, versi kompiler, dan keluaran mentah.

``pocket/Store.kt``
    Riwayat keluaran server PocketSAMP.

``LogExport.kt``
    ``_log_export`` - kepala log (tanggal, versi app, perangkat, info
    kompilasi) plus penulisan file ``.log`` dan penyalinan ke clipboard dengan
    pemotongan aman untuk log panjang.
