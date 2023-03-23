package dev.booky.stackdeobf.mappings.providers;
// Created by booky10 in StackDeobfuscator (16:57 23.03.23)

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class MojangMappingProvider extends AbstractMappingProvider {

    private Path mojangPath, intermediaryPath;
    private MemoryMappingTree mojang, intermediary;

    public MojangMappingProvider() {
        super("mojang");
    }

    @Override
    protected void downloadMappings(Path cacheDir) throws IOException {
        this.mojangPath = cacheDir.resolve("mojang_" + MC_VERSION + ".txt");
        if (Files.notExists(this.mojangPath)) {
            URI mojangUri = this.fetchMojangMappingsUri();
            this.download(mojangUri, this.mojangPath);
        }

        // see comment in parseMappings(Path) for why intermediary mappings are needed
        this.intermediaryPath = cacheDir.resolve("intermediary_" + MC_VERSION + ".txt");
        if (Files.notExists(this.intermediaryPath)) {
            Path intermediaryJarPath = cacheDir.resolve("intermediary_" + MC_VERSION + ".jar");
            if (Files.notExists(intermediaryJarPath)) {
                URI intermediaryUri = URI.create("https://maven.fabricmc.net/net/fabricmc/intermediary/" + MC_VERSION + "/intermediary-" + MC_VERSION + "-v2.jar");
                this.download(intermediaryUri, intermediaryJarPath);
            }

            // intermediary mappings are inside a jar file, they need to be extracted
            try (FileSystem jar = FileSystems.newFileSystem(intermediaryJarPath)) {
                Path mappingsPath = jar.getPath("mappings/mappings.tiny");
                Files.copy(mappingsPath, this.intermediaryPath);
            }
        }
    }

    private URI fetchMojangMappingsUri() {
        URI manifestUri = URI.create(System.getProperty("stackdeobf.manifest-uri", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));

        Gson gson = new Gson();
        HttpResponse<String> manifestResp = HTTP.sendAsync(HttpRequest.newBuilder(manifestUri).build(), HttpResponse.BodyHandlers.ofString()).join();
        JsonObject manifestObj = gson.fromJson(manifestResp.body(), JsonObject.class);

        for (JsonElement element : manifestObj.getAsJsonArray("versions")) {
            JsonObject elementObj = element.getAsJsonObject();
            if (!MC_VERSION.equals(elementObj.get("id").getAsString())) {
                continue;
            }

            URI infoUri = URI.create(elementObj.get("url").getAsString());
            HttpResponse<String> infoResp = HTTP.sendAsync(HttpRequest.newBuilder(infoUri).build(), HttpResponse.BodyHandlers.ofString()).join();
            JsonObject infoObj = gson.fromJson(infoResp.body(), JsonObject.class);

            EnvType env = FabricLoader.getInstance().getEnvironmentType();
            String envName = env.name().toLowerCase(Locale.ROOT);

            return URI.create(infoObj
                    .getAsJsonObject("downloads")
                    .getAsJsonObject(envName + "_mappings")
                    .get("url").getAsString());
        }

        throw new IllegalStateException("Invalid minecraft version: " + MC_VERSION + " (not found in mojang version manifest)");
    }

    @Override
    protected void parseMappings() throws IOException {
        // the production mappings need to be mapped back to their
        // obfuscated form, because mojang mappings are obfuscated -> named,
        // without the intermediary mappings inbetween

        this.mojang = this.parseMojangMappings();
        this.intermediary = this.parseIntermediaryMappings();
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
    protected void visitMappings(MappingVisitor visitor) throws IOException {
        // the source names need to be mapped to intermediary, because
        // the specified visitor expects to receive intermediary source names
        this.mojang.accept(new ForwardingMappingVisitor(visitor) {
            private MappingTree.ClassMapping clazz;

            @Override
            public boolean visitClass(String srcName) throws IOException {
                this.clazz = intermediary.getClass(srcName);
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
        });
    }
}
