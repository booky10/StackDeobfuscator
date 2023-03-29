package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (14:35 23.03.23)

import com.google.common.base.Preconditions;
import dev.booky.stackdeobf.StackDeobfMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingVisitor;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public abstract class AbstractMappingProvider {

    protected static final String MC_VERSION = SharedConstants.getCurrentVersion().getId();
    protected final String name;

    protected AbstractMappingProvider(String name) {
        this.name = name;
    }

    public CompletableFuture<Void> cacheMappings(MappingVisitor visitor, Executor executor) {
        Path cacheDir = getCacheDir();

        return CompletableFuture.completedFuture(null)
                .thenComposeAsync($ -> this.downloadMappings(cacheDir, executor), executor)
                .thenCompose($ -> this.parseMappings(executor))
                .thenCompose($ -> this.visitMappings(visitor, executor));
    }

    private static Path getCacheDir() {
        Path cacheDir;
        if (System.getProperties().containsKey("stackdeobf.mappings-cache-dir")) {
            cacheDir = Path.of(System.getProperty("stackdeobf.mappings-cache-dir"));
        } else {
            cacheDir = FabricLoader.getInstance().getGameDir().resolve("stackdeobf_mappings");
        }

        if (Files.notExists(cacheDir)) {
            try {
                Files.createDirectories(cacheDir);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        Preconditions.checkState(Files.isDirectory(cacheDir), cacheDir + " has to be a directory");
        return cacheDir;
    }

    private CompletableFuture<Long> trackTime(CompletableFuture<Void> future) {
        long start = System.currentTimeMillis();
        return future.thenApply($ -> System.currentTimeMillis() - start);
    }

    private CompletableFuture<Void> downloadMappings(Path cacheDir, Executor executor) {
        StackDeobfMod.LOGGER.info("Verifying cache of {} mappings...", this.name);
        return this.trackTime(this.downloadMappings0(cacheDir, executor)).thenAccept(timeDiff ->
                StackDeobfMod.LOGGER.info("Verified cache of {} mappings (took {}ms)", this.name, timeDiff));
    }

    private CompletableFuture<Void> parseMappings(Executor executor) {
        StackDeobfMod.LOGGER.info("Parsing {} mappings...", this.name);
        return this.trackTime(this.parseMappings0(executor)).thenAccept(timeDiff ->
                StackDeobfMod.LOGGER.info("Parsed {} mappings (took {}ms)", this.name, timeDiff));
    }

    private CompletableFuture<Void> visitMappings(MappingVisitor visitor, Executor executor) {
        StackDeobfMod.LOGGER.info("Caching {} mappings...", this.name);
        return this.trackTime(this.visitMappings0(visitor, executor)).thenAccept(timeDiff ->
                StackDeobfMod.LOGGER.info("Cached {} mappings (took {}ms)", this.name, timeDiff));
    }

    protected abstract CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor);

    protected abstract CompletableFuture<Void> parseMappings0(Executor executor);

    protected abstract CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor);

    public String getName() {
        return this.name;
    }
}
