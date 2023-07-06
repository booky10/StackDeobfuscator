package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:06 06.07.23)

import dev.booky.stackdeobf.http.HttpUtil;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.util.CompatUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.net.URI;
import java.util.Objects;

public final class ApiRoutes {

    private static final String PREFIX = "/api/v1";

    private ApiRoutes() {
    }

    public static void register(CachedMappings mappings, Javalin javalin) {
        javalin.get(PREFIX + "/deobf/url", ctx -> handleDeobfUrlReq(mappings, ctx));
        javalin.post(PREFIX + "/deobf/body", ctx -> handleDeobfReq(mappings, ctx));
    }

    private static void handleDeobfUrlReq(CachedMappings mappings, Context ctx) {
        URI uri = URI.create(Objects.requireNonNull(ctx.queryParam("url")));
        HttpUtil.getAsync(uri).handle((bytes, throwable) -> {
            if (throwable != null) {
                CompatUtil.LOGGER.error("Error while deobfuscating text from {}", uri, throwable);
                ctx.status(500);
            }

            if (bytes != null) {
                String str = new String(bytes);
                ctx.result(mappings.remapString(str));
            }

            // don't care about result
            return null;
        }).join();
    }

    private static void handleDeobfReq(CachedMappings mappings, Context ctx) {
        ctx.result(mappings.remapString(ctx.body()));
    }
}
