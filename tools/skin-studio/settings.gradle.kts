// The skin studio: a plain JVM build (no Android SDK) that runs the touch controller's tests,
// renders previews of every skin and generates the skins' artwork. See README.md.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "skin-studio"
