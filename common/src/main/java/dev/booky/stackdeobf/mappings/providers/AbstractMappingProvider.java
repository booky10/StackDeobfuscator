package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (14:35 23.03.23)

import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.MappingVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractMappingProvider {

    protected static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");
    private static final Pattern ID_COMMIT_HASH_PATTERN = Pattern.compile("^(.+) / [0-9a-f]{32}$");

    protected final VersionData versionData;
    protected final String name;

    protected AbstractMappingProvider(VersionData versionData, String name) {
        this.versionData = versionData;
        this.name = name;
    }

    protected static String getFabricatedVersion(VersionData versionData) {
        String version;
        // after 1.14.2, fabric switched to using the version id instead of the name for yarn versions
        if (versionData.getWorldVersion() >= 1963
                // WHAT THE FUCK DID HAPPEN HERE? HOW?
                // "Minecraft.Server / f7d695aa1ba843f2aa0cbc2ece6aea49"
                // HOW IS THIS A VERSION ID??? HOW DID THIS GET DEPLOYED ANYWHERE?
                && versionData.getWorldVersion() != 2836) {
            version = versionData.getId();

            // versions before 1.14.3-pre1 (and some combat tests) include the current
            // commit hash in the version.json id; just remove it using a regex everywhere
            Matcher matcher = ID_COMMIT_HASH_PATTERN.matcher(version);
            if (matcher.matches()) {
                version = matcher.group(1);
            }
        } else {
            version = versionData.getName();
        }

        // the first combat test used a very obscure "id", just replace it manually
        if ("1.14.3 - Combat Test".equals(version)) {
            version = "1.14_combat-212796";
        }

        // these just randomly have dots replaced with underscores, I don't want
        // to look into why, just do this manually for now
        //
        // 1_15_combat-1+build.1 also exists, but is completely gone from maven metadata?
        return switch (version) {
            // @formatter:off // what are you doing?
            case "1.15_combat-6", "1.16_combat-0"
                    -> version.replace('.', '_');
            // @formatter:on
            default -> version;
        };
    }

    private static Path getCacheDir(Path fallbackDir) {
        Path cacheDir;
        if (System.getProperties().containsKey("stackdeobf.mappings-cache-dir")) {
            cacheDir = Path.of(System.getProperty("stackdeobf.mappings-cache-dir"));
        } else {
            cacheDir = fallbackDir;
        }

        if (Files.notExists(cacheDir)) {
            try {
                Files.createDirectories(cacheDir);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        if (!Files.isDirectory(cacheDir)) {
            throw new IllegalMonitorStateException(cacheDir + " has to be a directory");
        }
        return cacheDir;
    }

    public CompletableFuture<Void> cacheMappings(Path fallbackCacheDir, MappingVisitor visitor, Executor executor) {
        Path cacheDir = getCacheDir(fallbackCacheDir);

        return CompletableFuture.completedFuture(null)
                .thenComposeAsync($ -> this.downloadMappings(cacheDir, executor), executor)
                .thenCompose($ -> this.parseMappings(executor))
                .thenCompose($ -> this.visitMappings(visitor, executor));
    }

    protected byte[] extractPackagedMappings(byte[] jarBytes) {
        try {
            Path jarPath = Files.createTempFile(null, ".jar");
            try {
                Files.write(jarPath, jarBytes);
                try (FileSystem jar = FileSystems.newFileSystem(jarPath)) {
                    return Files.readAllBytes(jar.getPath("mappings", "mappings.tiny"));
                }
            } finally {
                Files.delete(jarPath);
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private CompletableFuture<Long> trackTime(CompletableFuture<Void> future) {
        long start = System.currentTimeMillis();
        return future.thenApply($ -> System.currentTimeMillis() - start);
    }

    private CompletableFuture<Void> downloadMappings(Path cacheDir, Executor executor) {
        LOGGER.info("Verifying cache of {} mappings...", this.name);
        return this.trackTime(this.downloadMappings0(cacheDir, executor)).thenAccept(timeDiff ->
                LOGGER.info("Verified cache of {} mappings (took {}ms)", this.name, timeDiff));
    }

    private CompletableFuture<Void> parseMappings(Executor executor) {
        LOGGER.info("Parsing {} mappings...", this.name);
        return this.trackTime(this.parseMappings0(executor)).thenAccept(timeDiff ->
                LOGGER.info("Parsed {} mappings (took {}ms)", this.name, timeDiff));
    }

    private CompletableFuture<Void> visitMappings(MappingVisitor visitor, Executor executor) {
        LOGGER.info("Caching {} mappings...", this.name);
        return this.trackTime(this.visitMappings0(visitor, executor)).thenAccept(timeDiff ->
                LOGGER.info("Cached {} mappings (took {}ms)", this.name, timeDiff));
    }

    protected abstract CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor);

    protected abstract CompletableFuture<Void> parseMappings0(Executor executor);

    protected abstract CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor);

    public String getName() {
        return this.name;
    }
}
