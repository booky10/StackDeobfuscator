package dev.booky.stackdeobf.config;
// Created by booky10 in StackDeobfuscator (17:15 23.03.23)

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import dev.booky.stackdeobf.mappings.providers.CustomMappingProvider;
import dev.booky.stackdeobf.mappings.providers.MojangMappingProvider;
import dev.booky.stackdeobf.mappings.providers.QuiltMappingProvider;
import dev.booky.stackdeobf.mappings.providers.YarnMappingProvider;
import dev.booky.stackdeobf.util.VersionData;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.mappingio.format.MappingFormat;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Locale;

public final class MappingProviderSerializer implements JsonSerializer<AbstractMappingProvider>, JsonDeserializer<AbstractMappingProvider> {

    private final VersionData versionData;

    public MappingProviderSerializer(VersionData versionData) {
        this.versionData = versionData;
    }

    @Override
    public AbstractMappingProvider deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            Path path = Path.of(obj.get("path").getAsString());
            MappingFormat format = ctx.deserialize(obj.get("mapping-format"), MappingFormat.class);
            return new CustomMappingProvider(this.versionData, path, format);
        }

        EnvType env = FabricLoader.getInstance().getEnvironmentType();
        String envName = env.name().toLowerCase(Locale.ROOT);

        String id = json.getAsString().trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "mojang" -> new MojangMappingProvider(this.versionData, envName);
            case "yarn" -> new YarnMappingProvider(this.versionData);
            case "quilt" -> new QuiltMappingProvider(this.versionData);
            default -> throw new JsonParseException("Invalid mappings id: " + id);
        };
    }

    @Override
    public JsonElement serialize(AbstractMappingProvider src, Type typeOfSrc, JsonSerializationContext context) {
        if (src instanceof MojangMappingProvider) {
            return new JsonPrimitive("mojang");
        }
        if (src instanceof YarnMappingProvider) {
            return new JsonPrimitive("yarn");
        }
        if (src instanceof QuiltMappingProvider) {
            return new JsonPrimitive("quilt");
        }
        if (src instanceof CustomMappingProvider custom) {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", custom.getPath().toString());
            obj.addProperty("mapping-format", custom.getFormat().name());
            return obj;
        }
        throw new UnsupportedOperationException("Unsupported mapping provider: " + src.getName());
    }
}
