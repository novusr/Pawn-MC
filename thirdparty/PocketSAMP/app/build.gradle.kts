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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    // The MariaDB JDBC driver ships as a vendored jar in this module (same file the
    // upstream build referenced); keep both paths pointing at it.
    implementation(files("libs/mariadb-java-client-3.3.3.jar"))
    implementation(libs.mariadb.java.client)
    implementation(libs.commons.compress)
}
