# PawnMC ProGuard Rules

# Keep all JNI native methods in Runner
-keep class com.rvdjv.pawnmc.data.compiler.Runner {
    private native <methods>;
}

# Keep Activities (referenced in AndroidManifest.xml)
# `interface` is a Kotlin keyword, so the package is escaped as `interface` in
# source files; ProGuard needs the plain name here.
-keep class com.rvdjv.pawnmc.interface.main.MainActivity
-keep class com.rvdjv.pawnmc.interface.settings.SettingsActivity

# Keep CompilerConfig enums (used for SharedPreferences serialization)
-keep class com.rvdjv.pawnmc.data.config.CompilerConfig$CompilerVersion { *; }
-keep class com.rvdjv.pawnmc.data.config.CompilerConfig$DebugLevel { *; }

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile