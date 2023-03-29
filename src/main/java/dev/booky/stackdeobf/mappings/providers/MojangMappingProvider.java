package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:57 23.03.23)

import com.google.common.base.Preconditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.booky.stackdeobf.compat.CompatUtil;
import dev.booky.stackdeobf.http.HttpUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraft.util.GsonHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MojangMappingProvider extends AbstractMappingProvider {

    private Path mojangPath, intermediaryPath;
    private MemoryMappingTree mojang, intermediary;

    public MojangMappingProvider() {
        super("mojang");
        Preconditions.checkState(CompatUtil.WORLD_VERSION >= 2203, "Mojang mappings are only provided by mojang starting from 19w36a");
    }

    @Override
    protected CompletableFuture<Void> downloadMappings0(Path cacheDir, Executor executor) {
        this.mojangPath = cacheDir.resolve("mojang_" + CompatUtil.VERSION_ID + ".txt");

        // only create futures if no mappings are locally cached
        List<CompletableFuture<?>> futures = new ArrayList<>(2);

        if (Files.notExists(this.mojangPath)) {
            futures.add(this.fetchMojangMappingsUri(CompatUtil.VERSION_ID, executor)
                    .thenCompose(uri -> HttpUtil.getAsync(uri, executor))
                    .thenAccept(mappingBytes -> {
                        try {
                            Files.write(this.mojangPath, mappingBytes);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    }));
        }

        // see comment in "parseMappings" method for why intermediary mappings are needed
        this.intermediaryPath = cacheDir.resolve("intermediary_" + CompatUtil.VERSION_ID + ".txt");
        if (Files.notExists(this.intermediaryPath)) {
            Path intermediaryJarPath = cacheDir.resolve("intermediary_" + CompatUtil.VERSION_ID + ".jar");
            URI intermediaryUri = URI.create("https://maven.fabricmc.net/net/fabricmc/intermediary/" +
                    CompatUtil.VERSION_ID + "/intermediary-" + CompatUtil.VERSION_ID + "-v2.jar");

            futures.add(HttpUtil.getAsync(intermediaryUri, executor).thenAccept(intermediaryJarBytes -> {
                try {
                    Files.write(intermediaryJarPath, intermediaryJarBytes);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                // extract the mappings file from the mappings jar
                try (FileSystem jar = FileSystems.newFileSystem(intermediaryJarPath)) {
                    Path mappingsPath = jar.getPath("mappings/mappings.tiny");
                    Files.copy(mappingsPath, this.intermediaryPath);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }

                // delete the jar, it is not needed anymore
                try {
                    Files.delete(intermediaryJarPath);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<URI> fetchMojangMappingsUri(String mcVersion, Executor executor) {
        URI manifestUri = URI.create(System.getProperty("stackdeobf.manifest-uri", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
        return HttpUtil.getAsync(manifestUri, executor).thenCompose(manifestResp -> {
            JsonObject manifestObj;
            try (ByteArrayInputStream input = new ByteArrayInputStream(manifestResp);
                 Reader reader = new InputStreamReader(input)) {
                manifestObj = GsonHelper.parse(reader);
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
                        infoObj = GsonHelper.parse(reader);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    EnvType env = FabricLoader.getInstance().getEnvironmentType();
                    String envName = env.name().toLowerCase(Locale.ROOT);

                    return URI.create(infoObj
                            .getAsJsonObject("downloads")
                            .getAsJsonObject(envName + "_mappings")
                            .get("url").getAsString());
                });
            }

            throw new IllegalStateException("Invalid minecraft version: " + mcVersion + " (not found in mojang version manifest)");
        });
    }

    @Override
    protected CompletableFuture<Void> parseMappings0(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // the production mappings need to be mapped back to their
                // obfuscated form, because mojang mappings are obfuscated -> named,
                // without the intermediary mappings inbetween

                this.mojang = this.parseMojangMappings();
                this.intermediary = this.parseIntermediaryMappings();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            return null;
        }, executor);
    }

    private MemoryMappingTree parseMojangMappings() throws IOException {
        MemoryMappingTree moj2obf = new MemoryMappingTree();
        MappingReader.read(this.mojangPath, MappingFormat.PROGUARD, moj2obf);

        moj2obf.setSrcNamespace("named");
        moj2obf.setDstNamespaces(List.of("official"));

        MemoryMappingTree obf2moj = new MemoryMappingTree();
        moj2obf.accept(new MappingSourceNsSwitch(obf2moj, "official"));
        return obf2moj;
    }

    private MemoryMappingTree parseIntermediaryMappings() throws IOException {
        MemoryMappingTree obf2inter = new MemoryMappingTree();
        MappingReader.read(this.intermediaryPath, MappingFormat.TINY_2, obf2inter);
        return obf2inter;
    }

    @Override
    protected CompletableFuture<Void> visitMappings0(MappingVisitor visitor, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // the source names need to be mapped to intermediary, because
                // the specified visitor expects to receive intermediary source names
                this.mojang.accept(new Visitor(visitor));
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
            this.clazz = MojangMappingProvider.this.intermediary.getClass(srcName);
            if (this.clazz != null) {
                return super.visitClass(this.clazz.getDstName(0));
            }
            return false;
        }

        @Override
        public boolean visitMethod(String srcName, String srcDesc) throws IOException {
            MappingTree.MethodMapping mapping = this.clazz.getMethod(srcName, srcDesc);
            if (mapping != null) {
                return super.visitMethod(mapping.getDstName(0), mapping.getDstDesc(0));
            }
            return false;
        }

        @Override
        public boolean visitField(String srcName, String srcDesc) throws IOException {
            MappingTree.FieldMapping mapping = this.clazz.getField(srcName, srcDesc);
            if (mapping != null) {
                return super.visitField(mapping.getDstName(0), mapping.getDstDesc(0));
            }
            return false;
        }
    }
}
