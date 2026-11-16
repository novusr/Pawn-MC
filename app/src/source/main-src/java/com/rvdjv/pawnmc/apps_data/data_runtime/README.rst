data_runtime
============

Penghubung ke proses PocketSAMP.

English
-------

Bridge to the PocketSAMP process.

Berkas
------

``PocketSampGateway.kt``
    Membuka layar PocketSAMP dari PawnMC dan meneruskan permintaan yang sudah
    disiapkan (path workspace, file aktif). Bila build tidak memuat PocketSAMP,
    gateway mengembalikan ``false`` sehingga UI menampilkan pesan
    ``main.pocketsamp.launch_failed`` dan kembali ke layar asal.
