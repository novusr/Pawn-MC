package com.pocketsamp.server.configuration

/**
 * Every `server.cfg` parameter this runner understands, for the read-only
 * configuration information screen.
 *
 * The value shapes mirror the hints the syntax/completion layer uses, so what
 * the info screen documents is exactly what the editor autocompletes. The raw
 * `name value` lines the user wrote travel separately via [Reader.readRawValues].
 */
object ConfigParameters {

    /** One documented parameter: its key and the accepted value shape. */
    data class Info(val key: String, val accepted: String)

    val INFO: List<Info> = listOf(
        Info("announce", "0|1"),
        Info("maxplayers", "0..1000"),
        Info("port", "1..65535"),
        Info("bind", "0.0.0.0"),
        Info("lanmode", "0|1"),
        Info("query", "0|1"),
        Info("logqueries", "0|1"),
        Info("rcon_port", "1..65535"),
        Info("rcon_maxadmins", "1..10"),
        Info("rcon_bind", "IP address"),
        Info("output", "0|1"),
        Info("mtu", "576..1492"),
        Info("timestamp", "0|1"),
        Info("logtimeformat", "[%H:%M:%S]"),
        Info("password", "\"password\""),
        Info("hostname", "\"SA:MP Server\""),
        Info("mapname", "\"San Andreas\""),
        Info("language", "\"English\""),
        Info("weburl", "\"\""),
        Info("rcon_password", "\"password\""),
        Info("gravity", "0.008"),
        Info("weather", "0..20"),
        Info("tirepopping", "0|1"),
        Info("gamemodetext", "\"Unknown\""),
        Info("filterscripts", "name name ..."),
        Info("plugins", "name.so name.so ..."),
        Info("nosign", "0|1"),
        Info("anticheat", "0|1"),
        Info("instagib", "0|1"),
        Info("myriad", "0|1"),
        Info("chatlogging", "0|1"),
        Info("messageholelimit", "1000..100000"),
        Info("playertimeout", "milliseconds"),
        Info("db_logging", "0|1"),
        Info("db_log_queries", "0|1"),
        Info("onfoot_rate", "milliseconds"),
        Info("incar_rate", "milliseconds"),
        Info("maxplayerperip", "0.."),
        Info("stream_rate", "milliseconds"),
        Info("stream_distance", "0..1000"),
        Info("sleep", "milliseconds"),
        Info("connseedtime", "milliseconds"),
        Info("conncookies", "0|1"),
        Info("cookielogging", "0|1"),
        Info("useartwork", "0|1"),
        Info("artpath", "path"),
        Info("artwork", "archive.zip"),
        Info("version", "\"0.3.7-R2\""),
    )
}
