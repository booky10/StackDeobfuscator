package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:06 23.03.23)

public final class QuiltMappingProvider extends PackagedMappingProvider {

    public QuiltMappingProvider() {
        super("quilt", "https://maven.quiltmc.org/repository/release",
                "org.quiltmc", "quilt-mappings", "intermediary-v2");
    }
}
