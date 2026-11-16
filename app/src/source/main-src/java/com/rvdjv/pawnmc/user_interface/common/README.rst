common
======

Komponen UI yang dipakai lebih dari satu layar. Folder ini sengaja kecil: kalau
sebuah komponen hanya dipakai satu layar, tempatnya di folder layar itu.

English
-------

UI components shared by more than one screen. The folder is deliberately small:
a component used by a single screen belongs in that screen's folder.

Berkas
------

``VerticalScrollbar.kt``
    Scrollbar vertikal Compose untuk:
    ``MainScreen`` (``LazyListState``), ``SettingsScreen`` (``ScrollState``),
    dan panel output kompiler (``ScrollState``). Angka di atas dan di bawah
    thumb adalah unit pertama dan terakhir yang tercakup thumb - nomornya ikut
    bergerak saat digulir, sama seperti scrollbar gutter Xed Editor.
