package dev.booky.stackdeobf.mappings;
// Created by booky10 in StackDeobfuscator (17:04 20.03.23)

import com.mojang.logging.LogUtils;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;

public final class CachedMappings {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Int2ObjectMap<String> CLASSES = new Int2ObjectOpenHashMap<>(); // includes package
    private static final Int2ObjectMap<String> METHODS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<String> FIELDS = new Int2ObjectOpenHashMap<>();

    private CachedMappings() {
    }

    public static void init(AbstractMappingProvider provider) {
        LOGGER.info("Caching {} mappings...", provider.getName());
        try {
            // visitor expects mappings to be intermediary -> named
            provider.cacheMappings(new MappingCacheVisitor(CLASSES, METHODS, FIELDS));
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        LOGGER.info("Finished caching {} mappings:", provider.getName());
        LOGGER.info("  Classes: " + CLASSES.size());
        LOGGER.info("  Methods: " + METHODS.size());
        LOGGER.info("  Fields: " + FIELDS.size());
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
