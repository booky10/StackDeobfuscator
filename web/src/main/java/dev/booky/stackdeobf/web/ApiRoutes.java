package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:06 06.07.23)

import dev.booky.stackdeobf.http.HttpUtil;
import dev.booky.stackdeobf.mappings.RemappingUtil;
import dev.booky.stackdeobf.util.CompatUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.net.URI;
import java.util.Objects;

public final class ApiRoutes {

    private static final String PREFIX = "/api/v1";

    private ApiRoutes() {
    }

    public static void register(Javalin javalin) {
        javalin.get(PREFIX + "/deobf/url", ApiRoutes::handleDeobfUrlReq);
        javalin.post(PREFIX + "/deobf/body", ApiRoutes::handleDeobfReq);
    }

    private static void handleDeobfUrlReq(Context ctx) {
        URI uri = URI.create(Objects.requireNonNull(ctx.queryParam("url")));
        HttpUtil.getAsync(uri).handle((bytes, throwable) -> {
            if (throwable != null) {
                CompatUtil.LOGGER.error("Error while deobfuscating text from {}", uri, throwable);
                ctx.status(500);
            }

            if (bytes != null) {
                String str = new String(bytes);
                ctx.result(RemappingUtil.remapString(str));
            }

            // don't care about result
            return null;
        }).join();
    }

    private static void handleDeobfReq(Context ctx) {
        ctx.result(RemappingUtil.remapString(ctx.body()));
    }
}
