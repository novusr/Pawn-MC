xedapi
======

Inti Xed Editor: layar Compose, widget Sora, warna, scrollbar, dan jembatan ke
kompiler. Paket ini adalah satu-satunya tempat yang menyentuh ``CodeEditor``
milik Sora secara langsung.

English
-------

The core of Xed Editor: the Compose screen, the Sora widget, colours, the gutter
scrollbar and the compiler bridge. This is the only package that touches Sora's
``CodeEditor`` directly.

Berkas dan tugasnya
-------------------

``EditorScreen.kt``
    Layar editor. Menyusun tab strip, kanvas kode, panel workspace mengapung,
    Developer Portal, bilah simbol cepat, bilah status, dan seluruh dialog
    (baru file, folder, jump, hash, about, pemilih sumber folder). Juga
    menjalankan loop sinkronisasi explainer.

``XedCodeEditor.kt``
    Subclass ``CodeEditor``: kolom-kolom auto-close, Ctrl+A/C/X/V untuk keyboard
    fisik, dan ``ColumnLineNumberRenderer`` yang menggambar chip ``baris-kolom``
    di gutter.

``EditorScheme.kt``
    ``buildEditorScheme`` - membangun ``EditorColorScheme`` dari tema aplikasi
    atau warna kanvas pilihan pengguna, lalu memasang palet sintaks hangat
    (keyword amber, fungsi emas, tipe persik, tanda kurung oranye pekat).

``GutterScrollbar.kt``
    Scrollbar vertikal di sisi kanan kode. Menampilkan dua angka: baris di tepi
    atas thumb dan baris di tepi bawah thumb. Bisa di-tap untuk lompat dan
    di-drag untuk menggulir.

``ExplainInjector.kt``
    Pembaca ``compiler/injector_data.json``. Menambahkan komentar ``// [cause]``
    pada baris yang dilaporkan kompilasi, tidak pernah menimpa komentar yang
    sudah ada (komentar milik pengguna), dan menyediakan aksi Discard yang
    menghapus seluruh record file itu.

``EditorViewModel.kt``
    State dokumen tunggal (file, konten, dirty flag) dan penyimpanan.

``ToolPanel.kt``
    Panel cari/ganti (workspace maupun per file).

``EditorScreen.kt`` juga memegang bilah status (posisi kursor, status simpan)
dan meng-host ``BuiltInFolderBrowser``; folder picker dibuka langsung lewat
``Intent.createChooser`` di atas ``ACTION_OPEN_DOCUMENT_TREE``, tanpa dialog
pemilih sumber tersendiri.

Alur penting
------------

1. Kompilasi berjalan di ``MainViewModel``; hasilnya menulis ``injector_data.json``.
2. ``diagnosticsRevision`` naik, ``LaunchedEffect`` di ``EditorScreen`` memanggil
   ``ExplainInjector.apply`` sehingga komentar muncul saat itu juga.
3. Pengguna memilih *Discard*: komentar dihapus dari buffer dan dari file
   simpanan, seluruh record file itu dihapus dari JSON.
4. Kompilasi bersih tidak menghapus komentar yang sudah tampil - hanya record
   file itu yang dikosongkan.
