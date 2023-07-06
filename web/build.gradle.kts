import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

plugins {
    id("application")
    alias(libs.plugins.shadow)
}

val bootClass = "$group.stackdeobf.web.StackDeobfMain"

dependencies {
    implementation(projects.common)

    // required mc libraries
    implementation(libs.bundles.builtin)

    implementation(libs.javalin)
    implementation(libs.caffeine)
    implementation(libs.bundles.log4j)
}

application {
    mainClass.set(bootClass)
}

tasks {
    withType<JavaExec> {
        workingDir = projectDir.resolve("run")
        workingDir.mkdirs()
    }

    jar {
        manifest.attributes(
            "Implementation-Title" to rootProject.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "booky10",

            "Main-Class" to bootClass,
            "Multi-Release" to "true", // prevents some log4j warnings
        )
    }

    shadowJar {
        // merges log4j plugin files
        transform(Log4j2PluginsCacheFileTransformer::class.java)
    }

    assemble {
        dependsOn(shadowJar)
    }
}
