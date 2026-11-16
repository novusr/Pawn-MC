workspace
=========

Semua yang berkaitan dengan "folder yang sedang dibuka": sesi, pohon file,
panel mengapung, dan pemilihan folder lewat picker.

English
-------

Everything about "the folder currently open": the session, the file tree, the
floating panel, and choosing a folder through a picker.

Berkas
------

``Session.kt``
    ``WorkspaceSession`` - daftar workspace terbuka, dokumen aktif, dirty state,
    pencarian dan penggantian, serta persistensi tab antar sesi.

``Panel.kt``
    Panel explorer mengapung (tab, pohon file, hasil pencarian, dan output
    kompiler). Output kompiler di panel ini punya scrollbar sendiri.

``FolderPicker.kt``
    Intent folder picker (``buildFolderPickerIntent`` /
    ``buildFolderPickerIntentAt``), konversi URI tree <-> ``File``, dan
    ``availableThirdPartyFolderPickers`` untuk mendeteksi ZArchiver dan file
    manager lain yang terpasang.

``FolderSourceDialog.kt``
    ``FolderSourceChooserDialog`` (pilih browser bawaan / picker sistem /
    pihak ketiga), ``IncludePathInfoDialog`` (path lengkap + folder induk + isi
    folder), dan ``BuiltInFolderBrowser``.

Alur pemilihan folder
---------------------

1. Pemanggil memanggil ``availableThirdPartyFolderPickers(context)``.
2. Jika kosong, picker sistem langsung dibuka seperti biasa.
3. Jika ada, ``FolderSourceChooserDialog`` ditampilkan; pilihan pihak ketiga
   dikirim dengan ``setPackage`` ke ``ACTION_OPEN_DOCUMENT_TREE``.
4. Hasil URI diubah menjadi ``File`` oleh ``resolveTreeToFile`` dan diserahkan
   ke pemanggil (workspace baru atau include path baru).
