filebrowser
===========

Penjelajah file dan folder bawaan PawnMC. Dipakai layar utama (memilih file
Pawn) dan sebagai opsi "Built-in browser" saat pengguna harus memilih folder
(include path, workspace Xed).

English
-------

PawnMC's built-in file and folder browser. Used by the main screen (picking a
Pawn file) and as the "Built-in browser" option whenever the user has to choose a
folder (include paths, the Xed workspace).

Berkas
------

``Dialog.kt``
    Dialog full-screen dalam dua mode: ``FileBrowserMode.FILE`` (hanya file
    dengan ekstensi yang diminta) dan ``FileBrowserMode.FOLDER`` (pilih folder
    yang sedang dibuka). ``initialDirectory`` membuat dialog langsung terbuka di
    folder tertentu - dipakai alur include path supaya browser mendarat di path
    yang sudah tersimpan.
