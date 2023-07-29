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
        // this may seem like some unnecessary exclusions, but this saves about 24MiB of disk space...
        // fastutil is very big, and this project only requires the Int2Object maps
        val fastutilPkg = "it/unimi/dsi/fastutil"
        sequenceOf("booleans", "bytes", "chars", "doubles", "floats", "io", "longs", "shorts")
            .forEach { exclude("$fastutilPkg/$it/*") }
        sequenceOf("A", "B", "I", "P", "S")
            .forEach { exclude("$fastutilPkg/$it*") }
        sequenceOf(
            "Abstract", "ObjectA", "ObjectB", "ObjectCh", "ObjectCom", "Object2",
            "ObjectD", "ObjectF", "ObjectH", "ObjectIm", "ObjectIn", "ObjectIterat", "ObjectL",
            "ObjectO", "ObjectR", "ObjectR", "ObjectSem", "ObjectSh", "ObjectSo", "ObjectSp", "Ref"
        ).forEach { exclude("$fastutilPkg/objects/$it*") }
        sequenceOf(
            "AbstractInt2B", "AbstractInt2C", "AbstractInt2D", "AbstractInt2F", "AbstractInt2I",
            "AbstractInt2L", "AbstractInt2ObjectS", "AbstractInt2R", "AbstractInt2S", "AbstractIntB",
            "AbstractIntC", "AbstractIntI", "AbstractIntL", "AbstractIntP", "AbstractIntS", "Int2B",
            "Int2C", "Int2D", "Int2F", "Int2I", "Int2L", "Int2ObjectA", "Int2ObjectL", "Int2ObjectOpenC",
            "Int2ObjectR", "Int2ObjectS", "Int2R", "Int2S", "IntA", "IntB", "IntCh", "IntCom", "IntCon",
            "IntD", "IntF", "IntH", "IntIm", "IntIn", "IntIterator", "IntL", "IntM", "IntO", "IntP",
            "IntR", "IntSem", "IntSh", "IntSo", "IntSp", "IntSt", "IntU"
        ).forEach { exclude("$fastutilPkg/ints/$it*") }

        // rebuild shadowjar when exclusions change
        inputs.properties("excludes" to excludes)

        // merges log4j plugin files
        transform(Log4j2PluginsCacheFileTransformer::class.java)
    }

    assemble {
        dependsOn(shadowJar)
    }
}
