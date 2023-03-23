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
import dev.booky.stackdeobf.mappings.types.AbstractMappingType;
import dev.booky.stackdeobf.mappings.types.CustomMappingType;
import dev.booky.stackdeobf.mappings.types.MojangMappingType;
import dev.booky.stackdeobf.mappings.types.QuiltMappingType;
import dev.booky.stackdeobf.mappings.types.YarnMappingType;
import net.fabricmc.mappingio.format.MappingFormat;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Locale;

public class MappingTypeSerializer implements JsonSerializer<AbstractMappingType>, JsonDeserializer<AbstractMappingType> {

    public static final MappingTypeSerializer INSTANCE = new MappingTypeSerializer();

    private MappingTypeSerializer() {
    }

    @Override
    public AbstractMappingType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            Path path = Path.of(obj.get("path").getAsString());
            MappingFormat format = ctx.deserialize(obj.get("mapping-format"), MappingFormat.class);
            return new CustomMappingType(path, format);
        }

        String id = json.getAsString().trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "mojang" -> new MojangMappingType();
            case "yarn" -> new YarnMappingType();
            case "quilt" -> new QuiltMappingType();
            default -> throw new JsonParseException("Invalid mappings id: " + id);
        };
    }

    @Override
    public JsonElement serialize(AbstractMappingType src, Type typeOfSrc, JsonSerializationContext context) {
        if (src instanceof MojangMappingType) {
            return new JsonPrimitive("mojang");
        }
        if (src instanceof YarnMappingType) {
            return new JsonPrimitive("yarn");
        }
        if (src instanceof QuiltMappingType) {
            return new JsonPrimitive("quilt");
        }
        if (src instanceof CustomMappingType custom) {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", custom.getPath().toString());
            obj.addProperty("mapping-format", custom.getFormat().name());
            return obj;
        }
        throw new UnsupportedOperationException("Unsupported mapping type: " + src.getName());
    }
}
