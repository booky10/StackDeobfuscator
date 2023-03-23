package dev.booky.stackdeobf.config;
// Created by booky10 in StackDeobfuscator (17:12 23.03.23)

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import dev.booky.stackdeobf.mappings.providers.YarnMappingProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StackDeobfConfig {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().setPrettyPrinting()
            .registerTypeAdapter(AbstractMappingProvider.class, MappingProviderSerializer.INSTANCE)
            .create();

    @SerializedName("inject-logger")
    private boolean logInject;

    @SerializedName("mapping-type")
    private AbstractMappingProvider mappingProvider;

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
        config.mappingProvider = new YarnMappingProvider();

        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        }
        return config;
    }

    public boolean hasLogInjectEnabled() {
        return this.logInject;
    }

    public AbstractMappingProvider getMappingProvider() {
        return this.mappingProvider;
    }
}
