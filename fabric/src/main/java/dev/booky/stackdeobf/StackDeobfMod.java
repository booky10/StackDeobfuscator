package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:38 18.12.22)

import dev.booky.stackdeobf.config.StackDeobfConfig;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.util.RemappingRewritePolicy;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class StackDeobfMod implements PreLaunchEntrypoint {

    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger("StackDeobfuscator");
    private static final VersionData VERSION_DATA = VersionData.fromClasspath();

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

    public static VersionData getVersionData() {
        return VERSION_DATA;
    }

    @Override
    public void onPreLaunch() {
        StackDeobfConfig config = this.loadConfig();

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path cacheDir = gameDir.resolve("stackdeobf_mappings");

        // don't need to print errors, already done after loading
        CachedMappings.create(cacheDir, config.getMappingProvider()).thenAccept(mappings -> {
            StackDeobfMod.mappings = mappings;

            if (config.hasLogInjectEnabled()) {
                LOGGER.info("Injecting into root logger...");

                RemappingRewritePolicy policy = new RemappingRewritePolicy(
                        mappings, config.shouldRewriteEveryLogMessage());
                policy.inject((Logger) LogManager.getRootLogger());
            }
        });
    }

    private StackDeobfConfig loadConfig() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("stackdeobf.json");
            return StackDeobfConfig.load(VERSION_DATA, configPath);
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception occurred while loading stack deobfuscator configuration", throwable);
        }
    }
}
