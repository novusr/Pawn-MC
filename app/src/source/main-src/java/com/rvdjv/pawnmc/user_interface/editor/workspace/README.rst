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
    ``buildFolderPickerIntentAt``) dan konversi URI tree <-> ``File``.

``FolderSourceDialog.kt``
    ``IncludePathInfoDialog`` (path lengkap + folder induk + isi folder) dan
    ``BuiltInFolderBrowser`` (penjelajah bawaan untuk folder app-private).

Alur pemilihan folder
---------------------

1. Pemanggil membungkus intent dari ``buildFolderPickerIntent(At)`` dengan
   ``Intent.createChooser``; aplikasi yang memahami ``ACTION_OPEN_DOCUMENT_TREE``
   (ZArchiver, file manager lain) muncul langsung di pemilih sistem tanpa daftar
   tambahan dari aplikasi.
2. Hasil URI diubah menjadi ``File`` oleh ``resolveTreeToFile`` dan diserahkan
   ke pemanggil (workspace baru atau include path baru).
