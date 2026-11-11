plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// PocketSAMP, adopted from thirdparty/PocketSAMP as an embedded runtime module of
// PawnMC. It is a *library*, not an application: the UI is opened from the PawnMC
// main interface (the old self-test header button now launches it), and the engine
// (foreground service, QEMU/SA-MP, native ARM64 MariaDB) runs inside PawnMC's process.
// The upstream `com.pocketsamp.server` namespace is deliberately untouched — the
// project is kept under its own name the same way the Xed editor identity is kept.
android {
    namespace = "com.pocketsamp.server"
    compileSdk = 36

    defaultConfig {
        // The legacy PawnMC flavor consumes the API-24-compatible fallback path;
        // modern builds still include the full runtime on API 26+.
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("tar", "gz")
    }

    sourceSets {
        getByName("main") {
            // Host-side native helpers such as QEMU are staged here so Android extracts
            // them to nativeLibraryDir. MariaDB and its libraries are installed from
            // the separate runtime archive instead of being packaged into the APK.
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Reuse PawnMC's version catalog and exact versions so this embedded module
    // does not pull in a second Compose/lifecycle/coroutines stack.
    implementation(platform(rootProject.libs.androidx.compose.bom))
    implementation(rootProject.libs.androidx.core.ktx)
    implementation(rootProject.libs.androidx.lifecycle.runtime.ktx)
    implementation(rootProject.libs.androidx.lifecycle.viewmodel.compose)
    implementation(rootProject.libs.androidx.activity.compose)

    implementation(rootProject.libs.androidx.ui)
    implementation(rootProject.libs.androidx.ui.graphics)
    implementation(rootProject.libs.androidx.material3)
    // This library shares the host app's Material icon artifacts and version.
    // Only explicitly referenced icon ImageVectors are packaged after R8 shrinking.
    implementation(rootProject.libs.androidx.material.icons.extended)

    implementation(rootProject.libs.kotlinx.coroutines.android)
    implementation(rootProject.libs.mariadb.java.client)
    implementation(rootProject.libs.commons.compress)
    // Archive extraction beyond commons-compress: junrar reads RAR4 archives in
    // pure Java; SevenZipJBinding adds the 7-Zip engine for 7z and modern RAR5
    // (native per-ABI, unpacked from the -all-platforms jar).
    implementation(rootProject.libs.junrar)
    implementation(rootProject.libs.sevenzipjbinding)
    implementation(rootProject.libs.sevenzipjbinding.allplatforms)

    testImplementation(rootProject.libs.junit)
}
