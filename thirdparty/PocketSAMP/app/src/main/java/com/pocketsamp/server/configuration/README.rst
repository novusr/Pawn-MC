configuration
=============

Konfigurasi runtime PocketSAMP: pembacaan dan penulisan ``server.cfg`` tanpa
merusak komentar atau urutan baris yang sudah ada.

English
-------

PocketSAMP runtime configuration: reading and writing ``server.cfg`` without
disturbing existing comments or line order.

Berkas
------

``Config.kt``
    Model konfigurasi (mode lan, ``rcon_password``, port, dst.).

``Reader.kt``
    Membaca nilai dari ``server.cfg`` dengan pencocokan kunci yang toleran
    terhadap spasi dan huruf besar-kecil.

``Writer.kt``
    Menulis nilai kembali ke baris yang sama; kunci yang belum ada ditambahkan
    di akhir, bukan menimpa isi.

Catatan
-------

Folder kerja server (dan konversi URI tree dari folder picker) dikelola
``data/_DirectoryManager``, bukan di sini.
