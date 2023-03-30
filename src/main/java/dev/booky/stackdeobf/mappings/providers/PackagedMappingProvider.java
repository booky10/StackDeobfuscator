package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:08 23.03.23)

import dev.booky.stackdeobf.compat.CompatUtil;
import dev.booky.stackdeobf.http.HttpUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
        // after 1.14.2, fabric switched to using the version id instead of the name for yarn versions
        String version = CompatUtil.WORLD_VERSION >= 1963 ? CompatUtil.VERSION_ID : CompatUtil.VERSION_NAME;

        // versions somewhere before mojang mappings (I don't have decompiled mc versions
        // before mojang mappings) include the current commit hash in the version.json name
        version = StringUtils.split(version, ' ')[0];

        return this.fetchLatestVersion(cacheDir, version, executor)
                .thenCompose(build -> {
                    this.path = cacheDir.resolve(this.name + "_" + build + ".gz");

                    // already cached, don't download anything
                    if (Files.exists(this.path)) {
                        CompatUtil.LOGGER.info("Mappings for {} build {} are already downloaded", this.name, build);
                        return CompletableFuture.completedFuture(null);
                    }

                    Path jarPath;
                    try {
                        jarPath = Files.createTempFile(this.name + "_" + build, ".jar");
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    URI uri = URI.create(this.mappingUri.replace("$VER", build));
                    CompatUtil.LOGGER.info("Downloading {} mappings jar for build {}...", this.name, build);

                    return HttpUtil.getAsync(uri, executor).thenAccept(mappingJarBytes -> {
                        try {
                            Files.write(jarPath, mappingJarBytes);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }

                        // extract the mappings file from the mappings jar
                        try (FileSystem jar = FileSystems.newFileSystem(jarPath)) {
                            Path mappingsPath = jar.getPath("mappings", "mappings.tiny");
                            try (OutputStream fileOutput = Files.newOutputStream(this.path);
                                 GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                                Files.copy(mappingsPath, gzipOutput);
                            }
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

    private CompletableFuture<String> fetchLatestVersion(Path cacheDir, String mcVersion, Executor executor) {
        return CompletableFuture.completedFuture(null).thenComposeAsync($ -> {
            Path versionCachePath = cacheDir.resolve(this.name + "_" + mcVersion + "_latest.txt");
            if (Files.exists(versionCachePath)) {
                try {
                    long lastVersionFetch = Files.getLastModifiedTime(versionCachePath).toMillis();
                    long timeDiff = (System.currentTimeMillis() - lastVersionFetch) / 1000 / 60;

                    long maxTimeDiff = Long.getLong("stackdeobf.build-refresh-cooldown", 2 * 24 * 60 /* specified in minutes */);
                    if (timeDiff <= maxTimeDiff) {
                        // latest build has already been fetched in the last x minutes (default: 2 days)
                        CompatUtil.LOGGER.info("Latest build for {} is already cached ({} hour(s) ago, refresh in {} hour(s))",
                                this.name, (long) Math.floor(timeDiff / 60d), (long) Math.ceil((maxTimeDiff - timeDiff) / 60d));
                        return CompletableFuture.completedFuture(Files.readString(versionCachePath).trim());
                    } else {
                        CompatUtil.LOGGER.info("Refreshing latest {} build (last refresh was {} hour(s) ago)...",
                                this.name, (long) Math.ceil(timeDiff / 60d));
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }

            CompatUtil.LOGGER.info("Fetching latest {} build...", this.name);
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
                        if (!version.startsWith(mcVersion + "+")
                                // 19w14b and before have this formatting
                                && !version.startsWith(mcVersion + ".")) {
                            continue;
                        }

                        Files.writeString(versionCachePath, version);
                        CompatUtil.LOGGER.info("Cached latest {} build version: {}", this.name, version);

                        return version;
                    }

                    throw new IllegalArgumentException("Can't find " + this.name + " mappings for minecraft version " + mcVersion);
                } catch (IOException exception) {
                    throw new RuntimeException("Can't parse response from " + this.metaUri + " for " + mcVersion, exception);
                }
            });
        }, executor);
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
