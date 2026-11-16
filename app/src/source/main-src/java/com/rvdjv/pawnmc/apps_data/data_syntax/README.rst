data_syntax
===========

Data bahasa Pawn yang dipakai highlighter dan autocomplete. Tidak ada logika
tampilan; semuanya berupa tabel dan pencarian.

English
-------

The Pawn language data behind highlighting and autocomplete. No display logic:
tables and lookups only.

Berkas
------

``Registry.kt``
    ``PawnRegistry``: apakah sebuah kata adalah keyword, tipe, konstanta,
    fungsi, atau direktif; daftar operator dengan panjang terpanjang dulu; dan
    ``getCompletions(prefix, language)``.

``InternDat.kt``
    Membaca blok internal dari ``_internal.toml`` (kategori ``stock``,
    ``native``, ``forward``, ``public``) dan mengisinya ke ``Registry``.

``Index.kt``
    Indeks nama untuk pencarian cepat pada data internal.

``ServerConfig.kt``
    Daftar direktif ``server.cfg`` (``lanmode``, ``rcon_password``, dst.) dan
    aturan mana yang dianggap direktif.

Catatan warna
-------------

Paket ini tidak menyimpan warna. Slot yang dipakai highlighter adalah
``KEYWORD``, ``IDENTIFIER_NAME``, ``LITERAL``, ``STRING``, ``NUMBER``,
``FUNCTION_NAME``, ``OPERATOR``, ``BRACKET``, ``ANNOTATION``, ``COMMENT`` dan
``COMMENT_KEYWORD`` - semuanya diisi palet hangat di
``user_interface/editor/xedapi/EditorScheme.kt``.
