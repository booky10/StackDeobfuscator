package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:06 06.07.23)

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.booky.stackdeobf.http.HttpUtil;
import dev.booky.stackdeobf.mappings.CachedMappings;
import dev.booky.stackdeobf.mappings.providers.AbstractMappingProvider;
import dev.booky.stackdeobf.mappings.providers.MojangMappingProvider;
import dev.booky.stackdeobf.mappings.providers.QuiltMappingProvider;
import dev.booky.stackdeobf.mappings.providers.YarnMappingProvider;
import dev.booky.stackdeobf.util.VersionData;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public final class ApiRoutes {

    private static final Path CACHE_DIR = Path.of(System.getProperty("mappings.cachedir", "mappings"));
    private static final String DEFAULT_MAPPINGS_PROVIDER = "yarn";
    private static final String DEFAULT_MAPPINGS_VERSION = "3465";
    private static final String DEFAULT_ENVIRONMENT = "client";

    private static final String PREFIX = "/api/v1";

    private final AsyncLoadingCache<CacheKey, CachedMappings> mappingsCache;

    private final Javalin javalin;
    private final Map<Integer, VersionData> versionData;

    private ApiRoutes(Javalin javalin, Map<Integer, VersionData> versionData) {
        this.javalin = javalin;
        this.versionData = versionData;

        this.mappingsCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .buildAsync(this::loadMapping);
    }

    public static void register(Javalin javalin, Map<Integer, VersionData> versionData) {
        ApiRoutes routes = new ApiRoutes(javalin, versionData);
        routes.register();
    }

    private CompletableFuture<CachedMappings> loadMapping(CacheKey key, Executor executor) {
        String mappings = key.mappings();
        int version = key.version();
        String environment = key.environment();

        VersionData versionData = this.versionData.get(version);
        if (versionData == null) {
            throw new BadRequestResponse("Unsupported version specified: " + version);
        }

        AbstractMappingProvider provider = switch (mappings) {
            case "yarn" -> new YarnMappingProvider(versionData);
            case "quilt" -> {
                if (versionData.getWorldVersion() < 2975) {
                    throw new BadRequestResponse("Unsupported version for quilt mappings specified: " + version);
                }
                yield new QuiltMappingProvider(versionData);
            }
            case "mojang" -> {
                if (versionData.getWorldVersion() < 2203 && versionData.getWorldVersion() != 1976) {
                    throw new BadRequestResponse("Unsupported version for mojang mappings specified: " + version);
                }
                yield new MojangMappingProvider(versionData, environment);
            }
            default -> throw new BadRequestResponse("Unsupported mappings specified: " + mappings);
        };

        return CachedMappings.create(CACHE_DIR, provider, executor);
    }

    private void register() {
        this.javalin.get(PREFIX + "/deobf/url", ctx -> ctx.future(() -> this.handleDeobfUrlReq(ctx)));
        this.javalin.post(PREFIX + "/deobf/body", ctx -> ctx.future(() -> this.handleDeobfBodyReq(ctx)));
    }

    private CompletableFuture<CachedMappings> getMappings(Context ctx) {
        String mappings = Objects.requireNonNullElse(ctx.queryParam("mappings"), DEFAULT_MAPPINGS_PROVIDER);
        String version = Objects.requireNonNullElse(ctx.queryParam("version"), DEFAULT_MAPPINGS_VERSION);
        String environment = Objects.requireNonNullElse(ctx.queryParam("environment"), DEFAULT_ENVIRONMENT);

        CacheKey cacheKey;
        try {
            cacheKey = new CacheKey(mappings, version, environment);
        } catch (NumberFormatException exception) {
            throw new BadRequestResponse("Illegal world version specified: " + version);
        }

        return this.mappingsCache.get(cacheKey);
    }

    private CompletableFuture<?> handleDeobfUrlReq(Context ctx) {
        URI uri;
        try {
            uri = URI.create(Objects.requireNonNull(ctx.queryParam("url")));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestResponse("Illegal url specified: " + exception);
        }

        return this.getMappings(ctx)
                .thenCompose(mappings -> HttpUtil.getAsync(uri)
                        .thenAccept(bytes -> {
                            String str = new String(bytes);
                            String remappedStr = mappings.remapString(str);
                            ctx.result(remappedStr);
                        }));
    }

    private CompletableFuture<?> handleDeobfBodyReq(Context ctx) {
        return this.getMappings(ctx).thenAccept(mappings -> {
            String remappedStr = mappings.remapString(ctx.body());
            ctx.result(remappedStr);
        });
    }

    private record CacheKey(String mappings, int version, String environment) {

        public CacheKey(String mappings, int version, String environment) {
            if (!"client".equalsIgnoreCase(environment) && !"server".equalsIgnoreCase(environment)) {
                throw new BadRequestResponse("Illegal environment specified: " + environment);
            }

            this.mappings = mappings.toLowerCase(Locale.ROOT);
            this.version = version;
            this.environment = environment.toLowerCase(Locale.ROOT);
        }

        public CacheKey(String mappings, String versionStr, String environment) throws NumberFormatException {
            this(mappings, Integer.parseInt(versionStr), environment);
        }
    }
}