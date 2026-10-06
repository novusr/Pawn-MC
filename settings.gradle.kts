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

// Each module lives at `<name>/src/main`, so Gradle's default project dir already
// matches the include path and no `projectDir` override is needed.

