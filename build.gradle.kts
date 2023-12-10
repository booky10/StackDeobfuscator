import org.gradle.configurationcache.extensions.capitalized
import java.io.ByteArrayOutputStream

plugins {
    id("java-library")
    id("maven-publish")
}

fun getGitCommit(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        standardOutput = stdout
    }
    return stdout.toString().trim()
}

tasks["jar"].enabled = false

subprojects {
    apply<JavaLibraryPlugin>()
    apply<MavenPublishPlugin>()

    version = "1.4.2+${getGitCommit()}"
    group = "dev.booky"

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://libraries.minecraft.net/")
    }

    java {
        withSourcesJar()
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }
    }

    publishing {
        publications.create<MavenPublication>("maven") {
            artifactId = "${rootProject.name}-${project.name}".lowercase()
            from(components["java"])
        }
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = Charsets.UTF_8.name()
            options.release.set(17)
        }

        withType<Jar>().configureEach {
            val joinedName = "${rootProject.name}${project.name.capitalized()}"
            archiveBaseName.set(joinedName)

            sequenceOf("COPYING", "COPYING.LESSER")
                .map { rootProject.layout.projectDirectory.file(it) }
                .forEach { sourceFile ->
                    from(sourceFile) {
                        rename { return@rename "${it}_$joinedName" }
                    }
                }
        }
    }
}
