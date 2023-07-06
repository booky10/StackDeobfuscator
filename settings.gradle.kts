enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "StackDeobfuscator"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
