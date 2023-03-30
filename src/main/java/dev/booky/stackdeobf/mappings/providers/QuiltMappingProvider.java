package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:06 23.03.23)

import com.google.common.base.Preconditions;
import dev.booky.stackdeobf.compat.CompatUtil;

public final class QuiltMappingProvider extends PackagedMappingProvider {

    public QuiltMappingProvider() {
        super("quilt", "https://maven.quiltmc.org/repository/release",
                "org.quiltmc", "quilt-mappings", "intermediary-v2");
        Preconditions.checkState(CompatUtil.WORLD_VERSION >= 3088, "Quilt mappings only exist starting from 22w14a");
        Preconditions.checkState(CompatUtil.WORLD_VERSION >= 3120, "Quilt only provides compatible mappings starting with 1.19.2");
    }
}
