enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "StackDeobfuscator"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

setupSubProject("common")
setupSubProject("fabric")
setupSubProject("web")

fun setupSubProject(name: String) {
    val projectName = "${rootProject.name}-${name[0].titlecase() + name.substring(1)}"
    include(projectName)
    project(":$projectName").projectDir = file(name)
}
