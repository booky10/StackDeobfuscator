import org.gradle.configurationcache.extensions.capitalized

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "StackDeobfuscator"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

setupSubProject("common")
setupSubProject("fabric")
setupSubProject("web")

fun setupSubProject(name: String) {
    val projectName = "${rootProject.name}-${name.capitalized()}"
    include(projectName)
    project(":$projectName").projectDir = file(name)
}
