import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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
        create("nougat") {
            java.srcDir("src/nougat-src/java")
            res.srcDir("src/nougat-src/res")
        }
        create("newer") {
            java.srcDir("src/newer-src/java")
            res.srcDir("src/newer-src/res")
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
        create("nougat") {
            dimension = "androidApi"
            // Preserve the established package suffix for in-place updates.
            minSdk = 24
            targetSdk = 36
            // Kept stable so Android can update installed Nougat packages in place.
            applicationIdSuffix = ".legacy"
            versionNameSuffix = "-nougat"
        }
        create("newer") {
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
        versionName = "1.5.2"


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // ABIs the compiler bridge is built for. Kept on `ndk` (not on the
            // packaging filter) so the CMake build still produces every target; the
            // packaging filter below then decides what reaches `lib/`.
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
            // CI release artifacts remain installable without a private signing secret.
            signingConfig = signingConfigs.getByName("debug")
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

        // No `jniLibs` filter here on purpose.
        //
        // `JniLibsPackaging` only offers `excludes`, `pickFirsts`, `keepDebugSymbols`,
        // `testOnly` and `useLegacyPackaging` — there is no "keep" rule, and the merge
        // filters run before the native libraries are built, so an exclusion here would
        // also remove the files the per-ABI archive task needs (it would find nothing to
        // pack). The APK layout is produced after the merge instead, by
        // `ArchiveAbiLibrariesTask` below: it turns `lib/<abi>/*.so` into
        // `lib/<abi>/<abi>.zip` and leaves only `libm.so` loose.
        //
        // `useLegacyPackaging` defaults to false, i.e. the extracted `.so` files are
        // mapped straight out of the APK, which is what lets the bridge be loaded without
        // being copied anywhere.
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
    // Extended icons are shared with PocketSAMP's UI through the same Compose
    // version catalog; R8 removes every generated ImageVector not referenced here
    // or by the embedded runtime.
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.sora.editor)

    // PocketSAMP is compiled into the modern flavor only. The Nougat flavor provides its
    // own `com.pocketsamp.server.ServerApp` stub (src/nougat-src), so depending on the
    // module there as well would define that class twice and break R8.
    "newerImplementation"(project(":pocketsamp"))

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
// ---------------------------------------------------------------------------------
// Per-ABI native archive
//
// `lib/<abi>/` carries a single `<abi>.zip` holding the libraries the app unpacks into
// `Documents/PawnMC/library/<abi>/` on first launch:
//
//   lib/<abi>/<abi>.zip
//       libpawnc3107.so      Pawn compiler 3.10.7 engine (unpacked, not loaded from the APK)
//       libpawnc31011.so     Pawn compiler 3.10.11 engine
//       libandroidx.graphics.path.so   Compose's path library
//
// `libm.so` (the PawnMC JNI bridge) is the only library that stays loose in the APK: it
// has to be loadable before anything is unpacked, and Android's linker will not open a
// file from app storage that the package manager did not install.
//
// The task runs after AGP linked and stripped the libraries but before the APK is
// packaged, and it rewrites the staging `lib/<abi>/` folder, which the `packaging`
// filters have already narrowed down to `lib/*/libm.so`.
// ---------------------------------------------------------------------------------
val bridgeLibraryName = "libm.so"

val unpackedLibraryNames = listOf(
    "libpawnc3107.so",
    "libpawnc31011.so",
    "libandroidx.graphics.path.so"
)



/**
 * Packs one ABI's native libraries into `lib/<abi>/<abi>.zip`.
 *
 * The source is the `lib/<abi>` staging folder AGP merges into the APK, i.e. the
 * libraries that were linked and stripped for that ABI. The loose `.so` files are then
 * removed from the folder, so the packaged `lib/<abi>/` contains the archive (and the
 * bridge, which the `pickFirsts` rule in `packaging` keeps) and nothing else.
 */
abstract class ArchiveAbiLibrariesTask : DefaultTask() {

    /** `intermediates/stripped_native_libs/<variant>/<abi>`. */
    @get:Internal
    abstract val libraryDirectory: DirectoryProperty

    @get:OutputFile
    abstract val archive: RegularFileProperty

    @get:Input
    abstract val abi: Property<String>

    @get:Input
    abstract val libraryNames: ListProperty<String>

    @TaskAction
    fun run() {
        val abiName = abi.get()
        val folder = libraryDirectory.get().asFile
        val target = archive.get().asFile
        // The bridge has to survive the packaging filter: without it the JNI surface
        // would exist only inside the archive, and nothing could unpack it.
        require(File(folder, bridgeLibraryName).isFile) {
            "[native-archive] $abiName: $bridgeLibraryName is missing from " +
                "${folder.absolutePath}; the packaging filter dropped the bridge"
        }
        val present = libraryNames.get().map { File(folder, it) }.filter { it.isFile }

        if (present.isEmpty()) {
            error("[native-archive] $abiName: no native libraries in ${folder.absolutePath}")
        }

        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            for (file in present) {
                out.putNextEntry(ZipEntry(file.name).apply { time = 0L })
                file.inputStream().buffered().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        logger.lifecycle("[native-archive] $abiName: ${target.name} <- ${present.joinToString { it.name }}")

        // Only the archive is packaged for this ABI; the loose libraries it now holds are
        // removed from the staging folder so they cannot be merged into `lib/<abi>/` too.
        folder.listFiles()
            ?.filter { it.extension == "so" && it.name in libraryNames.get() }
            ?.forEach { it.delete() }
        val missing = libraryNames.get().filterNot { File(folder, it).isFile }
        if (missing.isNotEmpty()) {
            logger.lifecycle("[native-archive] $abiName: never produced: ${missing.joinToString()}")
        }
    }
}

/**
 * One archive task per ABI and variant, wired in front of the APK packaging step.
 *
 * `PackageApplication` is hooked through `onVariants`: it is the task that consumes
 * `lib/<abi>/`, so making the archive a dependency of it guarantees the zip exists (and
 * the loose libraries are gone) by the time `lib/**` is read.
 */
androidComponents.onVariants { variant ->
    val packagingTaskName = "package${variant.name.replaceFirstChar { it.uppercase() }}"
    val packagingTask = tasks.named<com.android.build.gradle.tasks.PackageApplication>(packagingTaskName)

    packagingTask.configure {
        val staging = layout.buildDirectory.dir(
            "intermediates/stripped_native_libs/${variant.name}"
        ).get().asFile
        val variantLabel = variant.name.replaceFirstChar { it.uppercase() }

        android.defaultConfig.ndk.abiFilters.forEach { abiName ->
            val archiveDirectory = File(staging, abiName)
            val archiveTask = tasks.register<ArchiveAbiLibrariesTask>(
                "archiveLibraries$variantLabel" + abiName.split('-', '_').joinToString("") { part ->
                    part.replaceFirstChar { it.uppercase() }
                }
            ) {
                group = "build"
                description = "Packs the $abiName libraries into lib/$abiName/$abiName.zip."
                this.abi.set(abiName)
                this.libraryDirectory.set(archiveDirectory)
                this.libraryNames.set(unpackedLibraryNames)
                this.archive.set(File(archiveDirectory, "$abiName.zip"))
            }
            dependsOn(archiveTask)
        }
    }
}
