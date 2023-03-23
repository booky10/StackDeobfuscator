package dev.booky.stackdeobf.config;
// Created by booky10 in StackDeobfuscator (17:12 23.03.23)

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.booky.stackdeobf.mappings.types.AbstractMappingType;
import dev.booky.stackdeobf.mappings.types.YarnMappingType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StackDeobfConfig {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().setPrettyPrinting()
            .registerTypeAdapter(AbstractMappingType.class, MappingTypeSerializer.INSTANCE)
            .create();

    @SerializedName("inject-logger")
    private boolean logInject;

    @SerializedName("mapping-type")
    private AbstractMappingType mappingType;

    private StackDeobfConfig() {
    }

    public static StackDeobfConfig load(Path configPath) throws IOException {
        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath)) {
                return GSON.fromJson(reader, StackDeobfConfig.class);
            }
        }

        // save default config
        StackDeobfConfig config = new StackDeobfConfig();
        config.logInject = true;
        config.mappingType = new YarnMappingType();

        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        }
        return config;
    }

    public boolean hasLogInjectEnabled() {
        return this.logInject;
    }

    public AbstractMappingType getMappingType() {
        return this.mappingType;
    }
}
