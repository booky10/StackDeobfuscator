package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (17:42 23.03.23)

import com.google.common.base.Preconditions;
import dev.booky.stackdeobf.compat.CompatUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class CustomMappingProvider extends AbstractMappingProvider {

    private final Path path;
    private final MappingFormat format;
    private MemoryMappingTree mappings;

    public CustomMappingProvider(Path path, MappingFormat format) {
        super("custom");
        this.path = path;
        this.format = format;
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        try {
            // this one check does not need to be done asynchronously
            if (Files.notExists(this.path)) {
                throw new FileNotFoundException("Custom mappings file at " + this.path + " doesn't exist");
            }
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        CompatUtil.LOGGER.info("Skipping mappings download, custom mappings are selected");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MemoryMappingTree mappings = new MemoryMappingTree();
                this.readPath(this.path, mappings, this.format);

                if (!"intermediary".equals(mappings.getSrcNamespace())) {
                    if (!mappings.getDstNamespaces().contains("intermediary")) {
                        throw new IllegalStateException("Custom mappings don't map from 'intermediary'");
                    }

                    // try to switch to have intermediary as source
                    MemoryMappingTree switchedMappings = new MemoryMappingTree();
                    mappings.accept(new MappingSourceNsSwitch(switchedMappings, "intermediary"));
                    mappings = switchedMappings;
                }

                if (!mappings.getDstNamespaces().contains("named")) {
                    throw new IllegalStateException("Custom mappings don't map to 'named'");
                }

                // 'named' needs to be the first destination namespace
                if (mappings.getDstNamespaces().indexOf("named") != 0) {
                    List<String> orderedNamespaces = new ArrayList<>(mappings.getDstNamespaces());
                    orderedNamespaces.remove("named");
                    orderedNamespaces.add(0, "named");

                    MemoryMappingTree reorderedMappings = new MemoryMappingTree();
                    mappings.accept(new MappingDstNsReorder(reorderedMappings, orderedNamespaces));
                    mappings = reorderedMappings;
                }

                this.mappings = mappings;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                this.mappings.accept(visitor);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    private void readPath(Path path, MappingVisitor visitor, MappingFormat format) throws IOException {
        String contentType = Files.probeContentType(path);
        if ("application/gzip".equals(contentType)) {
            try (InputStream fileInput = Files.newInputStream(path);
                 GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                 Reader reader = new InputStreamReader(gzipInput)) {
                MappingReader.read(reader, format, visitor);
            }
        }

        if ("application/java-archive".equals(contentType) || "application/zip".equals(contentType)) {
            try (FileSystem archive = FileSystems.newFileSystem(path)) {
                Path innerPath = archive.getPath("mappings", "mappings.tiny");
                if (Files.notExists(innerPath)) {
                    Path singlePath = null;
                    for (Path directory : archive.getRootDirectories()) {
                        try (Stream<Path> files = Files.list(directory)) {
                            for (Path subPath : files.toList()) {
                                Preconditions.checkState(singlePath == null,
                                        "More than one file found in " + path.toAbsolutePath());
                                singlePath = subPath;
                            }
                        }
                    }
                    innerPath = singlePath;
                }
                Objects.requireNonNull(innerPath, "No mappings file found in " + path.toAbsolutePath());

                this.readPath(innerPath, visitor, format);
                return;
            }
        }

        if (contentType != null && !"text/plain".equals(contentType)) {
            CompatUtil.LOGGER.warn("Can't recognize content type of {}, assuming it's plain text",
                    path.toAbsolutePath());
        }

        MappingReader.read(path, format, visitor);
    }

    public Path getPath() {
        return this.path;
    }

    public MappingFormat getFormat() {
        return this.format;
    }
}
