package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:04 20.03.23)

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import dev.booky.stackdeobf.util.CompatUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CachedMappings {

    // "CLASSES" name has package prefixed (separated by '.')
    private static final Int2ObjectMap<String> CLASSES = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private static final Int2ObjectMap<String> METHODS = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private static final Int2ObjectMap<String> FIELDS = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

    private CachedMappings() {
    }

    public static void init(AbstractMappingProvider provider) {
        CompatUtil.LOGGER.info("Creating asynchronous mapping cache executor...");
        ExecutorService cacheExecutor = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("Mappings Cache Thread").setDaemon(true).build());
        long start = System.currentTimeMillis();

        // visitor expects mappings to be intermediary -> named
        provider.cacheMappings(new MappingCacheVisitor(CLASSES, METHODS, FIELDS), cacheExecutor)
                .thenAccept($ -> {
                    long timeDiff = System.currentTimeMillis() - start;
                    CompatUtil.LOGGER.info("Cached mappings have been built (took {}ms)", timeDiff);

                    CompatUtil.LOGGER.info("  Classes: " + CLASSES.size());
                    CompatUtil.LOGGER.info("  Methods: " + METHODS.size());
                    CompatUtil.LOGGER.info("  Fields: " + FIELDS.size());
                })
                // needs to be executed asynchronously, otherwise the
                // executor of the current thread would be shut down
                .thenRunAsync(() -> {
                    CompatUtil.LOGGER.info("Shutting down asynchronous mapping cache executor...");
                    cacheExecutor.shutdown();
                });
    }

    public static @Nullable String remapClass(int id) {
        return CLASSES.get(id);
    }

    public static @Nullable String remapMethod(int id) {
        return METHODS.get(id);
    }

    public static @Nullable String remapField(int id) {
        return FIELDS.get(id);
    }
}
