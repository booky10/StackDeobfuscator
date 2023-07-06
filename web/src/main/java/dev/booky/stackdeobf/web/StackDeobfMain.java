package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (16:34 06.07.23)

import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import dev.booky.stackdeobf.mappings.providers.MojangMappingProvider;
import dev.booky.stackdeobf.mappings.providers.QuiltMappingProvider;
import dev.booky.stackdeobf.mappings.providers.YarnMappingProvider;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.io.IoBuilder;

import java.nio.file.Path;

public final class StackDeobfMain {

    private static final String HTTP_BIND = System.getProperty("web.bind", "localhost");
    private static final int HTTP_PORT = Integer.getInteger("web.port", 8080);

    private static final Path CACHE_DIR = Path.of(System.getProperty("mappings.cachedir", "mappings"));
    private static final String PROVIDER_STR = System.getProperty("mappings.provider", "yarn");

    static {
        System.setProperty("java.awt.headless", "true");
        System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream());
        System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream());
    }

    private StackDeobfMain() {
    }

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        Thread.currentThread().setName("Startup Thread");

        AbstractMappingProvider provider = switch (PROVIDER_STR) {
            case "mojang" -> new MojangMappingProvider("client");
            case "yarn" -> new YarnMappingProvider();
            case "quilt" -> new QuiltMappingProvider();
            default -> throw new IllegalArgumentException("Invalid mappings id: " + PROVIDER_STR);
        };
        CachedMappings mappings = CachedMappings.create(CACHE_DIR.toAbsolutePath(), provider).join();

        StackDeobfService service = new StackDeobfService(mappings, HTTP_BIND, HTTP_PORT);
        service.start(startTime);
    }
}
