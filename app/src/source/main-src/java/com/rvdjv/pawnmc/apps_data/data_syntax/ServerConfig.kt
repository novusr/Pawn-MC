package com.rvdjv.pawnmc.apps_data.data_syntax

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig

/**
 * SA-MP `server.cfg` vocabulary shared by the highlighter and the completion popup.
 *
 * Every entry below was checked against the server that consumes it: the
 * `AddVariable` / `AddStringVariable` registrations in `main.cpp` and the code
 * that reads each one afterwards. Defaults are the ones `main.cpp` passes in.
 */
object ServerConfig {

    /** Extensions of the files this language serves, without the dot.
     *
     * `.log` shares the plain key/value style of a server configuration, so the
     * same highlighter keeps plain text readable instead of applying Pawn rules.
     */
    val EXTENSIONS: Set<String> = setOf("cfg", "log")

    /** Accepted value, used only to decorate the completion text. */
    private val VALUE_HINTS: Map<String, String> = mapOf(
        "announce" to "0|1",
        "maxplayers" to "0..1000",
        "port" to "1..65535",
        "bind" to "0.0.0.0",
        "lanmode" to "0|1",
        "query" to "0|1",
        "logqueries" to "0|1",
        "rcon_port" to "1..65535",
        "rcon_maxadmins" to "1..10",
        "rcon_bind" to "IP address",
        "output" to "0|1",
        "mtu" to "576..1492",
        "timestamp" to "0|1",
        "logtimeformat" to "[%H:%M:%S]",
        "password" to "\"password\"",
        "hostname" to "\"SA:MP Server\"",
        "mapname" to "\"San Andreas\"",
        "language" to "\"English\"",
        "weburl" to "\"\"",
        "rcon_password" to "\"password\"",
        "gravity" to "0.008",
        "weather" to "0..20",
        "tirepopping" to "0|1",
        "gamemodetext" to "\"Unknown\"",
        "filterscripts" to "name name ...",
        "plugins" to "name.so name.so ...",
        "nosign" to "0|1",
        "anticheat" to "0|1",
        "instagib" to "0|1",
        "myriad" to "0|1",
        "chatlogging" to "0|1",
        "messageholelimit" to "1000..100000",
        "playertimeout" to "milliseconds",
        "db_logging" to "0|1",
        "db_log_queries" to "0|1",
        "onfoot_rate" to "milliseconds",
        "incar_rate" to "milliseconds",
        "maxplayerperip" to "0..",
        "stream_rate" to "milliseconds",
        "stream_distance" to "0..1000",
        "sleep" to "milliseconds",
        "connseedtime" to "milliseconds",
        "conncookies" to "0|1",
        "cookielogging" to "0|1",
        "useartwork" to "0|1",
        "artpath" to "path",
        "artwork" to "archive.zip",
        "version" to "\"0.3.7-R2\"",
    )

    private val DESCRIPTIONS: Map<String, String> = mapOf(
        "announce" to "Registers the server on the SA-MP master list so it shows in the launcher. Default 0.",
        "maxplayers" to "Number of player slots; clamped to 0..1000. What GetMaxPlayers returns.",
        "port" to "UDP port the server listens on, clamped to 1..65535. Default 7777.",
        "bind" to "IP the server binds to. Empty binds every interface; a value is read-only after start.",
        "lanmode" to "Makes the server skip the strict master-list checks for LAN play. Default 0.",
        "query" to "Answers server queries from browsers and the launcher. Default 1.",
        "logqueries" to "Writes every query the server receives to the log. Default 0.",
        "rcon_port" to "Separate RCON port; only compiled in when the RCON socket is enabled.",
        "rcon_maxadmins" to "How many RCON administrators may be logged in at once.",
        "rcon_bind" to "IP the RCON listener binds to, distinct from the game socket.",
        "output" to "Linux only: mirrors the log into the console output file. Default 1.",
        "mtu" to "Maximum packet size, clamped to 576..1492. Default 576.",
        "timestamp" to "Prefixes log lines with the time produced by logtimeformat. Default 0.",
        "logtimeformat" to "strftime pattern for log timestamps, e.g. [%H:%M:%S]. Only used with timestamp=1.",
        "password" to "Password a client must send before joining; empty means open server.",
        "hostname" to "Server name shown in the browser and the client. Default \"SA:MP Server\".",
        "mapname" to "Map name the client shows while connecting. Default \"San Andreas\".",
        "language" to "Language reported to the client, e.g. English or Deutsch. Default English.",
        "weburl" to "Site the client opens; also advertised in the server info.",
        "rcon_password" to "Remote console password. An empty value disables RCON login.",
        "gravity" to "World gravity pushed to every player. Default 0.008.",
        "weather" to "Weather id applied to the whole world, 0..20.",
        "tirepopping" to "Tyre popping. Present in older configs; the current server no longer registers it.",
        "gamemodetext" to "Gamemode name clients see while connecting. Default \"Unknown\".",
        "filterscripts" to "Filter scripts loaded on top of gamemode0; names without the .amx suffix.",
        "plugins" to "Plugins loaded before the gamemode; each name is the library without its extension.",
        "nosign" to "Skips the AMX signature check when the server is built with signing enabled.",
        "anticheat" to "Legacy movement checks. The current server sources compile this out.",
        "instagib" to "Every hit kills regardless of weapon damage.",
        "myriad" to "Legacy SA-MP mode switch, kept for compatibility with old configs.",
        "chatlogging" to "Writes player chat to the server log. Default 1.",
        "messageholelimit" to "Out-of-order packet tolerance per player, clamped to 1000..100000.",
        "playertimeout" to "Milliseconds without traffic before a player is timed out. Default 10000.",
        "db_logging" to "Logs database activity of the gamemode. Default 0.",
        "db_log_queries" to "Logs the SQL text too, not only timing. Default 0.",
        "onfoot_rate" to "On-foot sync send rate in milliseconds. Default 30.",
        "incar_rate" to "In-car sync send rate in milliseconds. Default 30.",
        "maxplayerperip" to "Connections allowed from one IP address; 0 means unlimited.",
        "stream_rate" to "Object streaming update rate in milliseconds. Default 1000.",
        "stream_distance" to "Distance at which objects stream in, clamped to 0..1000. Default 300.",
        "sleep" to "Milliseconds the main loop sleeps per iteration. Default 5.",
        "connseedtime" to "Milliseconds between connection cookie re-seeds. Default 300000.",
        "conncookies" to "Requires the connection cookie handshake (anti-spoofing). Default 1.",
        "cookielogging" to "Logs connection cookie traffic. Default 0.",
        "useartwork" to "Enables the custom artwork download for clients.",
        "artpath" to "Directory that serves the artwork archive.",
        "artwork" to "Artwork archive downloaded by clients; needs artpath and useartwork.",
        "version" to "Informational version marker written by the configured server.",
    )

