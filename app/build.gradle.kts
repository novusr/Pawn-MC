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
        // Every app source lives under `app/src/source/`: the shared `main-src` tree and
        // the per-flavor trees beside it, all named with the `-src` suffix so no folder
        // carries the bare `src` name the Gradle source set already implies.
        getByName("main") {
            // The manifest is not covered by `java`/`res`/`assets`: AGP still reads
            // `src/main/AndroidManifest.xml` unless the source set is redirected, so
            // it has to point at the moved tree explicitly.
            manifest.srcFile("src/source/main-src/AndroidManifest.xml")
            java.srcDir("src/source/main-src/java")
            res.srcDir("src/source/main-src/res")
            // `native/data/_data_2026_ect.toml` is the single source of localisation data
            // and ships as an asset, so nothing else in the app duplicates it.
            assets.srcDir(rootProject.file("native/data"))
        }
        create("nougat") {
            java.srcDir("src/source/nougat-src/java")
            res.srcDir("src/source/nougat-src/res")
        }
        create("newer") {
            java.srcDir("src/source/newer-src/java")
            res.srcDir("src/source/newer-src/res")
        }
        getByName("test") {
            java.srcDir(rootProject.file("native/test/app/unit"))
        }
        getByName("androidTest") {
            java.srcDir(rootProject.file("native/test/app/instrumented"))
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
        versionName = "1.5.3"


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
            path = file("../native/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ndkVersion = "29.0.14206865"

    packaging {
        // JNA ships its licence files (AL2.0, LGPL2.1) in both jna and jna-platform,
        // and AGP refuses to merge two copies of the same resource path. The
        // duplicate is exactly the same licence text, so picking the first input is
        // the correct merge: the licence still ships once inside the APK.
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")

        // No `jniLibs` filter here on purpose.
        //
        // `JniLibsPackaging` only offers `excludes`, `pickFirsts`, `keepDebugSymbols`,
        // `testOnly` and `useLegacyPackaging`; there is no "keep" rule.
        //
        // APK layout, and why (`libm.so` is the one deliberate exception):
        //
        //   assets/<abi>.zip                libpawnc3107.so + libpawnc31011.so
        //                                   (+ libandroidx.graphics.path.so)
        //   lib/<abi>/libm.so               JNI bridge only
        //
        // The engine libraries must NOT be in `lib/`. CMake declares `pawnc3107` and
        // `pawnc31011` as SHARED targets, so AGP otherwise packages them into
        // `lib/<abi>/` in addition to the archive — that is the duplicate this exclusion
        // removes. The archive still gets them: CI zips them from
        // `intermediates/cxx/*/obj/<abi>/`, which these filters do not touch.
        //
        // `libm.so` is deliberately not excluded: `System.loadLibrary` resolves only
        // through the APK's `lib/<abi>/` folder, and the Android linker refuses to
        // `dlopen` a path in app storage. It is the bridge that loads everything else,
        // so it has to be the one library the loader can reach.
        jniLibs {
            excludes += listOf(
                "**/lib/*/libpawnc3107.so",
                "**/lib/*/libpawnc31011.so"
            )
        }
        //
        // `useLegacyPackaging` defaults to false, i.e. `libm.so` is mapped straight out
        // of the APK rather than extracted, which is what lets it load without a copy.
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
    // own `com.pocketsamp.server.ServerApp` stub (src/source/nougat-src), so depending on the
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
// Third-party native library into the per-ABI archive
//
// `assets/<abi>.zip` is produced by CI (`ci.yml`) from the CMake output, because the
// Pawn compiler libraries only exist as build artifacts:
//
//   assets/<abi>.zip
//       libpawnc3107.so      Pawn compiler 3.10.7 engine
//       libpawnc31011.so     Pawn compiler 3.10.11 engine
//
// `libandroidx.graphics.path.so` cannot be packed there: it comes from the Compose
// dependency, not from CMake, so it is only known after AGP has merged native libs.
// This task adds it to each archive before packaging, whenever the archive and the
// library are both present. A local build that skipped the CI step has no archives and
// this task does nothing.
// ---------------------------------------------------------------------------------
val thirdPartyArchivedLibrary = "libandroidx.graphics.path.so"

/**
 * Appends [thirdPartyArchivedLibrary] to every `assets/<abi>.zip` in the variant's
 * merged assets, skipping archives that already contain it.
 */
abstract class AddLibraryToAbiArchivesTask : DefaultTask() {

    /** Directory holding the merged `assets/` tree for this variant. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetsDirectory: DirectoryProperty

    /** Merged native library roots; `libandroidx.graphics.path.so` is looked up here. */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeLibraries: ConfigurableFileCollection

    @get:Input
    abstract val libraryName: Property<String>

    @TaskAction
    fun run() {
        val assets = assetsDirectory.orNull?.asFile ?: return
        if (!assets.isDirectory) return

        val library = nativeLibraries.files
            .firstOrNull { it.name == libraryName.get() && it.isFile }
        if (library == null) {
            logger.lifecycle("[native-archive] ${libraryName.get()} not produced; archives left as-is")
            return
        }

        val archives = assets.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
        if (archives.isEmpty()) {
            logger.lifecycle("[native-archive] no per-ABI archives in ${assets.absolutePath}")
            return
        }

        val payload = library.readBytes()
        for (archive in archives) {
            val existing = ZipFile(archive).use { zip ->
                zip.entries().asSequence().map { entry -> entry.name }.toSet()
            }
            if (libraryName.get() in existing) {
                logger.lifecycle("[native-archive] ${archive.name} already contains ${library.name}")
                continue
            }

            val entries = ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .map { entry ->
                        entry.name to zip.getInputStream(entry).use { stream -> stream.readBytes() }
                    }
                    .toList()
            }

            // Explicitly typed: `entries + (name to bytes)` yields an ambiguous element
            // type, which made the destructuring `forEach` fail to resolve.
            val merged: List<Pair<String, ByteArray>> = entries + listOf(libraryName.get() to payload)
            ZipOutputStream(archive.outputStream().buffered()).use { out ->
                for ((name, bytes) in merged) {
                    out.putNextEntry(ZipEntry(name).apply { time = 0L })
                    out.write(bytes)
                    out.closeEntry()
                }
            }
            logger.lifecycle("[native-archive] ${archive.name} <- ${library.name}")
        }
    }
}

androidComponents.onVariants { variant ->
    val assetsTaskName = "merge${variant.name.replaceFirstChar { it.uppercase() }}Assets"
    val assetsTask = tasks.matching { it.name == assetsTaskName }.firstOrNull() ?: return@onVariants

    val addTask = tasks.register<AddLibraryToAbiArchivesTask>(
        "addThirdPartyLibrary${variant.name.replaceFirstChar { it.uppercase()}}"
    ) {
        group = "build"
        description = "Adds ${thirdPartyArchivedLibrary} to the per-ABI native archives."
        this.libraryName.set(thirdPartyArchivedLibrary)
        this.assetsDirectory.set(
            layout.buildDirectory.dir("intermediates/assets/${variant.name}/merge${variant.name.replaceFirstChar { it.uppercase() }}Assets")
        )
        this.nativeLibraries.from(
            layout.buildDirectory.dir("intermediates/merged_native_libs/${variant.name}")
        )
        outputs.upToDateWhen { false }
    }

    assetsTask.finalizedBy(addTask)
}
