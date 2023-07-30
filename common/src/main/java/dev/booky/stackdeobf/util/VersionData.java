package dev.booky.stackdeobf.util;
// Created by booky10 in StackDeobfuscator (19:41 06.07.23)

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class VersionData {

    private static final Gson GSON = new Gson();

    private final String id;
    private final String name;
    private final int worldVersion;
    private final int protocolVersion;
    private final OffsetDateTime buildTime;

    private VersionData(String id, String name, int worldVersion, int protocolVersion, OffsetDateTime buildTime) {
        this.id = id;
        this.name = name;
        this.worldVersion = worldVersion;
        this.protocolVersion = protocolVersion;
        this.buildTime = buildTime;
    }

    public static VersionData fromClasspath() {
        JsonObject object;
        try (InputStream input = VersionData.class.getResourceAsStream("/version.json");
             Reader reader = new InputStreamReader(Objects.requireNonNull(input, "version.json not found"))) {
            object = GSON.fromJson(reader, JsonObject.class);
        } catch (Throwable throwable) {
            throw new RuntimeException("Error while reading version data from classpath", throwable);
        }
        return fromJson(object);
    }

    public static VersionData fromJson(Path path) throws IOException {
        JsonObject object;
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            object = GSON.fromJson(reader, JsonObject.class);
        }
        return fromJson(object);
    }

    public static VersionData fromJson(JsonObject object) {
        String id = object.get("id").getAsString();
        String name = object.get("name").getAsString();
        int worldVersion = object.get("world_version").getAsInt();
        int protocolVersion = object.get("protocol_version").getAsInt();

        String buildTimeStr = object.get("build_time").getAsString();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        OffsetDateTime buildTime = OffsetDateTime.parse(buildTimeStr, formatter);

        return new VersionData(id, name, worldVersion, protocolVersion, buildTime);
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getWorldVersion() {
        return this.worldVersion;
    }

    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    public OffsetDateTime getBuildTime() {
        return this.buildTime;
    }

    @Override
    public String toString() {
        return "VersionData{id='" + this.id + '\'' + ", name='" + this.name + '\'' + ", worldVersion=" + this.worldVersion + ", protocolVersion=" + this.protocolVersion + ", buildTime=" + this.buildTime + '}';
    }
}
