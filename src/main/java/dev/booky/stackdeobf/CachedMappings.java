package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:04 20.03.23)

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MappingTree.FieldMapping;
import net.fabricmc.mappingio.tree.MappingTree.MethodMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CachedMappings {

    private static final Int2ObjectMap<String> CLASSES = new Int2ObjectOpenHashMap<>(); // includes package
    private static final Int2ObjectMap<String> METHODS = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<String> FIELDS = new Int2ObjectOpenHashMap<>();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    private static final String MC_VERSION = SharedConstants.getCurrentVersion().getId();
    private static final URI MANIFEST_URI = URI.create(System.getProperty("stackdeobf.manifest-uri", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));

    private static final URI MOJANG_URI = Util.make(() -> {
        HttpResponse<String> manifestResp = HTTP.sendAsync(HttpRequest.newBuilder(MANIFEST_URI).build(), HttpResponse.BodyHandlers.ofString()).join();
        JsonObject manifestObj = GSON.fromJson(manifestResp.body(), JsonObject.class);

        for (JsonElement element : manifestObj.getAsJsonArray("versions")) {
            JsonObject elementObj = element.getAsJsonObject();
            if (!MC_VERSION.equals(elementObj.get("id").getAsString())) {
                continue;
            }

            URI infoUri = URI.create(elementObj.get("url").getAsString());
            HttpResponse<String> infoResp = HTTP.sendAsync(HttpRequest.newBuilder(infoUri).build(), HttpResponse.BodyHandlers.ofString()).join();
            JsonObject infoObj = GSON.fromJson(infoResp.body(), JsonObject.class);

            return URI.create(infoObj
                    .getAsJsonObject("downloads")
                    .getAsJsonObject("client_mappings")
                    .get("url").getAsString());
        }

        throw new IllegalStateException("Invalid minecraft version: " + MC_VERSION);
    });
    private static final URI INTERMEDIARY_URI = URI.create("https://maven.fabricmc.net/net/fabricmc/intermediary/" + MC_VERSION + "/intermediary-" + MC_VERSION + "-v2.jar");

    private static final Path CACHE_DIR = FabricLoader.getInstance().getGameDir().resolve("stackdeobf_mappings");
    private static final Path MOJANG_PATH = CACHE_DIR.resolve("mojang_" + MC_VERSION + ".txt");
    private static final Path INTERMEDIARY_JAR_PATH = CACHE_DIR.resolve("intermediary_" + MC_VERSION + ".jar");
    private static final Path INTERMEDIARY_PATH = CACHE_DIR.resolve("intermediary_" + MC_VERSION + ".txt");

    private static final MemoryMappingTree INTERMEDIARY, MOJANG;

    static {
        if (Files.notExists(CACHE_DIR)) {
            try {
                Files.createDirectories(CACHE_DIR);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
        Preconditions.checkState(Files.isDirectory(CACHE_DIR), CACHE_DIR + " has to be a directory");

        downloadIfMissing(MOJANG_PATH, MOJANG_URI);
        downloadIfMissing(INTERMEDIARY_JAR_PATH, INTERMEDIARY_URI);

        if (Files.notExists(INTERMEDIARY_PATH)) {
            try (FileSystem jar = FileSystems.newFileSystem(INTERMEDIARY_JAR_PATH)) {
                Path mappingsPath = jar.getPath("mappings/mappings.tiny");
                Files.copy(mappingsPath, INTERMEDIARY_PATH);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        try {
            INTERMEDIARY = parseIntermediaryMappings();
            MOJANG = parseMojangMappings();
            LOGGER.info("Parsed mojang and intermediary mappings");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        LOGGER.info("Caching class, method and field mappings");
        try {
            INTERMEDIARY.accept(new MappingVisitorAdapter() {
                private String srcClassName;
                private String srcMethodName, srcMethodDesc;
                private String srcFieldName, srcFieldDesc;

                @Override
                public boolean visitClass(String srcName) throws IOException {
                    this.srcClassName = srcName;
                    return super.visitClass(srcName);
                }

                @Override
                public boolean visitMethod(String srcName, String srcDesc) throws IOException {
                    this.srcMethodName = srcName;
                    this.srcMethodDesc = srcDesc;
                    return super.visitMethod(srcName, srcDesc);
                }

                @Override
                public boolean visitField(String srcName, String srcDesc) throws IOException {
                    this.srcFieldName = srcName;
                    this.srcFieldDesc = srcDesc;
                    return super.visitField(srcName, srcDesc);
                }

                @Override
                public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
                    switch (targetKind) {
                        case CLASS -> {
                            ClassMapping mapping = MOJANG.getClass(this.srcClassName);
                            if (mapping != null && name.startsWith("net/minecraft/class_")) {
                                try {
                                    int classId = Integer.parseInt(name.substring("net/minecraft/class_".length()));
                                    CLASSES.put(classId, mapping.getDstName(0).replace('/', '.'));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        case METHOD -> {
                            MethodMapping mapping = MOJANG.getMethod(this.srcClassName, this.srcMethodName, this.srcMethodDesc);
                            if (mapping != null && name.startsWith("method_")) {
                                int methodId = Integer.parseInt(name.substring("method_".length()));
                                METHODS.put(methodId, mapping.getDstName(0));
                            }
                        }
                        case FIELD -> {
                            FieldMapping mapping = MOJANG.getField(this.srcClassName, this.srcFieldName, this.srcFieldDesc);
                            if (mapping != null && name.startsWith("field_")) {
                                int fieldId = Integer.parseInt(name.substring("field_".length()));
                                FIELDS.put(fieldId, mapping.getDstName(0));
                            }
                        }
                    }
                    super.visitDstName(targetKind, namespace, name);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private CachedMappings() {
    }

    public static void init() {
        LOGGER.info("Cached mappings have been loaded:");
        LOGGER.info("  Classes: " + CLASSES.size());
        LOGGER.info("  Methods: " + METHODS.size());
        LOGGER.info("  Fields: " + FIELDS.size());
    }

    public static @Nullable String remapClass(int id) {
        return CLASSES.get(id);
    }

    public static @Nullable String remapMethod(int id) {
        return METHODS.get(id);
    }

    public static @Nullable String remapField(int id) {
        return FIELDS.get(id);
    }

    private static MemoryMappingTree parseMojangMappings() throws IOException {
        MemoryMappingTree moj2obf = new MemoryMappingTree();
        MappingReader.read(MOJANG_PATH, MappingFormat.PROGUARD, moj2obf);

        moj2obf.setSrcNamespace("named");
        moj2obf.setDstNamespaces(List.of("official"));

        MemoryMappingTree obf2moj = new MemoryMappingTree();
        moj2obf.accept(new MappingSourceNsSwitch(obf2moj, "official"));
        return obf2moj;
    }

    private static MemoryMappingTree parseIntermediaryMappings() throws IOException {
        MemoryMappingTree obf2inter = new MemoryMappingTree();
        MappingReader.read(INTERMEDIARY_PATH, MappingFormat.TINY_2, obf2inter);
        return obf2inter;
    }

    private static void downloadIfMissing(Path path, URI uri) {
        if (!Files.isRegularFile(path)) {
            try {
                LOGGER.info("Downloading {} to {}...", uri, path);
                HTTP.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofFile(path));
                LOGGER.info("  Finished");
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}
