data_compiler
=============

Seluruh jalur kompilasi Pawn: memuat library native, menyusun argumen,
menjalankan kompiler, menerjemahkan keluaran, dan menyimpan diagnosa untuk Xed.

English
-------

The whole Pawn compile path: loading the native library, building the arguments,
running the compiler, reading the output back, and storing diagnostics for Xed.

Berkas
------

``Compiler.kt``
    Fasad yang dipakai UI: ``compile``, ``compileManual``, ``detectCompilerVersionForFile``,
    ``discoverRelevantIncludePaths``, ``convertFolderToLowerCase``,
    ``explainCompilerOutput`` (sekarang menerima ``sourcePath``).

``Runner.kt``
    Eksekusi: memilih ABI, memanggil ``NativeBridge``, kebijakan auto-switch
    ``__addressof``, retry ke versi kompiler lain, dan mencatat riwayat.

``NativeBridge.kt``
    Satu-satunya permukaan JNI. Memuat ``lib<versi>.so`` dari ``library/<abi>/``.

``Detection.kt``
    Membaca metadata kompiler di disk untuk menentukan versinya.

``CaseConversion.kt``
    Mode *ignore case*: backup folder lalu menurunkan semua nama file dan
    referensi ``#include``.

``Explainer.kt`` / ``Explanations.kt``
    Mengubah baris
    ``file.pwn(12) : error 017: undefined symbol "x"``
    menjadi ``[cause] ...`` dalam lima bahasa, dan menyerahkan record ke
    ``DiagnosticsStore``.

``DiagnosticsStore.kt``
    Penulis ``compiler/injector_data.json``. Per baris menyimpan
    ``line/code/kind/message/explainer/at``. Tanpa kolom ``state``: satu kali
    *Discard* menghapus seluruh record file itu, dan kompilasi berikutnya
    menulis JSON baru.

``Names.kt``
    Nama file dan flag yang dipakai lintas kelas (mis. nama binary pawncc).

Kontrak explainer
-----------------

* Setiap kompilasi memperbarui JSON, terlepas dari toggle *Explain output*.
* Kompilasi bersih menghapus record file itu; komentar yang sudah tercetak di
  editor tetap ada sampai pengguna menekan *Discard* atau menyimpan file.
* Baris yang sudah membawa komentar tidak pernah ditimpa oleh pass injeksi.
* ``Explainer.explainCompilerOutput(rawText, cacheDir, language, sourcePath)``
  wajib diberi ``sourcePath`` agar kompilasi bersih tahu file mana yang
  recordnya dipensiunkan.
