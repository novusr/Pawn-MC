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
    }

    defaultConfig {
        applicationId = "com.rvdjv.pawnmc"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.5.1"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // abiFilters += listOf("arm64-v8a", "armeabi-v7a")
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

    // The sandbox runs `libproot.so`, `libloader.so` and `libtermux.so` straight out of
    // `nativeLibraryDir`, which Android requires them to live in to be execable.
    // Without legacy packaging AGP leaves the libraries compressed inside the APK and
    // maps them from there, so that directory stays empty and every exec fails with
    // "inaccessible or not found". This is deliberately declared on the application
    // module only: the `:terminal:*` libraries must not set it, because a library that
    // requests legacy packaging changes how its own libraries are packaged and Gradle
    // then reports a conflict between modules.
    packaging { jniLibs { useLegacyPackaging = true } }
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

    // The in-editor terminal: proot sandbox + Termux VT emulator, opened from the
    // floating Xed editor button.
    implementation(project(":terminal:app"))

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}