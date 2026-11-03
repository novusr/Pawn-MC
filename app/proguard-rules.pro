# PawnMC ProGuard Rules

# Keep all JNI native methods in Runner
# `Runner` lives under `com.rvdjv.pawnmc.apps-data`, a hyphenated package segment that
# Kotlin escapes as `apps-data` in source, so the plain name is used here.
-keep class com.rvdjv.pawnmc.apps-data.data_compiler.Runner {
    private native <methods>;
}

# Keep Activities (referenced in AndroidManifest.xml)
# `interface` is a Kotlin keyword and the package now carries a hyphen, so source files
# escape it as `user-interface`; ProGuard needs the plain name here.
-keep class com.rvdjv.pawnmc.user-interface.main.MainActivity
-keep class com.rvdjv.pawnmc.user-interface.settings.SettingsActivity

# Loaded by generated Kotlin file-class name from PocketSampGateway.
-keep class com.pocketsamp.server.PocketSampRuntimeMarkerKt { *; }

# Keep CompilerConfig enums (used for SharedPreferences serialization)
-keep class com.rvdjv.pawnmc.apps-data.data_config.CompilerConfig$CompilerVersion { *; }
-keep class com.rvdjv.pawnmc.apps-data.data_config.CompilerConfig$DebugLevel { *; }

# Material Icons Extended generates one ImageVector getter per icon. Do not add
# package-wide keep rules: unreachable generated getters must remain shrinkable.

# MariaDB JDBC is loaded by name (Class.forName) and self-registers with
# DriverManager from its static initialiser. R8 would strip or rename the whole
# driver otherwise, leaving the database screen dead in a release build.
-keep class org.mariadb.jdbc.Driver { *; }

# The driver resolves codecs, plugins and authentication handlers by class name at
# runtime, so those names must stay stable. Only names are kept: bodies, fields and
# other members remain shrinkable and the unused ones disappear.
-keepnames class org.mariadb.jdbc.**

# MariaDB JDBC (pocketsamp) references platform APIs Android does not ship. They are
# only reached by features this app never uses: JMX pool monitoring, LDAP hostname
# verification, GSSAPI/Kerberos and AWS IAM authentication, XA transactions. Suppress
# the absence instead of pulling those libraries in, which would be far larger than
# the driver itself.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.sql.SQLType
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.login.**
-dontwarn javax.sql.XAConnection
-dontwarn org.ietf.jgss.**
-dontwarn software.amazon.awssdk.**

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile