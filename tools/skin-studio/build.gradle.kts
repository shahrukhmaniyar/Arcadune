// See README.md. The controller code is compiled straight from lemuroid-touchinput, against
// Compose Multiplatform for the desktop (the same androidx.compose packages) and the Android
// framework classes from Robolectric's android-all, plus a few stubs (stubs/ and generated).
plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}



val touchinput = rootDir.resolve("../../lemuroid-touchinput/src")
val generatedStubs = layout.buildDirectory.dir("generated/stubs")
val androidAll = "org.robolectric:android-all:15-robolectric-12650502"
val compose = "1.5.12"

// TouchControllerID and the shared key codes, copied from the real sources without the parts that
// need the Android build (the PadKit layouts), so they never drift from the app.
val generateStubs by tasks.registering {
    val ids = touchinput.resolve("main/java/com/swordfish/touchinput/radial/settings/TouchControllerID.kt")
    val controls = touchinput.resolve("main/java/com/swordfish/touchinput/radial/layouts/shared/Controls.kt")
    inputs.files(ids, controls)
    outputs.dir(generatedStubs)
    doLast {
        val out = generatedStubs.get().asFile.apply { mkdirs() }
        val enumBody = Regex("""enum class TouchControllerID \{(.*?);""", RegexOption.DOT_MATCHES_ALL).find(ids.readText())!!.groupValues[1]
        out.resolve("TouchControllerID.kt").writeText(
            "package com.swordfish.touchinput.radial.settings\n\nenum class TouchControllerID {$enumBody;\n}\n",
        )
        val text = controls.readText()
        val keys = Regex("""^const val KEYCODE_\w+ = .*$""", RegexOption.MULTILINE).findAll(text).joinToString("\n") { it.value }
        val layouts = Regex("""object ComposeTouchLayouts \{.*?\n\}""", RegexOption.DOT_MATCHES_ALL).find(text)!!.value
        out.resolve("Controls.kt").writeText(
            "package com.swordfish.touchinput.radial.layouts.shared\n\nimport android.view.KeyEvent\n\n$keys\n\n$layouts\n",
        )
    }
}

sourceSets {
    main {
        kotlin.srcDirs("stubs", generatedStubs, touchinput.resolve("main/java/com/swordfish/touchinput/skin"), "src/main/kotlin")
        // Reads Android assets: the studio has its own reader (StudioArt).
        kotlin.exclude("**/SkinArtLoader.kt")
    }
    test { kotlin.srcDirs(touchinput.resolve("test/java")) }
}

tasks.named("compileKotlin") { dependsOn(generateStubs) }

dependencies {
    implementation("io.github.swordfish90:padkit-desktop:1.0.0-beta1")
    implementation("org.jetbrains.compose.ui:ui-desktop:$compose")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$compose")
    implementation("org.jetbrains.compose.material3:material3-desktop:$compose")
    implementation("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$compose")
    implementation(androidAll)
    testImplementation("junit:junit:4.13.2")
}

configurations.all {
    // Compose 1.6+ needs androidx artifacts that only Google's repository has; 1.5 does not.
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("org.jetbrains.compose") && requested.version?.startsWith("1.5") != true) useVersion(compose)
    }
    exclude(group = "androidx.lifecycle")
    exclude(group = "androidx.annotation")
    exclude(group = "androidx.arch.core")
}

/** Writes the skins' artwork into the app's assets (lemuroid-touchinput/src/main/assets/skin-art). */
tasks.register<JavaExec>("art") {
    group = "skin studio"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("StudioArtKt")
    args = listOf(touchinput.resolve("main/assets/skin-art").path)
}

/** Renders every skin on several phone and tablet shapes into build/preview (ONLY=GB,NES limits it; VARIANT=minimal). */
tasks.register<JavaExec>("preview") {
    group = "skin studio"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("PreviewKt")
    args = listOf(layout.buildDirectory.dir("preview").get().asFile.path, touchinput.resolve("main/assets/skin-art").path)
}

// The artwork test reads the assets: rerun it when they change.
tasks.named<Test>("test") { inputs.dir(touchinput.resolve("main/assets")) }
