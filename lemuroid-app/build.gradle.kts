import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("Arcadune")
}

val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
val hasReleaseKey = keystoreProperties.containsKey("storeFile")
val raHardcoreApproved =
    project.findProperty("raHardcoreApproved")
        ?.toString()
        ?.toBooleanStrictOrNull()
        ?: false
val raServerHost =
    project.findProperty("raServerHost")
        ?.toString()
        ?.trim()
        .orEmpty()

android {
    ndkVersion = "26.3.11579264"
    defaultConfig {
        versionCode = 263
        versionName = "1.1.1"
        applicationId = "io.github.shahrukhmaniyar.arcadune"
        buildConfigField("Boolean", "RA_HARDCORE_APPROVED", raHardcoreApproved.toString())
        buildConfigField("String", "RA_SERVER_HOST", "\"${raServerHost.replace("\"", "")}\"")

        // Only ARM phones: the cores Arcadune downloads exist only for these, and leaving out the
        // x86 emulator/Chromebook copies keeps both APKs much smaller.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        // English only for now. The other languages in res/values-* translate only about a third of
        // the app (Lemuroid's old text), so a phone in German showed half German, half English. Add a
        // language back here once its strings.xml is complete (and turn MissingTranslation back on).
        resourceConfigurations += listOf("en")
    }
    flavorDimensions += listOf("opensource", "cores")

    if (usePlayDynamicFeatures()) {
        println("Building Google Play version. Bundling dynamic features.")
        dynamicFeatures.addAll(
            setOf(
                ":lemuroid_core_dosbox_pure",
                ":lemuroid_core_fbneo",
                ":lemuroid_core_fceumm",
                ":lemuroid_core_gambatte",
                ":lemuroid_core_genesis_plus_gx",
                ":lemuroid_core_handy",
                ":lemuroid_core_mame2003_plus",
                ":lemuroid_core_mednafen_ngp",
                ":lemuroid_core_mednafen_pce_fast",
                ":lemuroid_core_mednafen_wswan",
                ":lemuroid_core_melonds",
                ":lemuroid_core_mgba",
                ":lemuroid_core_mupen64plus_next_gles3",
                ":lemuroid_core_pcsx_rearmed",
                ":lemuroid_core_ppsspp",
                ":lemuroid_core_prosystem",
                ":lemuroid_core_snes9x",
                ":lemuroid_core_stella",
                ":lemuroid_core_citra",
                ":lemuroid_core_flycast",
                ":lemuroid_core_opera",
                ":lemuroid_core_yabasanshiro",
                ":lemuroid_core_picodrive",
                ":lemuroid_core_a5200",
                ":lemuroid_core_atari800",
                ":lemuroid_core_gearcoleco",
                ":lemuroid_core_vice_x64",
                ":lemuroid_core_applewin",
                ":lemuroid_core_fmsx",
            ),
        )
    }

    // Since some dependencies are closed source we make a completely free as in free speech variant.

    productFlavors {

        create("free") {
            dimension = "opensource"
        }

        create("play") {
            dimension = "opensource"
        }

        // Include cores in the final apk
        create("bundle") {
            dimension = "cores"
        }

        // Download cores on demand (from GooglePlay or GitHub)
        create("dynamic") {
            dimension = "cores"
        }
    }

    packagingOptions {
        jniLibs {
            // Stripping created some issues with some libretro cores such as ppsspp
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
            // DeSmuME is an on-demand downloadable alternate DS core (melonDS is the bundled default).
            excludes += setOf("**/libdesmume_libretro_android.so")
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module")
        }
    }

    signingConfigs {
        maybeCreate("debug").apply {
            storeFile = file("$rootDir/debug.keystore")
        }

        // The release key lives outside the repository. See "Signing a release" in the README.
        if (hasReleaseKey) {
            maybeCreate("release").apply {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                keyAlias = keystoreProperties.getProperty("keyAlias")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            // Without keystore.properties, release builds are signed with the debug key.
            signingConfig = signingConfigs[if (hasReleaseKey) "release" else "debug"]
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            resValue("string", "lemuroid_name", "Arcadune")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "lemuroid_name", "Arcadune Debug")
        }
    }

    lint {
        // MissingTranslation stays off while only English ships (see resourceConfigurations).
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
        // Lint must pass for a release: known, reviewed warnings live in lint-baseline.xml.
        baseline = file("lint-baseline.xml")
        abortOnError = true
        checkReleaseBuilds = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = deps.versions.kotlinExtension
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid"

    applicationVariants.all {
        val variantName = name
        outputs.all {
            val outputImpl = this as? BaseVariantOutputImpl
            outputImpl?.outputFileName =
                when (variantName) {
                    "freeBundleRelease" -> "Arcadune-full.apk"
                    "freeDynamicRelease" -> "Arcadune.apk"
                    else -> "Arcadune-$variantName.apk"
                }
        }
    }
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":lemuroid-metadata-libretro-db"))
    implementation(project(":lemuroid-touchinput"))

    "baselineProfile"(project(":baselineprofile"))
    implementation(deps.libs.androidx.profileInstaller)

    "bundleImplementation"(project(":bundled-cores"))

    "freeImplementation"(project(":lemuroid-app-ext-free"))
    "playImplementation"(project(":lemuroid-app-ext-play"))

    implementation(deps.libs.androidx.navigation.navigationFragment)
    implementation(deps.libs.androidx.navigation.navigationUi)
    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.androidx.lifecycle.reactiveStreams)

    kapt(deps.libs.androidx.lifecycle.processor)

    implementation(deps.libs.androidx.leanback.leanback)
    implementation(deps.libs.androidx.leanback.leanbackPreference)
    implementation(deps.libs.androidx.leanback.leanbackPaging)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.guava)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.androidx.leanback.tvProvider)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.systemUiController)
    implementation(deps.libs.androidx.compose.accompanist.navigationMaterial)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    implementation(project(":libretrodroid"))

    // Uncomment this when using a local aar file.
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    kapt(deps.libs.dagger.android.processor)
    kapt(deps.libs.dagger.compiler)

    testImplementation("junit:junit:4.13.2")
    // Android's org.json only exists on a phone; unit tests need the real library.
    testImplementation("org.json:json:20240303")
}

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}
