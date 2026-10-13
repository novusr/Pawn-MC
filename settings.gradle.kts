pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PawnMC"
include(":app")


// Terminal feature, ported from the Xed editor: the proot sandbox plus the Termux
// terminal emulator/view. They live outside `app` because the native code needs a
// different NDK (28.2, 16 KB page aligned) than the compiler module uses.
include(":terminal:emulator")
include(":terminal:view")
include(":terminal:proot")
include(":terminal:link2symlink")
include(":terminal:app")

// PocketSAMP, vendored under `thirdparty/PocketSAMP` and adopted as an embedded runtime
// module of PawnMC (opened from the main interface like the editor, not a separate app).
// The upstream package/namespace `com.pocketsamp.server` is deliberately kept: honouring
// the upstream project by retaining its name, the same way the Xed editor identity is
// retained in this codebase.
include(":pocketsamp")

// Each module lives at `<name>/src/main`, so Gradle's default project dir already
// matches the include path and no `projectDir` override is needed — except PocketSAMP,
// which was cloned as a full upstream Gradle project and is included from its own `app`
// subproject directory instead of being re-arranged.
include(":pocketsamp")
project(":pocketsamp").projectDir = file("thirdparty/PocketSAMP/app")

