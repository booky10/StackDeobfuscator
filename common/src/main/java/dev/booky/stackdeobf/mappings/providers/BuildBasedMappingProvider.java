package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (22:08 23.03.23)

import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.MavenArtifactInfo;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BuildBasedMappingProvider extends AbstractMappingProvider {

    private static final Pattern ID_COMMIT_HASH_PATTERN = Pattern.compile("^(.+) / [0-9a-f]{32}$");

    protected final MavenArtifactInfo artifactInfo;
    protected final VerifiableUrl.HashType hashType;

    protected Path path;
    protected MemoryMappingTree mappings;

    public BuildBasedMappingProvider(VersionData versionData, String name, MavenArtifactInfo artifactInfo, VerifiableUrl.HashType hashType) {
        super(versionData, name);
        this.artifactInfo = artifactInfo;
        this.hashType = hashType;
    }

    protected MappingFormat getMappingFormat() {
        return MappingFormat.TINY_2;
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        String version;
        // after 1.14.2, fabric switched to using the version id instead of the name for yarn versions
        if (this.versionData.getWorldVersion() >= 1963
                // WHAT THE FUCK DID HAPPEN HERE? HOW?
                // "Minecraft.Server / f7d695aa1ba843f2aa0cbc2ece6aea49"
                // HOW IS THIS A VERSION ID??? HOW DID THIS GET DEPLOYED ANYWHERE?
                && this.versionData.getWorldVersion() != 2836) {
            version = this.versionData.getId();

            // versions before 1.14.3-pre1 (and some combat tests) include the current
            // commit hash in the version.json id; just remove it using a regex everywhere
            Matcher matcher = ID_COMMIT_HASH_PATTERN.matcher(version);
            if (matcher.matches()) {
                version = matcher.group(1);
            }
        } else {
            version = this.versionData.getName();
        }

        // the first combat test used a very obscure "id", just replace it manually
        if ("1.14.3 - Combat Test".equals(version)) {
            version = "1.14_combat-212796";
        }

        // these just randomly have dots replaced with underscores, I don't want
        // to look into why, just do this manually for now
        //
        // 1_15_combat-1+build.1 also exists, but is completely gone from maven metadata?
        switch (version) {
            // @formatter:off // what are you doing?
            case "1.15_combat-6", "1.16_combat-0"
                    -> version = version.replace('.', '_');
            // @formatter:on
        }

        return this.fetchLatestVersion(cacheDir, version, executor)
                .thenCompose(build -> {
                    this.path = cacheDir.resolve(this.name + "_" + build + ".gz");

                    // already cached, don't download anything
                    if (Files.exists(this.path)) {
                        LOGGER.info("Mappings for {} build {} are already downloaded", this.name, build);
                        return CompletableFuture.completedFuture(null);
                    }

                    return this.artifactInfo.buildVerifiableUrl(build, "jar", this.hashType, executor)
                            .thenCompose(verifiableUrl -> {
                                LOGGER.info("Downloading {} mappings jar for build {}...", this.name, build);
                                return verifiableUrl.get(executor);
                            })
                            .thenAccept(mappingJarBytes -> {
                                byte[] mappingBytes = this.extractPackagedMappings(mappingJarBytes);
                                try (OutputStream fileOutput = Files.newOutputStream(this.path);
                                     GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                                    gzipOutput.write(mappingBytes);
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
                        LOGGER.info("Latest build for {} is already cached ({} hour(s) ago, refresh in {} hour(s))",
                                this.name, (long) Math.floor(timeDiff / 60d), (long) Math.ceil((maxTimeDiff - timeDiff) / 60d));
                        return CompletableFuture.completedFuture(Files.readString(versionCachePath).trim());
                    } else {
                        LOGGER.info("Refreshing latest {} build (last refresh was {} hour(s) ago)...",
                                this.name, (long) Math.ceil(timeDiff / 60d));
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }

            return this.artifactInfo.buildVerifiableMetaUrl(this.hashType, executor).thenCompose(verifiableUrl -> {
                LOGGER.info("Fetching latest {} build...", this.name);
                return verifiableUrl.get(executor).thenApply(resp -> {
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
                            if (!version.startsWith(mcVersion + "+")) {
                                // 19w14b and before have this formatting
                                if (!version.startsWith(mcVersion + ".")) {
                                    continue;
                                }

                                if (version.substring((mcVersion + ".").length()).indexOf('.') != -1) {
                                    // mcVersion is something like "1.19" and version is something like "1.19.4+build.1"
                                    // this prevents this being recognized as a valid mapping
                                    continue;
                                }
                            }

                            Files.writeString(versionCachePath, version);
                            LOGGER.info("Cached latest {} build version: {}", this.name, version);

                            return version;
                        }

                        throw new IllegalArgumentException("Can't find " + this.name + " mappings for minecraft version " + mcVersion);
                    } catch (IOException exception) {
                        throw new RuntimeException("Can't parse response from " + verifiableUrl.getUrl() + " for " + mcVersion, exception);
                    }
                });
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
                MappingReader.read(reader, this.getMappingFormat(), mappings);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

            // tiny v1 mappings format is official -> intermediary/named
            // this has to be switched, so it is intermediary -> official/named
            MemoryMappingTree reorderedMappings;
            if (!"intermediary".equals(mappings.getSrcNamespace())
                    && mappings.getDstNamespaces().contains("intermediary")) {
                try {
                    reorderedMappings = new MemoryMappingTree();
                    mappings.accept(new MappingSourceNsSwitch(reorderedMappings, "intermediary"));
                } catch (IOException exception) {
                    throw new RuntimeException("Error while switching namespaces", exception);
                }
            } else {
                reorderedMappings = mappings;
            }

            this.mappings = reorderedMappings;
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
