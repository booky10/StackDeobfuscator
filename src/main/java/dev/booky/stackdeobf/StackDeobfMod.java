package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import com.mojang.logging.LogUtils;
import dev.booky.stackdeobf.config.StackDeobfConfig;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.mappings.RemappingUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;

import java.io.IOException;
import java.nio.file.Path;

public class StackDeobfMod implements ModInitializer {

    public static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        StackDeobfConfig config = this.loadConfig();

        if (config.hasLogInjectEnabled()) {
            RemappingUtil.injectLogFilter((Logger) LogManager.getRootLogger());
        }

        CachedMappings.init(config.getMappingProvider());
    }

    private StackDeobfConfig loadConfig() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("stackdeobf.json");
            return StackDeobfConfig.load(configPath);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
