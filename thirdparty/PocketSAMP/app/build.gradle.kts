plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// PocketSAMP, adopted from thirdparty/PocketSAMP as an embedded runtime module of
// PawnMC. It is a *library*, not an application: the UI is opened from the PawnMC
// main interface (the old self-test header button now launches it), and the engine
// (foreground service, QEMU/SA-MP, MariaDB) runs inside the single PawnMC process.
// The upstream `com.pocketsamp.server` namespace is deliberately untouched — the
// project is kept under its own name the same way the Xed editor identity is kept.
android {
    namespace = "com.pocketsamp.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("proguard-rules.pro")
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
            // The translator and PRoot are ordinary executables renamed to lib*.so so
            // Android extracts them to nativeLibraryDir. Keeping them under
            // jniLibs/arm64-v8a is what makes that directory exist: PackageManager
            // only creates lib/<abi>/ for an ABI the APK declares.
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
    /*
     * extended, not core: the tabs and screens use BugReport, Storage, Terminal,
     * Timer, ContentCopy, OpenInBrowser, PlayArrow, Stop, Refresh and CheckCircle.
     * Only ArrowBack, Dashboard and Add are in material-icons-core, so switching to
     * core breaks the build.
     */
    implementation(rootProject.libs.androidx.material.icons.extended)

    implementation(rootProject.libs.kotlinx.coroutines.android)
    implementation(rootProject.libs.mariadb.java.client)
    implementation(rootProject.libs.commons.compress)
}
