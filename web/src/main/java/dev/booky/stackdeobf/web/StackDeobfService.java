package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:02 06.07.23)

import dev.booky.stackdeobf.util.CompatUtil;
import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import org.apache.logging.log4j.LogManager;

import java.text.DecimalFormat;

public final class StackDeobfService {

    private final String bindAddress;
    private final int port;

    private Javalin javalin;
    private boolean running = true;

    public StackDeobfService(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public void start(long startTime) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop0(null), "Shutdown Handling Thread"));

        CompatUtil.LOGGER.info("Creating javalin service...");
        this.javalin = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.plugins.enableCors(cors -> cors.add(CorsPluginConfig::anyHost));
            config.plugins.enableRouteOverview("/api");
            config.staticFiles.add(files -> { /**/ });
        });

        CompatUtil.LOGGER.info("Configuring http routes...");
        ApiRoutes.register(this.javalin);

        CompatUtil.LOGGER.info("Launching javalin service on {}...", this.bindAddress);
        this.javalin.start(this.bindAddress, this.port);

        double bootTime = (System.currentTimeMillis() - startTime) / 1000d;
        String bootTimeStr = new DecimalFormat("#.##").format(bootTime);
        CompatUtil.LOGGER.info("Done ({}s)! To shutdown press CTRL+C", bootTimeStr);
    }

    public void stop(boolean cleanExit) {
        this.stop0(cleanExit);
    }

    private void stop0(Boolean cleanExit) {
        if (!this.running) {
            return;
        }
        this.running = false;

        CompatUtil.LOGGER.info("Closing javalin server...");
        if (this.javalin != null) {
            this.javalin.close();
        }

        CompatUtil.LOGGER.info("Shutting down... Goodbye (°_°)");
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