    private val INDONESIAN: Map<String, String> = mapOf(
        "announce" to "Mendaftarkan server ke master list SA-MP agar tampil di launcher. Bawaan 0.",
        "maxplayers" to "Jumlah slot pemain; dipangkas ke 0..1000. Nilai yang dibaca GetMaxPlayers.",
        "port" to "Port UDP server, dipangkas ke 1..65535. Bawaan 7777.",
        "bind" to "IP tempat server mengikat soket. Kosong berarti semua antarmuka; setelah start tidak bisa diubah.",
        "lanmode" to "Membuat server melewati pemeriksaan master list yang ketat untuk LAN. Bawaan 0.",
        "query" to "Menjawab query dari browser server dan launcher. Bawaan 1.",
        "logqueries" to "Menulis setiap query yang diterima server ke log. Bawaan 0.",
        "rcon_port" to "Port RCON terpisah; hanya ikut dikompilasi bila soket RCON diaktifkan.",
        "rcon_maxadmins" to "Jumlah admin RCON yang boleh login bersamaan.",
        "rcon_bind" to "IP tempat pendengar RCON mengikat soket, terpisah dari soket permainan.",
        "output" to "Khusus Linux: menyalin log ke berkas output konsol. Bawaan 1.",
        "mtu" to "Ukuran paket maksimum, dipangkas ke 576..1492. Bawaan 576.",
        "timestamp" to "Menambahkan waktu dari logtimeformat di awal baris log. Bawaan 0.",
        "logtimeformat" to "Pola strftime untuk penanda waktu log, mis. [%H:%M:%S]. Hanya dipakai bila timestamp=1.",
        "password" to "Sandi yang harus dikirim client sebelum bergabung; kosong berarti server terbuka.",
        "hostname" to "Nama server di browser dan client. Bawaan \"SA:MP Server\".",
        "mapname" to "Nama map yang ditampilkan client saat menyambung. Bawaan \"San Andreas\".",
        "language" to "Bahasa yang dilaporkan ke client, mis. English atau Deutsch. Bawaan English.",
        "weburl" to "Situs yang dibuka client; ikut diiklankan pada info server.",
        "rcon_password" to "Sandi remote console. Nilai kosong mematikan login RCON.",
        "gravity" to "Gravitasi dunia yang dikirim ke semua pemain. Bawaan 0.008.",
        "weather" to "ID cuaca untuk seluruh dunia, 0..20.",
        "tirepopping" to "Ban pecah. Ada di config lama; server sekarang tidak lagi mendaftarkannya.",
        "gamemodetext" to "Nama gamemode yang dilihat client saat menyambung. Bawaan \"Unknown\".",
        "filterscripts" to "Filter script yang dimuat di atas gamemode0; nama tanpa akhiran .amx.",
        "plugins" to "Plugin yang dimuat sebelum gamemode; tiap nama adalah nama pustaka tanpa ekstensi.",
        "nosign" to "Melewati pemeriksaan tanda tangan AMX bila server dibangun dengan penandatanganan.",
        "anticheat" to "Pemeriksaan gerak lama. Sumber server sekarang tidak mengompilasinya.",
        "instagib" to "Setiap tembakan langsung membunuh tanpa memperhitungkan damage senjata.",
        "myriad" to "Saklar mode SA-MP lama, dipertahankan untuk kompatibilitas config lama.",
        "chatlogging" to "Menulis obrolan pemain ke log server. Bawaan 1.",
        "messageholelimit" to "Toleransi paket tidak berurutan per pemain, dipangkas ke 1000..100000.",
        "playertimeout" to "Milidetik tanpa data sebelum pemain di-timeout. Bawaan 10000.",
        "db_logging" to "Mencatat aktivitas database gamemode. Bawaan 0.",
        "db_log_queries" to "Mencatat teks SQL, bukan hanya waktunya. Bawaan 0.",
        "onfoot_rate" to "Laju sinkronisasi pemain berjalan kaki dalam milidetik. Bawaan 30.",
        "incar_rate" to "Laju sinkronisasi pemain di kendaraan dalam milidetik. Bawaan 30.",
        "maxplayerperip" to "Koneksi yang diizinkan dari satu alamat IP; 0 berarti tanpa batas.",
        "stream_rate" to "Laju pembaruan object streaming dalam milidetik. Bawaan 1000.",
        "stream_distance" to "Jarak munculnya object, dipangkas ke 0..1000. Bawaan 300.",
        "sleep" to "Milidetik jeda loop utama tiap iterasi. Bawaan 5.",
        "connseedtime" to "Milidetik antar pembaruan seed cookie koneksi. Bawaan 300000.",
        "conncookies" to "Mewajibkan handshake cookie koneksi (anti-spoofing). Bawaan 1.",
        "cookielogging" to "Mencatat lalu lintas cookie koneksi. Bawaan 0.",
        "useartwork" to "Mengaktifkan unduhan artwork kustom untuk client.",
        "artpath" to "Folder yang menyajikan arsip artwork.",
        "artwork" to "Arsip artwork yang diunduh client; butuh artpath dan useartwork.",
        "version" to "Penanda versi informatif yang ditulis server.",
    )

