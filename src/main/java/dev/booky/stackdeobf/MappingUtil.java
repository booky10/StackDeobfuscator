package dev.booky.stackdeobf;
// Created by booky10 in StackDeobfuscator (17:43 17.12.22)

import com.google.common.base.Preconditions;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraft.SharedConstants;

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

    private static final String MAPPING_HASH = System.getProperty("stackdeobf.mapping-hash", "42366909cc612e76208d34bf1356f05a88e08a1d");
    private static final URI MOJANG_URI = URI.create("https://piston-data.mojang.com/v1/objects/" + MAPPING_HASH + "/client.txt");

    private static final String INTERMEDIARY_VERSION = SharedConstants.getCurrentVersion().getName();
    private static final URI INTERMEDIARY_URI = URI.create("https://maven.fabricmc.net/net/fabricmc/intermediary/" + INTERMEDIARY_VERSION + "/intermediary-" + INTERMEDIARY_VERSION + "-v2.jar");

    private static final Path CACHE_DIR = FabricLoader.getInstance().getGameDir().resolve("stackdeobf_mappings");
    private static final Path MOJANG_PATH = CACHE_DIR.resolve("mojang_" + MAPPING_HASH + ".txt");
    private static final Path INTERMEDIARY_JAR_PATH = CACHE_DIR.resolve("intermediary_" + INTERMEDIARY_VERSION + ".jar");
    private static final Path INTERMEDIARY_PATH = CACHE_DIR.resolve("intermediary_" + INTERMEDIARY_VERSION + ".txt");

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final MemoryMappingTree MAPPINGS;

    static {
        if (Files.notExists(CACHE_DIR)) {
            try {
                Files.createDirectories(CACHE_DIR);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
        Preconditions.checkState(Files.isDirectory(CACHE_DIR), CACHE_DIR + " has to be a directory");

        cacheOrGet(MOJANG_PATH, MOJANG_URI);
        cacheOrGet(INTERMEDIARY_JAR_PATH, INTERMEDIARY_URI);

        if (Files.notExists(INTERMEDIARY_PATH)) {
            try (FileSystem jar = FileSystems.newFileSystem(INTERMEDIARY_JAR_PATH)) {
                Path mappingsPath = jar.getPath("mappings/mappings.tiny");
                Files.copy(mappingsPath, INTERMEDIARY_PATH);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }

        try {
            MemoryMappingTree mojang = prepMojangMappings();
            MemoryMappingTree intermediary = prepIntermediaryMappings();
            MAPPINGS = mergeMojangIntermediary(intermediary, mojang);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

//        mojang.accept(MappingWriter.create(CACHE_DIR.resolve("mojang.txt"), MappingFormat.TINY_2));
//        intermediary.accept(MappingWriter.create(CACHE_DIR.resolve("intermediary.txt"), MappingFormat.TINY_2));
//        merged.accept(MappingWriter.create(CACHE_DIR.resolve("merged.txt"), MappingFormat.TINY_2));
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
        String classLoaderName = element.getClassLoaderName();
        String moduleName = element.getModuleName();
        String moduleVersion = element.getModuleVersion();
        String declaringClass = element.getClassName();
        String methodName = element.getMethodName();
        String fileName = element.getFileName();
        int lineNumber = element.getLineNumber();

        MappingTree.ClassMapping declaringClassMapping = MAPPINGS.getClass(declaringClass);
        if (declaringClassMapping != null) {
            declaringClass = declaringClassMapping.getDstName(0);

            String originalMethodName = methodName;
            Set<String> mappedMethodNames = declaringClassMapping.getMethods().stream()
                    .filter(method -> originalMethodName.equals(method.getSrcName()))
                    .map(method -> method.getDstName(0))
                    .collect(Collectors.toUnmodifiableSet());

            if (!mappedMethodNames.isEmpty()) {
                methodName = String.join("/", mappedMethodNames);
            }
        }

        return new StackTraceElement(classLoaderName, moduleName, moduleVersion,
                declaringClass, methodName, fileName, lineNumber);
    }

    private static MemoryMappingTree prepMojangMappings() throws IOException {
        MemoryMappingTree moj2obf = new MemoryMappingTree();
        MappingReader.read(MOJANG_PATH, MappingFormat.PROGUARD, moj2obf);

        moj2obf.setSrcNamespace("named");
        moj2obf.setDstNamespaces(List.of("official"));

        MemoryMappingTree obf2moj = new MemoryMappingTree();
        moj2obf.accept(new MappingSourceNsSwitch(obf2moj, "official"));
        return obf2moj;
    }

    private static MemoryMappingTree prepIntermediaryMappings() throws IOException {
        MemoryMappingTree obf2inter = new MemoryMappingTree();
        MappingReader.read(INTERMEDIARY_PATH, MappingFormat.TINY_2, obf2inter);
        return obf2inter;
    }

    private static void cacheOrGet(Path path, URI uri) {
        if (!Files.isRegularFile(path)) {
            try {
                HTTP.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofFile(path));
            } catch (IOException | InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private static MemoryMappingTree mergeMojangIntermediary(MemoryMappingTree intermediary, MemoryMappingTree mojang) throws IOException {
        MemoryMappingTree mappings = new MemoryMappingTree();
        mappings.visitNamespaces("intermediary", List.of("named"));

        for (MappingTree.ClassMapping mojangClass : mojang.getClasses()) {
            MappingTree.ClassMapping intermediaryClass = intermediary.getClass(mojangClass.getSrcName());
            if (intermediaryClass == null) {
                continue;
            }

            mappings.visitClass(intermediaryClass.getDstName(0));
            for (MappingTree.FieldMapping mojangField : mojangClass.getFields()) {
                MappingTree.FieldMapping intermediaryField = intermediaryClass.getField(mojangField.getSrcName(), mojangField.getSrcDesc());
                if (intermediaryField == null) {
                    continue;
                }

                mappings.visitField(intermediaryField.getDstName(0), intermediaryField.getDstDesc(0));
                mappings.visitDstDesc(MappedElementKind.FIELD, 0, mojangField.getDstDesc(0));
                mappings.visitDstName(MappedElementKind.FIELD, 0, mojangField.getDstName(0));
                if (mojangField.getComment() != null) {
                    mappings.visitComment(MappedElementKind.FIELD, mojangField.getComment());
                }
            }

            for (MappingTree.MethodMapping mojangMethod : mojangClass.getMethods()) {
                MappingTree.MethodMapping intermediaryMethod = intermediaryClass.getMethod(mojangMethod.getSrcName(), mojangMethod.getSrcDesc());
                if (intermediaryMethod == null) {
                    continue;
                }

                mappings.visitMethod(intermediaryMethod.getDstName(0), intermediaryMethod.getDstDesc(0));
                for (MappingTree.MethodArgMapping mojangMethodArg : mojangMethod.getArgs()) {
                    MappingTree.MethodArgMapping intermediaryMethodArg = intermediaryMethod.getArg(mojangMethodArg.getArgPosition(), mojangMethodArg.getLvIndex(), mojangMethodArg.getSrcName());
                    mappings.visitMethodArg(intermediaryMethodArg.getArgPosition(), intermediaryMethodArg.getLvIndex(), intermediaryMethodArg.getDstName(0));

                    mappings.visitDstName(MappedElementKind.METHOD_ARG, 0, mojangMethodArg.getDstName(0));
                    if (mojangMethodArg.getComment() != null) {
                        mappings.visitComment(MappedElementKind.METHOD_ARG, mojangMethodArg.getComment());
                    }
                }

                for (MappingTree.MethodVarMapping mojangMethodVar : mojangMethod.getVars()) {
                    MappingTree.MethodVarMapping intermediaryMethodVar = intermediaryMethod.getVar(mojangMethodVar.getLvtRowIndex(), mojangMethodVar.getLvIndex(), mojangMethodVar.getStartOpIdx(), mojangMethodVar.getSrcName());
                    mappings.visitMethodVar(intermediaryMethodVar.getLvtRowIndex(), intermediaryMethodVar.getLvIndex(), intermediaryMethodVar.getStartOpIdx(), intermediaryMethodVar.getDstName(0));

                    mappings.visitDstName(MappedElementKind.METHOD_VAR, 0, mojangMethodVar.getDstName(0));
                    if (mojangMethodVar.getComment() != null) {
                        mappings.visitComment(MappedElementKind.METHOD_VAR, mojangMethodVar.getComment());
                    }
                }

                mappings.visitDstDesc(MappedElementKind.METHOD, 0, mojangMethod.getDstDesc(0));
                mappings.visitDstName(MappedElementKind.METHOD, 0, mojangMethod.getDstName(0));
                if (mojangMethod.getComment() != null) {
                    mappings.visitComment(MappedElementKind.METHOD, mojangMethod.getComment());
                }
            }

            mappings.visitDstName(MappedElementKind.CLASS, 0, mojangClass.getDstName(0));
            if (mojangClass.getComment() != null) {
                mappings.visitComment(MappedElementKind.CLASS, mojangClass.getComment());
            }
        }

        mappings.visitEnd();
        return mappings;
    }
}

