@file:Suppress("ktlint")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "Arcadune"

include(
    ":libretrodroid",
    ":retrograde-util",
    ":retrograde-app-shared",
    ":lemuroid-touchinput",
    ":lemuroid-app",
    ":lemuroid-metadata-libretro-db",
    ":lemuroid-app-ext-free",
    ":lemuroid-app-ext-play",
    ":bundled-cores",
    ":baselineprofile"
)

project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}

if (usePlayDynamicFeatures()) {
    include(
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
        ":lemuroid_core_fmsx"
    )

    project(":lemuroid_core_gambatte").projectDir = File("lemuroid-cores/lemuroid_core_gambatte")
    project(":lemuroid_core_melonds").projectDir = File("lemuroid-cores/lemuroid_core_melonds")
    project(":lemuroid_core_fbneo").projectDir = File("lemuroid-cores/lemuroid_core_fbneo")
    project(":lemuroid_core_fceumm").projectDir = File("lemuroid-cores/lemuroid_core_fceumm")
    project(":lemuroid_core_genesis_plus_gx").projectDir = File("lemuroid-cores/lemuroid_core_genesis_plus_gx")
    project(":lemuroid_core_mame2003_plus").projectDir = File("lemuroid-cores/lemuroid_core_mame2003_plus")
    project(":lemuroid_core_mgba").projectDir = File("lemuroid-cores/lemuroid_core_mgba")
    project(":lemuroid_core_mupen64plus_next_gles3").projectDir = File("lemuroid-cores/lemuroid_core_mupen64plus_next_gles3")
    project(":lemuroid_core_pcsx_rearmed").projectDir = File("lemuroid-cores/lemuroid_core_pcsx_rearmed")
    project(":lemuroid_core_ppsspp").projectDir = File("lemuroid-cores/lemuroid_core_ppsspp")
    project(":lemuroid_core_snes9x").projectDir = File("lemuroid-cores/lemuroid_core_snes9x")
    project(":lemuroid_core_stella").projectDir = File("lemuroid-cores/lemuroid_core_stella")
    project(":lemuroid_core_handy").projectDir = File("lemuroid-cores/lemuroid_core_handy")
    project(":lemuroid_core_prosystem").projectDir = File("lemuroid-cores/lemuroid_core_prosystem")
    project(":lemuroid_core_mednafen_pce_fast").projectDir = File("lemuroid-cores/lemuroid_core_mednafen_pce_fast")
    project(":lemuroid_core_mednafen_ngp").projectDir = File("lemuroid-cores/lemuroid_core_mednafen_ngp")
    project(":lemuroid_core_mednafen_wswan").projectDir = File("lemuroid-cores/lemuroid_core_mednafen_wswan")
    project(":lemuroid_core_dosbox_pure").projectDir = File("lemuroid-cores/lemuroid_core_dosbox_pure")
    project(":lemuroid_core_citra").projectDir = File("lemuroid-cores/lemuroid_core_citra")
    project(":lemuroid_core_flycast").projectDir = File("lemuroid-cores/lemuroid_core_flycast")
    project(":lemuroid_core_opera").projectDir = File("lemuroid-cores/lemuroid_core_opera")
    project(":lemuroid_core_yabasanshiro").projectDir = File("lemuroid-cores/lemuroid_core_yabasanshiro")
    project(":lemuroid_core_picodrive").projectDir = File("lemuroid-cores/lemuroid_core_picodrive")
    project(":lemuroid_core_a5200").projectDir = File("lemuroid-cores/lemuroid_core_a5200")
    project(":lemuroid_core_atari800").projectDir = File("lemuroid-cores/lemuroid_core_atari800")
    project(":lemuroid_core_gearcoleco").projectDir = File("lemuroid-cores/lemuroid_core_gearcoleco")
    project(":lemuroid_core_vice_x64").projectDir = File("lemuroid-cores/lemuroid_core_vice_x64")
    project(":lemuroid_core_applewin").projectDir = File("lemuroid-cores/lemuroid_core_applewin")
    project(":lemuroid_core_fmsx").projectDir = File("lemuroid-cores/lemuroid_core_fmsx")
}
