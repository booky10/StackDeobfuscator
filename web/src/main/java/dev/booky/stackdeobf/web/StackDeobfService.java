package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:02 06.07.23)

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.booky.stackdeobf.util.VersionData;
import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig.CorsRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StackDeobfService {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");

    private final String bindAddress;
    private final int port;

    private Javalin javalin;
    private boolean running = true;

    public StackDeobfService(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    private static Map<Integer, VersionData> parseVersionData() throws IOException {
        JsonArray array;
        try (InputStream input = StackDeobfService.class.getResourceAsStream("/public/mc_versions.json")) {
            Objects.requireNonNull(input, "No minecraft version data file found in classpath");
            try (InputStreamReader reader = new InputStreamReader(input)) {
                array = new Gson().fromJson(reader, JsonArray.class);
            }
        }

        Map<Integer, VersionData> versionDataMap = new HashMap<>();
        for (JsonElement element : array) {
            VersionData versionData = VersionData.fromJson(element.getAsJsonObject());
            int key = versionData.getWorldVersion();
            versionDataMap.put(key, versionData);
        }
        return Collections.unmodifiableMap(versionDataMap);
    }

    public void start(long startTime) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop0(null), "Shutdown Handling Thread"));

        LOGGER.info("Parsing minecraft version data...");
        Map<Integer, VersionData> versionData;
        try {
            versionData = parseVersionData();
        } catch (IOException exception) {
            throw new RuntimeException("Error while trying to parse minecraft version data", exception);
        }

        LOGGER.info("Creating javalin service...");
        this.javalin = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.bundledPlugins.enableCors(cors -> cors.addRule(CorsRule::anyHost));
            config.bundledPlugins.enableRouteOverview("/api");
            config.staticFiles.add(files -> { /**/ });
        });

        LOGGER.info("Configuring http routes...");
        ApiRoutes.register(this.javalin, versionData);

        LOGGER.info("Launching javalin service on {}...", this.bindAddress);
        this.javalin.start(this.bindAddress, this.port);

        double bootTime = (System.currentTimeMillis() - startTime) / 1000d;
        String bootTimeStr = new DecimalFormat("#.##").format(bootTime);
        LOGGER.info("Done ({}s)! To shutdown press CTRL+C", bootTimeStr);
    }

    public void stop(boolean cleanExit) {
        this.stop0(cleanExit);
    }

    private void stop0(Boolean cleanExit) {
        if (!this.running) {
            return;
        }
        this.running = false;

        LOGGER.info("Closing javalin server...");
        if (this.javalin != null) {
            this.javalin.stop();
        }

        LOGGER.info("Shutting down... Goodbye (°_°)");
        LogManager.shutdown();

        if (cleanExit != null) {
            System.exit(cleanExit ? 0 : 1);
        }
    }

    public String getBindAddress() {
        return this.bindAddress;
    }

    public int getPort() {
        return this.port;
    }

    public boolean isRunning() {
        return this.running;
    }
}
