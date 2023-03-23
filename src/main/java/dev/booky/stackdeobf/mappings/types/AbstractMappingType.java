package dev.booky.stackdeobf.mappings.types;
// Created by booky10 in StackDeobfuscator (14:35 23.03.23)

import com.google.common.base.Preconditions;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.MappingVisitor;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractMappingType {

    protected static final String MC_VERSION = SharedConstants.getCurrentVersion().getId();
    protected static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String name;

    protected AbstractMappingType(String name) {
        this.name = name;
    }

    public void cacheMappings(MappingVisitor visitor) throws IOException {
        Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("stackdeobf_mappings");
        if (Files.notExists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }
        Preconditions.checkState(Files.isDirectory(cacheDir), cacheDir + " has to be a directory");

        this.downloadMappings(cacheDir);
        this.parseMappings();
        this.visitMappings(visitor);
    }

    public String getName() {
        return this.name;
    }

    protected abstract void downloadMappings(Path cacheDir) throws IOException;

    protected abstract void parseMappings() throws IOException;

    protected abstract void visitMappings(MappingVisitor visitor) throws IOException;

    protected void download(URI uri, Path path) {
        LOGGER.info("Downloading {} to {}...", uri, path);
        HTTP.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofFile(path)).join();
        LOGGER.info("  Finished");
    }
}
