// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP is declared once here so `com.android.library` resolves from the build
    // classpath in the `:terminal:*` modules instead of being requested with a
    // version of its own. Requesting a version for a plugin that is already on the
    // classpath is what produces "the plugin is already on the classpath with an
    // unknown version, so compatibility cannot be checked".
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
