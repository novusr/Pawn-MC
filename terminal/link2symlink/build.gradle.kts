// The LD_PRELOAD shim PRoot falls back to when Samsung kernels refuse to extract the
// rootfs under ptrace.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pawnmc.link2symlink"
    compileSdk = 36
    ndkVersion = libs.versions.ndkTerminal.get()

    defaultConfig {
        minSdk = 24
        externalNativeBuild { cmake { cppFlags += "" } }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies { implementation(libs.androidx.annotation) }
