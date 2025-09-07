package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:57 23.03.23)

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.booky.stackdeobf.http.VerifiableUrl;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static dev.booky.stackdeobf.util.VersionConstants.V19W36A;
import static dev.booky.stackdeobf.util.VersionConstants.V1_14_4;
import static dev.booky.stackdeobf.util.VersionConstants.V21W39A;

public class MojangMappingProvider extends AbstractMappingProvider {

    // taken from https://github.com/PrismLauncher/meta/blob/63194d47e829a8c072e5a40dac37863a21d1b11d/static/mojang/minecraft-experiments.json
    private static final Map<String, VerifiableUrl> STATIC_MINECRAFT_EXPERIMENTS = Map.ofEntries(
            Map.entry("1.19_deep_dark_experimental_snapshot-1", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/b1e589c1d6ed73519797214bc796e53f5429ac46/1_19_deep_dark_experimental_snapshot-1.zip"), VerifiableUrl.HashType.SHA1, "b1e589c1d6ed73519797214bc796e53f5429ac46")),
            Map.entry("1.18_experimental-snapshot-7", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/ab4ecebb133f56dd4c4c4c3257f030a947ddea84/1_18_experimental-snapshot-7.zip"), VerifiableUrl.HashType.SHA1, "ab4ecebb133f56dd4c4c4c3257f030a947ddea84")),
            Map.entry("1.18_experimental-snapshot-6", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/4697c84c6a347d0b8766759d5b00bc5a00b1b858/1_18_experimental-snapshot-6.zip"), VerifiableUrl.HashType.SHA1, "4697c84c6a347d0b8766759d5b00bc5a00b1b858")),
            Map.entry("1.18_experimental-snapshot-5", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/d9cb7f6fb4e440862adfb40a385d83e3f8d154db/1_18_experimental-snapshot-5.zip"), VerifiableUrl.HashType.SHA1, "d9cb7f6fb4e440862adfb40a385d83e3f8d154db")),
            Map.entry("1.18_experimental-snapshot-4", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/b92a360cbae2eb896a62964ad8c06c3493b6c390/1_18_experimental-snapshot-4.zip"), VerifiableUrl.HashType.SHA1, "b92a360cbae2eb896a62964ad8c06c3493b6c390")),
            Map.entry("1.18_experimental-snapshot-3", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/846648ff9fe60310d584061261de43010e5c722b/1_18_experimental-snapshot-3.zip"), VerifiableUrl.HashType.SHA1, "846648ff9fe60310d584061261de43010e5c722b")),
            Map.entry("1.18_experimental-snapshot-2", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/0adfe4f321aa45248fc88ac888bed5556633e7fb/1_18_experimental-snapshot-2.zip"), VerifiableUrl.HashType.SHA1, "0adfe4f321aa45248fc88ac888bed5556633e7fb")),
            Map.entry("1.18_experimental-snapshot-1", new VerifiableUrl(URI.create("https://launcher.mojang.com/v1/objects/231bba2a21e18b8c60976e1f6110c053b7b93226/1_18_experimental-snapshot-1.zip"), VerifiableUrl.HashType.SHA1, "231bba2a21e18b8c60976e1f6110c053b7b93226")),
            Map.entry("1.16_combat-3", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/2557b99d95588505e988886220779087d7d6b1e9/1_16_combat-3.zip"), VerifiableUrl.HashType.SHA1, "2557b99d95588505e988886220779087d7d6b1e9")),
            Map.entry("1.16_combat-0", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/5a8ceec8681ed96ab6ecb9607fb5d19c8a755559/1_16_combat-0.zip"), VerifiableUrl.HashType.SHA1, "5a8ceec8681ed96ab6ecb9607fb5d19c8a755559")),
            Map.entry("1.15_combat-6", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/52263d42a626b40c947e523128f7a195ec5af76a/1_15_combat-6.zip"), VerifiableUrl.HashType.SHA1, "52263d42a626b40c947e523128f7a195ec5af76a")),
            Map.entry("1.15_combat-1", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60/1_15_combat-1.zip"), VerifiableUrl.HashType.SHA1, "ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60")),
            Map.entry("1.14_combat-3", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/0f209c9c84b81c7d4c88b4632155b9ae550beb89/1_14_combat-3.zip"), VerifiableUrl.HashType.SHA1, "0f209c9c84b81c7d4c88b4632155b9ae550beb89")),
            Map.entry("1.14_combat-0", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/d164bb6ecc5fca9ac02878c85f11befae61ac1ca/1_14_combat-0.zip"), VerifiableUrl.HashType.SHA1, "d164bb6ecc5fca9ac02878c85f11befae61ac1ca")),
            Map.entry("1.14.3 - Combat Test / 5d5e0be06e714f03bba436c42db4c85b", new VerifiableUrl(URI.create("https://launcher.mojang.com/experiments/combat/610f5c9874ba8926d5ae1bcce647e5f0e6e7c889/1_14_combat-212796.zip"), VerifiableUrl.HashType.SHA1, "610f5c9874ba8926d5ae1bcce647e5f0e6e7c889")));

    private static final String LICENSE = """
            (c) 2020 Microsoft Corporation. These mappings are provided "as-is" and you bear the risk of using them.
            You may copy and use the mappings for development purposes, but you may not redistribute the mappings complete and unmodified.
            Microsoft makes no warranties, express or implied, with respect to the mappings provided here.
            Use and modification of this document or the source code (in any form) of Minecraft: Java Edition is governed by
            the Minecraft End User License Agreement available at https://account.mojang.com/documents/minecraft_eula.
            """;
    private static final Gson GSON = new Gson();

    // mojang mappings are split between client and server
    private final String environment;

    // the production/intermediary mappings need to be mapped back to their
    // obfuscated form, because mojang mappings are obfuscated -> named,
    // without the intermediary mappings inbetween
    private final IntermediaryMappingProvider intermediary;

    private Path path;
    private MemoryMappingTree mappings;

    public MojangMappingProvider(VersionData versionData, String environment) {
        super(versionData, "mojang");

        if (versionData.getWorldVersion() < V19W36A && versionData.getWorldVersion() != V1_14_4) {
            throw new IllegalStateException("Mojang mappings are only provided by mojang starting from 19w36a (excluding 1.14.4)");
        }

        this.environment = environment.toLowerCase(Locale.ROOT);
        this.intermediary = new IntermediaryMappingProvider(versionData);

        LOGGER.warn("By enabling mojang mappings, you agree to their license:");
        for (String line : StringUtils.split(LICENSE, '\n')) {
            LOGGER.warn(line);
        }
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        String version;
        // see comment at AbstractMappingProvider#getFabricatedVersion
        if (this.versionData.getWorldVersion() == V21W39A) {
            version = this.versionData.getName();
        } else {
            version = this.versionData.getId();
        }

        CompletableFuture<Void> intermediaryFuture = this.intermediary.downloadMappings0(cacheDir, executor);
        this.path = cacheDir.resolve("mojang_" + version + ".gz");
        if (Files.exists(this.path)) {
            return intermediaryFuture;
        }

        return intermediaryFuture.thenCompose($ -> this.fetchMojangMappingsUri(version, executor)
                .thenCompose(verifiableUrl -> verifiableUrl.get(executor))
                .thenAccept(resp -> {
                    try (OutputStream fileOutput = Files.newOutputStream(this.path);
                         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                        gzipOutput.write(resp.getBody());
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }));
    }

    private CompletableFuture<VerifiableUrl> fetchMojangMappingsUri(String mcVersion, Executor executor) {
        // some versions are not present in official version manifest, so use these hard-coded links
        VerifiableUrl staticUrl = STATIC_MINECRAFT_EXPERIMENTS.get(mcVersion);
        if (staticUrl != null) {
            LOGGER.warn("Static url found for {}, using {} for downloading", mcVersion, staticUrl.getUrl());
            return staticUrl.get(executor).thenApply(resp -> {
                JsonObject infoObj = null;
                try (ByteArrayInputStream input = new ByteArrayInputStream(resp.getBody());
                     ZipInputStream zipInput = new ZipInputStream(input)) {
                    ZipEntry entry;
                    while ((entry = zipInput.getNextEntry()) != null) {
                        if (!entry.getName().endsWith(".json")) {
                            continue;
                        }

                        try (Reader reader = new InputStreamReader(zipInput)) {
                            infoObj = GSON.fromJson(reader, JsonObject.class);
                        }
                        break;
                    }
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                if (infoObj == null) {
                    throw new IllegalStateException("No version metadata found in static profile for " + mcVersion);
                }

                JsonObject mappingsData = infoObj
                        .getAsJsonObject("downloads")
                        .getAsJsonObject(this.environment + "_mappings");
                URI mappingsUrl = URI.create(mappingsData.get("url").getAsString());
                String mappingsSha1 = mappingsData.get("sha1").getAsString();
                return new VerifiableUrl(mappingsUrl, VerifiableUrl.HashType.SHA1, mappingsSha1);
            });
        }

        URI manifestUri = URI.create(System.getProperty("stackdeobf.manifest-uri",
                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));

        // the server sends a "content-md5" header containing a md5 hash encoded as a base64-string,
        // but only if the http method is "HEAD"
        return VerifiableUrl.resolveByMd5Header(manifestUri, executor)
                .thenCompose(verifiableUrl -> verifiableUrl.get(executor))
                .thenCompose(manifestResp -> {
                    JsonObject manifestObj;
                    try (ByteArrayInputStream input = new ByteArrayInputStream(manifestResp.getBody());
                         Reader reader = new InputStreamReader(input)) {
                        manifestObj = GSON.fromJson(reader, JsonObject.class);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    for (JsonElement element : manifestObj.getAsJsonArray("versions")) {
                        JsonObject elementObj = element.getAsJsonObject();
                        if (!mcVersion.equals(elementObj.get("id").getAsString())) {
                            continue;
                        }

                        URI infoUrl = URI.create(elementObj.get("url").getAsString());
                        String infoSha1 = elementObj.get("sha1").getAsString();
                        VerifiableUrl verifiableInfoUrl = new VerifiableUrl(infoUrl, VerifiableUrl.HashType.SHA1, infoSha1);

                        return verifiableInfoUrl.get(executor).thenApply(infoResp -> {
                            JsonObject infoObj;
                            try (ByteArrayInputStream input = new ByteArrayInputStream(infoResp.getBody());
                                 Reader reader = new InputStreamReader(input)) {
                                infoObj = GSON.fromJson(reader, JsonObject.class);
                            } catch (IOException exception) {
                                throw new RuntimeException(exception);
                            }

                            JsonObject mappingsData = infoObj
                                    .getAsJsonObject("downloads")
                                    .getAsJsonObject(this.environment + "_mappings");
                            URI mappingsUrl = URI.create(mappingsData.get("url").getAsString());
                            String mappingsSha1 = mappingsData.get("sha1").getAsString();
                            return new VerifiableUrl(mappingsUrl, VerifiableUrl.HashType.SHA1, mappingsSha1);
                        });
                    }

                    throw new IllegalStateException("Invalid minecraft version: " + mcVersion
                            + " (not found in mojang version manifest)");
                });
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return this.intermediary.parseMappings0(executor).thenRun(() -> {
            try {
                MemoryMappingTree rawMappings = new MemoryMappingTree();

                try (InputStream fileInput = Files.newInputStream(this.path);
                     GZIPInputStream gzipInput = new GZIPInputStream(fileInput);
                     Reader reader = new InputStreamReader(gzipInput)) {
                    MappingReader.read(reader, MappingFormat.PROGUARD_FILE, rawMappings);
                }

                rawMappings.setSrcNamespace("named");
                rawMappings.setDstNamespaces(List.of("official"));

                // mappings provided by mojang are named -> obfuscated
                // this needs to be switched for the remapping to work properly

                MemoryMappingTree switchedMappings = new MemoryMappingTree();
                rawMappings.accept(new MappingSourceNsSwitch(switchedMappings, "official"));
                this.mappings = switchedMappings;
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // the source names need to be mapped to intermediary, because
                // the specified visitor expects to receive intermediary source names
                this.mappings.accept(new Visitor(visitor));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    private final class Visitor extends ForwardingMappingVisitor {

        private MappingTree.ClassMapping clazz;

        private Visitor(MappingVisitor next) {
            super(next);
        }

        @Override
        public boolean visitClass(String srcName) throws IOException {
            this.clazz = MojangMappingProvider.this.intermediary.getMappings().getClass(srcName);
            if (this.clazz == null) {
                return false;
            }
            return super.visitClass(this.clazz.getDstName(0));
        }

        @Override
        public boolean visitMethod(String srcName, String srcDesc) throws IOException {
            MappingTree.MethodMapping mapping = this.clazz.getMethod(srcName, srcDesc);
            if (mapping == null) {
                return false;
            }
            return super.visitMethod(mapping.getDstName(0), mapping.getDstDesc(0));
        }

        @Override
        public boolean visitField(String srcName, String srcDesc) throws IOException {
            MappingTree.FieldMapping mapping = this.clazz.getField(srcName, srcDesc);
            if (mapping == null) {
                return false;
            }
            return super.visitField(mapping.getDstName(0), mapping.getDstDesc(0));
        }
    }
}
