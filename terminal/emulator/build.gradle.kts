// Termux terminal emulator, vendored from the Xed editor so `libtermux.so` is built
// in-tree with NDK 28.2 (16 KB page aligned) instead of being pulled as a prebuilt
// AAR whose library was only 4 KB aligned and is rejected on Android 15+.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.terminal"
    compileSdk = 36
    ndkVersion = libs.versions.ndkTerminal.get()

    defaultConfig {
        minSdk = 24

        externalNativeBuild {
            ndkBuild {
                cFlags +=
                    listOf(
                        "-std=c11",
                        "-Wall",
                        "-Wextra",
                        "-Werror",
                        "-Os",
                        "-fno-stack-protector",
                        // Gradle installs libtermux.so into nativeLibraryDir, so it is
                        // always page aligned; keep the pad segment anyway for unpacked
                        // installs of the same APK.
                        "-Wl,--gc-sections",
                    )
            }
        }
    }

    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
