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
            languageVersion = JavaLanguageVersion.of(17)
            vendor = JvmVendorSpec.ADOPTIUM
        }
    }

    publishing {
        publications.create<MavenPublication>("maven") {
            artifactId = project.name.lowercase()
            from(components["java"])
        }

        if (rootProject.ext.has("publishingRepo")) {
            val publishingRepo = rootProject.ext.get("publishingRepo") as String
            repositories.maven(publishingRepo) {
                name = url.host.replace(".", "")
                authentication { create<BasicAuthentication>("basic") }
                credentials(PasswordCredentials::class)
            }
        }
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = Charsets.UTF_8.name()
            options.release = 17
        }

        withType<Jar>().configureEach {
            archiveBaseName.set(project.name.replace("-", ""))

            sequenceOf("COPYING", "COPYING.LESSER")
                .map { rootProject.layout.projectDirectory.file(it) }
                .forEach { sourceFile ->
                    from(sourceFile) {
                        rename { return@rename "${it}_${rootProject.name.lowercase()}" }
                    }
                }
        }
    }
}
