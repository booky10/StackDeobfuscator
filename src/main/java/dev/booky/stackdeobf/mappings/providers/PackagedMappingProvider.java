package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:08 23.03.23)

import dev.booky.stackdeobf.StackDeobfMod;
import dev.booky.stackdeobf.http.HttpUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class PackagedMappingProvider extends AbstractMappingProvider {

    protected final URI metaUri;
    protected final String mappingUri;

    protected Path path;
    protected MemoryMappingTree mappings;

    public PackagedMappingProvider(String name, String repo, String groupId, String artifactId, String classifier) {
        super(name);

        groupId = groupId.replace('.', '/');
        String baseUri = repo + "/" + groupId + "/" + artifactId + "/";

        this.metaUri = URI.create(baseUri + "maven-metadata.xml");
        this.mappingUri = baseUri + "$VER/" + artifactId + "-$VER-" + classifier + ".jar";
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        return this.fetchLatestVersion(MC_VERSION, executor)
                .thenCompose(build -> {
                    this.path = cacheDir.resolve(this.name + "_" + build + ".txt");

                    // already cached, don't download anything
                    if (Files.exists(this.path)) {
                        StackDeobfMod.LOGGER.info("Mappings for {} build {} are already downloaded", this.name, build);
                        return CompletableFuture.completedFuture(null);
                    }

                    Path jarPath = cacheDir.resolve(this.name + "_" + build + ".jar");
                    URI uri = URI.create(this.mappingUri.replace("$VER", build));

                    StackDeobfMod.LOGGER.info("Downloading {} mappings jar for build {}...", this.name, build);
                    return HttpUtil.getAsync(uri, executor).thenAccept(mappingJarBytes -> {
                        try {
                            Files.write(jarPath, mappingJarBytes);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }

                        // extract the mappings file from the mappings jar
                        try (FileSystem jar = FileSystems.newFileSystem(jarPath)) {
                            Path mappingsPath = jar.getPath("mappings/mappings.tiny");
                            Files.copy(mappingsPath, this.path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }

                        // delete the jar, it is not needed anymore
                        try {
                            Files.delete(jarPath);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
                });
    }

    private CompletableFuture<String> fetchLatestVersion(String mcVersion, Executor executor) {
        StackDeobfMod.LOGGER.info("Fetching latest {} build for {}...", this.name, mcVersion);
        return HttpUtil.getAsync(this.metaUri, executor).thenApply(resp -> {
            try (InputStream input = new ByteArrayInputStream(resp)) {
                Document document;
                try {
                    // https://stackoverflow.com/a/14968272
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    document = factory.newDocumentBuilder().parse(input);
                } catch (ParserConfigurationException | SAXException exception) {
                    throw new IOException(exception);
                }

                NodeList versions = document.getElementsByTagName("version");
                for (int i = versions.getLength() - 1; i >= 0; i--) {
                    String version = versions.item(i).getTextContent();
                    if (version.startsWith(mcVersion + "+")) {
                        return version;
                    }
                }

                throw new IllegalArgumentException("Can't find " + this.name + " mappings for minecraft version " + mcVersion);
            } catch (IOException exception) {
                throw new RuntimeException("Can't parse response from " + this.metaUri + " for " + mcVersion, exception);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MemoryMappingTree inter2named = new MemoryMappingTree();
                MappingReader.read(this.path, MappingFormat.TINY_2, inter2named);
                this.mappings = inter2named;
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
}
