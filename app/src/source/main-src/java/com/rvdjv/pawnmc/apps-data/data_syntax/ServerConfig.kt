package com.rvdjv.pawnmc.apps-data.data_syntax

import com.rvdjv.pawnmc.apps-data.data_config.CompilerConfig

/** SA-MP `server.cfg` vocabulary shared by the highlighter and the completion popup. */
object ServerConfig {

    /** Extensions of the files this language serves, without the dot. */
    val EXTENSIONS: Set<String> = setOf("cfg")

    /** Numeric range a directive accepts, used only to decorate the completion text. */
    private val VALUE_HINTS: Map<String, String> = mapOf(
        "announce" to "0|1",
        "maxplayers" to "1..1000",
        "port" to "1..65535",
        "bind" to "0.0.0.0",
        "lanmode" to "0|1",
        "query" to "0|1",
        "logqueries" to "0|1",
        "rcon_port" to "1..65535",
        "rcon_maxadmins" to "0..",
        "rcon_bind" to "IP address",
        "output" to "0|1",
        "mtu" to "bytes",
        "timestamp" to "0|1",
        "logtimeformat" to "%H:%M:%S",
        "logtimeformat2" to "%H:%M:%S",
        "password" to "password",
        "hostname" to "\"Server name\"",
        "mapname" to "\"Map name\"",
        "language" to "English",
        "weburl" to "url",
        "rcon_password" to "password",
        "gravity" to "0.008",
        "weather" to "0..20",
        "tirepopping" to "0|1",
        "gamemodetext" to "\"Gamemode name\"",
        "filterscripts" to "name ...",
        "plugins" to "name.so ...",
        "nosign" to "0|1",
        "anticheat" to "0|1",
        "instagib" to "0|1",
        "myriad" to "0|1",
        "chatlogging" to "0|1",
        "messageholelimit" to "ms",
        "playertimeout" to "ms",
        "db_logging" to "0|1",
        "db_log_queries" to "0|1",
        "onfoot_rate" to "ms",
        "incar_rate" to "ms",
        "maxplayerperip" to "count",
        "stream_rate" to "ms",
        "stream_distance" to "units",
        "sleep" to "ms",
        "connseedtime" to "ms",
        "conncookies" to "0|1",
        "cookielogging" to "0|1",
        "useartwork" to "0|1",
        "artpath" to "path",
        "artwork" to "file.zip",
        "version" to "string",
    )

    /** Directives whose value can be repeated on the same line. */
    private val LIST_DIRECTIVES: Set<String> = setOf(
        "filterscripts",
        "plugins",
        "artwork",
    )

    private val DESCRIPTIONS: Map<String, String> = mapOf(
        "announce" to "Advertises the server on the SA-MP master list (0 disables it).",
        "maxplayers" to "Slot count of the server, one client per slot.",
        "port" to "UDP port the SA-MP server listens on.",
        "bind" to "IP address the server binds to; empty or 0.0.0.0 listens on every interface.",
        "lanmode" to "LAN mode: protective mode that relaxes the query flood limits.",
        "query" to "Answers server queries from the server browser and SA-MP launchers.",
        "logqueries" to "Logs every query received by the server.",
        "rcon_port" to "Separate port for RCON; falls back to `port` when unset.",
        "rcon_maxadmins" to "How many RCON administrators may be logged in at once.",
        "rcon_bind" to "IP address RCON binds to, independent of the game socket.",
        "output" to "Writes the server log to a file next to the server.",
        "mtu" to "Maximum packet size sent to clients.",
        "timestamp" to "Prefixes every log line with a timestamp.",
        "logtimeformat" to "strftime pattern used by `timestamp`.",
        "password" to "Password a client must send before joining (empty = open server).",
        "hostname" to "Name shown in the server browser.",
        "mapname" to "Map name shown in the server browser and client menu.",
        "language" to "Language reported to clients (English, Deutsch, ...).",
        "weburl" to "Web address the client downloads missing resources from.",
        "rcon_password" to "Remote console password; empty disables RCON.",
        "gravity" to "World gravity applied to players.",
        "weather" to "Weather id applied to the whole server (0..20).",
        "tirepopping" to "Tyre popping on/off.",
        "gamemodetext" to "Gamemode name shown to the client while connecting.",
        "filterscripts" to "Filter scripts loaded on top of the gamemode.",
        "plugins" to "Server plugins loaded next to the gamemode.",
        "nosign" to "Disables script signature checking.",
        "anticheat" to "Enables the built-in movement/heartbeat checks.",
        "instagib" to "Instagib death-match mode (one hit per kill).",
        "myriad" to "Unused legacy SA-MP mode switch.",
        "chatlogging" to "Writes player chat to the server log.",
        "messageholelimit" to "Maximum tolerated holes in the client packet sequence before a kick.",
        "playertimeout" to "Milliseconds without traffic before a player times out.",
        "db_logging" to "Logs database activity of the gamemode.",
        "db_log_queries" to "Logs the SQL query text itself, not only the timing.",
        "onfoot_rate" to "On-foot sync update rate in milliseconds.",
        "incar_rate" to "In-car sync update rate in milliseconds.",
        "maxplayerperip" to "Maximum number of connections allowed from one IP address.",
        "stream_rate" to "Object streaming update rate in milliseconds.",
        "stream_distance" to "Distance at which objects stream in.",
        "sleep" to "Milliseconds the server sleeps between main loop iterations.",
        "connseedtime" to "Interval for re-seeding the connection cookie table.",
        "conncookies" to "Requires the connection cookie handshake (anti-spoofing).",
        "cookielogging" to "Logs connection cookie traffic.",
        "useartwork" to "Enables the custom artwork download (needs `artpath`/`artwork`).",
        "artpath" to "Directory serving the artwork archive.",
        "artwork" to "Artwork archive downloaded by clients.",
        "version" to "Version marker written by the configured server.",
    )

