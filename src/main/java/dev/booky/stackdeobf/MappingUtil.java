package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree.ClassMapping;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MappingUtil {

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
            LOGGER.info("Parsed mojang and intermediary mappings, ready for remapping");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void mapThrowable(Throwable throwable) {
        throwable.setStackTrace(mapStackTraceElements(throwable.getStackTrace()));

        if (throwable.getCause() != null) {
            mapThrowable(throwable.getCause());
        }

        for (Throwable suppressed : throwable.getSuppressed()) {
            mapThrowable(suppressed);
        }
    }

    public static StackTraceElement[] mapStackTraceElements(StackTraceElement[] elements) {
        return Arrays.stream(elements).map(MappingUtil::mapStackTraceElement).toArray(StackTraceElement[]::new);
    }

    public static StackTraceElement mapStackTraceElement(StackTraceElement element) {
        String classId = element.getClassName().replace('.', '/');

        // we look up using intermediary mappings (used in fabric production) and
        // using mojang mappings (some classes don't get obfuscated at all)
        ClassMapping intermediaryClassMapping = INTERMEDIARY.getClass(classId, 0);
        ClassMapping mojangClassMapping = MOJANG.getClass(classId, 0);

        // if the class is unknown to both, stop trying to do something
        if (intermediaryClassMapping == null && mojangClassMapping == null) {
            return element;
        }

        // look up by obfuscated naming
        if (intermediaryClassMapping == null) {
            intermediaryClassMapping = INTERMEDIARY.getClass(mojangClassMapping.getSrcName());
        } else if (mojangClassMapping == null) {
            mojangClassMapping = MOJANG.getClass(intermediaryClassMapping.getSrcName());
        }

        // some mappings are incomplete, cancel remapping
        if (intermediaryClassMapping == null || mojangClassMapping == null) {
            return element;
        }

        ClassMapping finalMojangClassMapping = mojangClassMapping;
        Set<String> mappedMethodNames = intermediaryClassMapping.getMethods().stream()
                // filtering with intermediary mapping name
                .filter(method -> element.getMethodName().equals(method.getDstName(0)))
                // mapping to mojang method mapping
                .map(method -> finalMojangClassMapping.getMethod(method.getSrcName(), method.getSrcDesc()))
                // mapping to mojang-mapped destination name
                .map(method -> method.getDstName(0))
                .collect(Collectors.toUnmodifiableSet());

        String className = mojangClassMapping.getDstName(0).replace('/', '.');
        String methodName = !mappedMethodNames.isEmpty()
                ? String.join("/", mappedMethodNames)
                : element.getMethodName();

        String fileName = element.getFileName();
        if (fileName != null) {
            String intermediaryName = intermediaryClassMapping.getDstName(0);
            if (intermediaryName.contains(fileName)) {
                fileName = mojangClassMapping.getDstName(0).replaceAll(".+/", "") + ".java";
            }
        }

        return new StackTraceElement(element.getClassLoaderName(), element.getModuleName(), element.getModuleVersion(),
                className, methodName, fileName, element.getLineNumber());
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

