import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.rvdjv.pawnmc"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("release-key.jks")
            val storePass = localProperties.getProperty("RELEASE_STORE_PASSWORD")
            val keyPass = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            
            if (keystoreFile.exists() && !storePass.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = "novusr"
                keyPassword = keyPass
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            // `data/_data_2026_ect.toml` is the single source of localisation data
            // and ships as an asset, so nothing else in the app duplicates it.
            assets.srcDir(rootProject.file("data"))
        }
        create("legacy") {
            java.srcDir("src/legacy/java")
        }
        create("modern") {
            java.srcDir("src/modern/java")
        }
        getByName("test") {
            java.srcDir(rootProject.file("test/app/unit"))
        }
        getByName("androidTest") {
            java.srcDir(rootProject.file("test/app/instrumented"))
        }
    }

    flavorDimensions += "androidApi"

    productFlavors {
        create("legacy") {
            dimension = "androidApi"
            // Android 7.0/API 24 baseline. Older devices remain supported via minSdk;
            // both variants target current Android policy requirements.
            minSdk = 24
            targetSdk = 36
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-legacy"
        }
        create("modern") {
            dimension = "androidApi"
            minSdk = 26
            targetSdk = 36
        }
    }

    defaultConfig {
        applicationId = "com.rvdjv.pawnmc"
        // Both flavors target current Android while minSdk controls older-device support.
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.1"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Pawn compiler native libraries support these ABIs. PocketSAMP's
            // translator assets currently ship only for arm64 and stay flavor-scoped.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        release {
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null) {
                signingConfig = releaseSigningConfig
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "29.0.14206865"

    packaging {
        // JNA ships its licence files (AL2.0, LGPL2.1) in both jna and jna-platform,
        // and AGP refuses to merge two copies of the same resource path. The
        // duplicate is exactly the same licence text, so picking the first input is
        // the correct merge — the licence still ships once inside the APK.
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }

    val appApkNamePrefix = "pawnmc"

    afterEvaluate {
    tasks.matching { task ->
        task.name.startsWith("assemble") || task.name.startsWith("package")
    }.configureEach {
        doLast {
            val variantName = when {
                name.contains("Debug", ignoreCase = true) -> "debug"
                name.contains("Release", ignoreCase = true) -> "release"
                else -> "debug"
            }
            val versionName = android.defaultConfig.versionName
            val targetName = "$appApkNamePrefix-$versionName-$variantName.apk"
            val outputsDir = File(project.layout.buildDirectory.asFile.get(), "outputs/apk")

            if (!outputsDir.exists()) return@doLast

            outputsDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .forEach { apkFile ->
                    val desiredFile = File(apkFile.parentFile, targetName)
                    if (apkFile.absolutePath != desiredFile.absolutePath && !desiredFile.exists()) {
                        apkFile.renameTo(desiredFile)
                    }
                }
        }
    }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.sora.editor)

    // PocketSAMP is compiled as part of both flavors. Legacy retains its public
    // runtime entry point and assets, but uses the API-24-compatible fallback behavior.
    implementation(project(":pocketsamp"))

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}