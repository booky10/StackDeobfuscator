package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import dev.booky.stackdeobf.config.StackDeobfConfig;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.mappings.RemappingUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.nio.file.Path;

public class StackDeobfMod implements ModInitializer {

    @Override
    public void onInitialize() {
        StackDeobfConfig config = this.loadConfig();

        if (config.hasLogInjectEnabled()) {
            RemappingUtil.injectLogFilter((Logger) LogManager.getRootLogger(), config.shouldRewriteEveryLogMessage());
        }

        CachedMappings.init(config.getMappingProvider());
    }

    private StackDeobfConfig loadConfig() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("stackdeobf.json");
            return StackDeobfConfig.load(configPath);
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception occurred while loading stack deobfuscator configuration", throwable);
        }
    }
}
