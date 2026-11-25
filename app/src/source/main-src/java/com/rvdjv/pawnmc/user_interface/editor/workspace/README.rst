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
    ``buildFolderPickerIntentAt``), konversi URI tree/documen <-> ``File`` untuk
    semua volume (``primary:``, ``emulated:``, ``home:``, volume eksternal), serta
    ``resolveOpenableDocument`` yang menyalin dokumen tanpa path lokal ke
    ``cache/open/`` supaya tetap bisa dibuka di editor.

``FolderSourceDialog.kt``
    ``IncludePathInfoDialog`` (path lengkap + folder induk + isi folder),
    ``FolderPickerSourceDialog`` (pilih "System picker" atau "Browse inside
    PawnMC"), dan ``BuiltInFolderBrowser`` (penjelajah bawaan untuk folder
    app-private).

Alur pemilihan folder
---------------------

1. ``FolderPickerSourceDialog`` menawarkan dua sumber. Pemilih sistem adalah
   ``buildFolderPickerIntent(At)`` dibungkus ``Intent.createChooser``; penjelajah
   bawaan adalah ``BuiltInFolderBrowser``.
2. ZArchiver dan teman-temannya **tidak** muncul di pemilih folder karena mereka
   hanya mendaftarkan ``ACTION_OPEN_DOCUMENT`` (file), bukan
   ``ACTION_OPEN_DOCUMENT_TREE`` (folder) - bukan karena chooser kita salah.
3. Hasil URI diubah menjadi ``File`` oleh ``resolveTreeToFile`` dan diserahkan
   ke pemanggil (workspace baru atau include path baru).
4. Folder milik PawnMC sendiri (``Documents/PawnMC/workspace``, tempat
   ``workspace.json`` berada) tidak pernah dibuka sebagai workspace dan tidak
   pernah disimpan ke daftar workspace.
