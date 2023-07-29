package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (20:56 30.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class IntermediaryMappingProvider extends AbstractMappingProvider {

    private static final String REPO_URL = System.getProperty("stackdeobf.intermediary.repo-url", "https://maven.fabricmc.net");
    private static final MavenArtifactInfo MAPPINGS_ARTIFACT = MavenArtifactInfo.parse(REPO_URL,
            System.getProperty("stackdeobf.intermediary.mappings-artifact", "net.fabricmc:intermediary:v2"));

    // even though yarn didn't have sha512 at the time, intermediary did
    private static final VerifiableUrl.HashType HASH_TYPE = VerifiableUrl.HashType.SHA512;

    private Path path;
    private MemoryMappingTree mappings;

    // only used as a conversion step (mojang + hashed quilt)
    IntermediaryMappingProvider(VersionData versionData) {
        super(versionData, "intermediary");
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        this.path = cacheDir.resolve("intermediary_" + this.versionData.getId() + ".gz");
        if (Files.exists(this.path)) {
            return CompletableFuture.completedFuture(null);
        }

        return MAPPINGS_ARTIFACT.buildVerifiableUrl(this.versionData.getId(), "jar", HASH_TYPE, executor)
                .thenCompose(verifiableUrl -> {
                    LOGGER.info("Downloading intermediary mappings for {}...", this.versionData.getId());
                    return verifiableUrl.get(executor);
                })
                .thenAccept(jarBytes -> {
                    byte[] mappingBytes = this.extractPackagedMappings(jarBytes);
                    try (OutputStream fileOutput = Files.newOutputStream(this.path);
                         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                        gzipOutput.write(mappingBytes);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            MemoryMappingTree mappings = new MemoryMappingTree();

            try (InputStream fileInput = Files.newInputStream(this.path);
                 GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                 Reader reader = new InputStreamReader(gzipInput)) {
                MappingReader.read(reader, MappingFormat.TINY_2, mappings);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

            this.mappings = mappings;
            return null;
        }, executor);
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        throw new UnsupportedOperationException();
    }

    public Path getPath() {
        return this.path;
    }

    public MemoryMappingTree getMappings() {
        return this.mappings;
    }
}
