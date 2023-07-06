package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:57 23.03.23)

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.booky.stackdeobf.http.HttpUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MojangMappingProvider extends AbstractMappingProvider {

    private static final String LICENSE = """
            (c) 2020 Microsoft Corporation.These mappings are provided "as-is" and you bear the risk of using them.
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
        Preconditions.checkState(versionData.getWorldVersion() >= 2203 || versionData.getWorldVersion() == 1976,
                "Mojang mappings are only provided by mojang starting from 19w36a (excluding 1.14.4)");
        this.environment = environment;
        this.intermediary = new IntermediaryMappingProvider(versionData);

        LOGGER.warn("By enabling mojang mappings, you agree to their license:");
        for (String line : StringUtils.split(LICENSE, '\n')) {
            LOGGER.warn(line);
        }
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        CompletableFuture<Void> intermediaryFuture = this.intermediary.downloadMappings0(cacheDir, executor);

        this.path = cacheDir.resolve("mojang_" + this.versionData.getId() + ".gz");
        if (Files.exists(this.path)) {
            return intermediaryFuture;
        }

        return intermediaryFuture.thenCompose($ -> this.fetchMojangMappingsUri(this.versionData.getId(), executor)
                .thenCompose(uri -> HttpUtil.getAsync(uri, executor))
                .thenAccept(mappingBytes -> {
                    try (OutputStream fileOutput = Files.newOutputStream(this.path);
                         GZIPOutputStream gzipOutput = new GZIPOutputStream(fileOutput)) {
                        gzipOutput.write(mappingBytes);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }));
    }

    private CompletableFuture<URI> fetchMojangMappingsUri(String mcVersion, Executor executor) {
        URI manifestUri = URI.create(System.getProperty("stackdeobf.manifest-uri", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
        return HttpUtil.getAsync(manifestUri, executor).thenCompose(manifestResp -> {
            JsonObject manifestObj;
            try (ByteArrayInputStream input = new ByteArrayInputStream(manifestResp);
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

                URI infoUri = URI.create(elementObj.get("url").getAsString());
                return HttpUtil.getAsync(infoUri, executor).thenApply(infoResp -> {
                    JsonObject infoObj;
                    try (ByteArrayInputStream input = new ByteArrayInputStream(infoResp);
                         Reader reader = new InputStreamReader(input)) {
                        infoObj = GSON.fromJson(reader, JsonObject.class);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    return URI.create(infoObj
                            .getAsJsonObject("downloads")
                            .getAsJsonObject(this.environment + "_mappings")
                            .get("url").getAsString());
                });
            }

            throw new IllegalStateException("Invalid minecraft version: " + mcVersion + " (not found in mojang version manifest)");
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
                    MappingReader.read(reader, MappingFormat.PROGUARD, rawMappings);
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