    /** Every directive, in the order the completion popup should offer them. */
    val DIRECTIVES: List<String> by lazy {
        buildList {
            addAll(VALUE_HINTS.keys)
            for (index in 0..15) add("gamemode$index")
        }
    }

    val DIRECTIVE_ORDER: Map<String, Int> by lazy {
        DIRECTIVES.withIndex().associate { (index, name) -> name to index }
    }

    private val directiveSet: Set<String> by lazy { DIRECTIVES.toSet() }

    /** `gamemode0` .. `gamemode15`, matched without materialising the whole list. */
    private val gamemodePattern = Regex("""gamemode(?:1[0-5]|[0-9])""")

    fun isDirective(word: String): Boolean =
        word in directiveSet || gamemodePattern.matches(word)

    fun isListDirective(word: String): Boolean = word in LIST_DIRECTIVES

    fun isGamemode(word: String): Boolean = gamemodePattern.matches(word)

    fun descriptionFor(word: String, language: CompilerConfig.AppLanguage): String {
        if (isGamemode(word)) return gamemodeDescription(word, language)
        val english = DESCRIPTIONS[word] ?: return ""
        return if (language == CompilerConfig.AppLanguage.ID) INDONESIAN[word] ?: english else english
    }

    private fun gamemodeDescription(word: String, language: CompilerConfig.AppLanguage): String {
        val index = word.removePrefix("gamemode").toInt()
        val english = "Loads script slot $index. Repeat counts keep one script in several slots."
        if (language != CompilerConfig.AppLanguage.ID) return english
        return "Memuat skrip pada slot $index. Jumlah pengulangan menaruh satu skrip di beberapa slot."
    }

    /** Completion text: directive name followed by its value hint. */
    fun usageFor(word: String): String {
        if (isGamemode(word)) return "$word <name> [count]"
        val hint = VALUE_HINTS[word] ?: return word
        return "$word $hint"
    }

    fun valueHintFor(word: String): String? = if (isGamemode(word)) "name [count]" else VALUE_HINTS[word]

    fun completions(
        prefix: String,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): List<String> {
        val query = prefix.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        return DIRECTIVES.filter { it.startsWith(query) }.sortedBy { DIRECTIVE_ORDER[it] ?: 0 }
    }

    /** Directives whose value can be repeated on the same line. */
    private val LIST_DIRECTIVES: Set<String> = setOf("filterscripts", "plugins")

    /** Keys `key = value` or `key value`; whichever separator precedes [column]. */
    fun directiveAt(line: CharSequence, column: Int): String? {
        if (column !in 0..line.length) return null
        val before = line.subSequence(0, column).toString().trimStart()
        val boundary = before.indexOfFirst { it == '=' || it == ' ' || it == '\t' }
        if (boundary < 0) return null
        val leading = before.substring(0, boundary).trim()
        if (leading.isEmpty()) return null
        return leading.takeIf { isDirective(it) }
    }
}
