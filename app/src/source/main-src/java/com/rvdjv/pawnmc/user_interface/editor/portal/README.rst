portal
======

Developer Portal - terminal Pawn di dalam Xed Editor. Pengguna mengetik perintah
(``pawncc args`` dan sejenisnya), portal menjalankannya lewat ``Runner``, lalu
menampilkan keluaran bergaya markdown. Tidak ada akses file langsung: semua
kompilasi lewat ``Compiler``/``Runner``.

English
-------

The Developer Portal - a Pawn terminal inside Xed Editor. The user types commands
(``pawncc args`` and friends), the portal runs them through ``Runner`` and renders
the output as markdown. No direct file access: every compile goes through
``Compiler``/``Runner``.

Berkas
------

``PortalMain.kt``
    Composable utama portal: input perintah, riwayat, status kompilasi, dan
    tombol salin.

``PortalCommandParser.kt``
    Memecah baris perintah menjadi verb, argumen, dan opsi.

``PortalDocs.kt``
    Dokumentasi perintah yang ditampilkan di panel bantuan.

``PortalText.kt``
    Semua teks portal dalam lima bahasa (ID/EN/TL/ES/RU).

``MarkdownOutput.kt``
    Renderer markdown ringan untuk keluaran kompiler dan dokumentasi.

``CommandSuggestionGrid.kt``
    Grid saran perintah yang muncul saat input kosong.
