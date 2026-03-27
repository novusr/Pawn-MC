import java.util.Properties
import java.util.regex.Pattern

fun extractPawncVersionLabel(cmakePath: String, defaultBase: String): String {
    val file = rootProject.file(cmakePath)
    var patchVersion = "?"
    if (file.exists()) {
        val content = file.readText()
        val patchMatcher = Pattern.compile("set\\s*\\(\\s*PAWNMC_PATCH\\s+([0-9]+)\\s*\\)").matcher(content)
        if (patchMatcher.find()) {
            patchVersion = patchMatcher.group(1)
        }
    }
    return "Pawn ${defaultBase}-pawnmc.${patchVersion}"
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    }

    defaultConfig {
        applicationId = "com.rvdjv.pawnmc"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"

        val label3107 = extractPawncVersionLabel("compilers/pawnc-3.10.7/source/compiler/CMakeLists.txt", "3.10.7")
        val label31011 = extractPawncVersionLabel("compilers/pawnc-3.10.11/source/compiler/CMakeLists.txt", "3.10.11")
        buildConfigField("String", "PAWNC_3107_LABEL", "\"$label3107\"")
        buildConfigField("String", "PAWNC_31011_LABEL", "\"$label31011\"")

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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}