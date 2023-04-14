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
    private static final int CURRENT_VERSION = 2;

    @SerializedName("config-version-dont-touch-this")
    private int version;

    @SerializedName("inject-logger")
    private boolean logInject = true;

    @SerializedName("rewrite-every-log-message")
    private boolean rewriteEveryLogMessage = false;

    @SerializedName("mapping-type")
    private AbstractMappingProvider mappingProvider;

    private StackDeobfConfig() {
    }

    public static StackDeobfConfig load(Path configPath) throws IOException {
        StackDeobfConfig config;

        if (Files.exists(configPath)) {
            try (BufferedReader reader = Files.newBufferedReader(configPath)) {
                config = GSON.fromJson(reader, StackDeobfConfig.class);
            }

            if (config.version == CURRENT_VERSION) {
                // don't need to save the config again, it is
                // already up-to-date
                return config;
            }

            // migrate the config format to the newer version

            if (config.version < 2) {
                config.rewriteEveryLogMessage = false;
            }
        } else {
            // create default config
            config = new StackDeobfConfig();
            config.mappingProvider = new YarnMappingProvider();
        }

        // either the config didn't exist before, or the config version was too old
        config.version = CURRENT_VERSION;
        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(config, writer);
        }
        return config;
    }

    public boolean hasLogInjectEnabled() {
        return this.logInject;
    }

    public boolean shouldRewriteEveryLogMessage() {
        return this.rewriteEveryLogMessage;
    }

    public AbstractMappingProvider getMappingProvider() {
        return this.mappingProvider;
    }
}
