syntax
======

Highlighter dan pelengkap kode untuk Xed Editor. Tidak ada UI Compose di sini:
semua kelas mengimplementasikan antarmuka ``Language`` / ``AnalyzeManager``
milik Sora, dan warna diambil dari slot ``EditorColorScheme`` yang dipasang
``xedapi/EditorScheme.kt``.

English
-------

Highlighting and completion for Xed Editor. No Compose here: every class
implements a Sora ``Language`` / ``AnalyzeManager`` interface, and colours come
from the ``EditorColorScheme`` slots installed by ``xedapi/EditorScheme.kt``.

Berkas
------

``CodeLanguage.kt``
    ``Language`` untuk file Pawn: analisis, autocomplete (registry + fungsi
    lokal), indentasi, dan symbol pairs.

``CodeAnalyzer.kt``
    Lexer Pawn. Memetakan token ke ``SpanKind``: keyword, tipe, konstanta,
    fungsi, identifier, direktif, string, angka, komentar, operator, dan
    ``Bracket`` untuk ``( ) [ ] { }`` dengan warna oranye pekat + tebal.

``CodeSymbols.kt``
    Mendeteksi deklarasi ``stock`` / ``public`` / ``forward`` / ``native`` di
    file yang sedang dibuka, agar namanya ikut muncul di autocomplete.

``CodeLanguages.kt``
    Memilih bahasa berdasarkan ekstensi file (``.pwn``/``.p``/``.inc``/``.pawn``
    -> Pawn, ``server.cfg`` -> config).

``ServerConfigAnalyzer.kt`` + ``ServerConfigLanguage.kt``
    Highlighter baris-per-baris untuk ``server.cfg`` memakai palet yang sama
    (direktif amber, nilai persik, angka rose, ``=`` dan ``;`` oranye).