    private val INDONESIAN: Map<String, String> = mapOf(
        "announce" to "Mendaftarkan server ke master list SA-MP (0 = tidak tampil).",
        "maxplayers" to "Jumlah slot pemain di server.",
        "port" to "Port UDP yang dipakai server SA-MP.",
        "bind" to "Alamat IP yang di-bind server; 0.0.0.0 berarti semua antarmuka.",
        "lanmode" to "Mode LAN: meredakan batas query agar tidak drop di jaringan lokal.",
        "query" to "Menjawab query dari server browser dan launcher SA-MP.",
        "logqueries" to "Mencatat setiap query yang diterima server.",
        "rcon_port" to "Port terpisah untuk RCON; memakai `port` bila kosong.",
        "rcon_maxadmins" to "Jumlah admin RCON yang boleh login bersamaan.",
        "rcon_bind" to "Alamat IP yang di-bind khusus untuk RCON.",
        "output" to "Menulis log server ke berkas di samping server.",
        "mtu" to "Ukuran paket maksimum yang dikirim ke client.",
        "timestamp" to "Menambahkan penanda waktu di awal setiap baris log.",
        "logtimeformat" to "Format strftime untuk `timestamp`.",
        "password" to "Sandi yang harus dikirim client sebelum bergabung.",
        "hostname" to "Nama server di server browser.",
        "mapname" to "Nama map yang tampil di server browser.",
        "language" to "Bahasa yang dilaporkan ke client.",
        "weburl" to "Alamat web untuk mengunduh resource yang belum ada di client.",
        "rcon_password" to "Sandi remote console; kosong berarti RCON dimatikan.",
        "gravity" to "Gravitasi dunia yang diterapkan ke pemain.",
        "weather" to "ID cuaca untuk seluruh server (0..20).",
        "tirepopping" to "Mengaktifkan/mematikan ban pecah.",
        "gamemodetext" to "Nama gamemode yang ditampilkan ke client saat menyambung.",
        "filterscripts" to "Filter script yang dimuat di atas gamemode.",
        "plugins" to "Plugin server yang dimuat bersama gamemode.",
        "nosign" to "Mematikan pemeriksaan tanda tangan skrip.",
        "anticheat" to "Mengaktifkan pemeriksaan gerak/heartbeat bawaan.",
        "instagib" to "Mode instagib: satu tembakan langsung membunuh.",
        "myriad" to "Saklar mode lama SA-MP yang tidak dipakai lagi.",
        "chatlogging" to "Menulis obrolan pemain ke log server.",
        "messageholelimit" to "Toleransi celah nomor paket client sebelum di-kick.",
        "playertimeout" to "Waktu tanpa data (ms) sebelum pemain dianggap timeout.",
        "db_logging" to "Mencatat aktivitas database gamemode.",
        "db_log_queries" to "Mencatat teks query SQL, bukan hanya waktunya.",
        "onfoot_rate" to "Interval sinkronisasi pemain berjalan kaki (ms).",
        "incar_rate" to "Interval sinkronisasi pemain di dalam kendaraan (ms).",
        "maxplayerperip" to "Batas koneksi dari satu alamat IP.",
        "stream_rate" to "Interval pembaruan object streaming (ms).",
        "stream_distance" to "Jarak munculnya object (streaming).",
        "sleep" to "Jeda loop utama server dalam milidetik.",
        "connseedtime" to "Interval pembaruan seed tabel cookie koneksi.",
        "conncookies" to "Mewajibkan handshake cookie koneksi (anti-spoofing).",
        "cookielogging" to "Mencatat lalu lintas cookie koneksi.",
        "useartwork" to "Mengaktifkan unduhan artwork khusus (perlu `artpath`/`artwork`).",
        "artpath" to "Folder yang menyajikan arsip artwork.",
        "artwork" to "Arsip artwork yang diunduh client.",
        "version" to "Penanda versi yang ditulis server.",
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

    fun isGamemode(word: String): Boolean = word.startsWith("gamemode") && isDirective(word)

    fun descriptionFor(word: String, language: CompilerConfig.AppLanguage): String {
        if (isGamemode(word)) return gamemodeDescription(word, language)
        val english = DESCRIPTIONS[word] ?: return ""
        return if (language == CompilerConfig.AppLanguage.ID) INDONESIAN[word] ?: english else english
    }

    private fun gamemodeDescription(word: String, language: CompilerConfig.AppLanguage): String {
        val index = word.removePrefix("gamemode").toIntOrNull()
        val after = if (index == 0) {
            "the first gamemode"
        } else {
            "another gamemode instance"
        }
        val english = "Script run by slot $index; repeated lines start $after. " +
            "Usage: $word <name> [repeat count]."
        if (language != CompilerConfig.AppLanguage.ID) return english
        return "Skrip untuk slot $index; baris berulang memulai $after. " +
            "Pemakaian: $word <nama> [jumlah pengulangan]."
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

    /** Keys `key = value` or `key value`; whichever separator precedes [column]. */
    fun directiveAt(line: CharSequence, column: Int): String? {
        if (column !in 0..line.length) return null
        val before = line.subSequence(0, column)
        val boundary = maxOf(
            before.lastIndexOf('='),
            before.lastIndexOf(' '),
            before.lastIndexOf('\t')
        )
        if (boundary < 0) return null
        val leading = before.take(boundary).trim()
        if (leading.isEmpty() || leading.any { it == '=' || it == ' ' || it == '\t' }) return null
        return leading.takeIf { isDirective(it) }
    }
}
