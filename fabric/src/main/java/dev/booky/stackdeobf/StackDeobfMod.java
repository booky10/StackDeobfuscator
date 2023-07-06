package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import dev.booky.stackdeobf.config.StackDeobfConfig;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.util.CompatUtil;
import dev.booky.stackdeobf.util.RemappingRewritePolicy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class StackDeobfMod implements ModInitializer {

    private static CachedMappings mappings;

    public static Throwable remap(Throwable throwable) {
        if (mappings != null) {
            return mappings.remapThrowable(throwable);
        }
        return throwable;
    }

    public static void remap(StackTraceElement[] elements) {
        if (mappings != null) {
            mappings.remapStackTrace(elements);
        }
    }

    public static @Nullable CachedMappings getMappings() {
        return mappings;
    }

    @Override
    public void onInitialize() {
        StackDeobfConfig config = this.loadConfig();

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("stackdeobf_mappings");

        // don't need to print errors, already done after loading
        CachedMappings.create(cacheDir, config.getMappingProvider()).thenAccept(mappings -> {
            StackDeobfMod.mappings = mappings;

            if (config.hasLogInjectEnabled()) {
                CompatUtil.LOGGER.info("Injecting into root logger...");

                RemappingRewritePolicy policy = new RemappingRewritePolicy(
                        mappings, config.shouldRewriteEveryLogMessage());
                policy.inject((Logger) LogManager.getRootLogger());
            }
        });
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
