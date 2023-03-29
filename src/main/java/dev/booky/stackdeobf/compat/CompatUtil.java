package dev.booky.stackdeobf.compat;
// Created by booky10 in StackDeobfuscator (21:20 29.03.23)

import com.google.gson.JsonObject;
import dev.booky.stackdeobf.StackDeobfMod;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.util.GsonHelper;
import org.apache.logging.log4j.LogManager;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;

public final class CompatUtil {

    static {
        try {
            // mixin plugin is loaded before the version info is loaded,
            // load it manually
            SharedConstants.tryDetectVersion();
        } catch (NoSuchMethodError ignored) {
            // doesn't exist in old versions
        }
    }

    public static final String VERSION_ID = Util.make(() -> {
        try {
            return SharedConstants.getCurrentVersion().getId();
        } catch (NoSuchMethodError error) {
            // version older than 1.19.4, manually read id from version.json
            return getVersionJson().get("id").getAsString();
        }
    });

    public static final String VERSION_NAME = Util.make(() -> {
        try {
            return SharedConstants.getCurrentVersion().getName();
        } catch (NoSuchMethodError error) {
            // version older than 1.19.4, manually read id from version.json
            return getVersionJson().get("name").getAsString();
        }
    });

    public static final int WORLD_VERSION = Util.make(() -> {
        try {
            return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        } catch (NoSuchMethodError error) {
            // version older than 1.19.4, manually read id from version.json
            return getVersionJson().get("world_version").getAsInt();
        }
    });

    public static final ILogger LOGGER = Util.make(() -> {
        try {
            // versions below 22w03a don't have mojangs logging library and therefore no slf4j
            Class.forName("org.slf4j.Logger");
            return new Slf4jLogger(LoggerFactory.getLogger(StackDeobfMod.class));
        } catch (ClassNotFoundException exception) {
            return new Log4jLogger(LogManager.getLogger(StackDeobfMod.class));
        }
    });

    private CompatUtil() {
    }

    private static JsonObject getVersionJson() {
        try (InputStream input = DetectedVersion.class.getResourceAsStream("/version.json");
             Reader reader = new InputStreamReader(Objects.requireNonNull(input, "version.json not found"))) {
            return GsonHelper.parse(reader);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
