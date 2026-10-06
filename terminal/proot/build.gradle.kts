// PRoot: the sandbox that lets the bundled Ubuntu rootfs run unprivileged.
//
// It is built as a SHARED library on purpose. Android's W^X rule means the app may only
// exec() files inside nativeLibraryDir, and Gradle only puts `lib*.so` there, so the
// loader is packaged as `libloader.so` and proot itself keeps the name `libproot.so`
// (without the `lib` prefix it would be moved to jniLibs root and lose exec permission).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pawnmc.proot"
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
