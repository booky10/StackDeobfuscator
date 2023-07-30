package dev.booky.stackdeobf.web;
// Created by booky10 in StackDeobfuscator (17:06 06.07.23)

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.booky.stackdeobf.http.HttpResponseContainer;
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
import io.javalin.http.util.NaiveRateLimit;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static dev.booky.stackdeobf.util.VersionConstants.V18W49A;
import static dev.booky.stackdeobf.util.VersionConstants.V19W36A;
import static dev.booky.stackdeobf.util.VersionConstants.V1_14_4;
import static dev.booky.stackdeobf.util.VersionConstants.V1_18_2;
import static dev.booky.stackdeobf.util.VersionConstants.V1_19_DEEP_DARK_EXPERIMENTAL_SNAPSHOT_1;
import static dev.booky.stackdeobf.util.VersionConstants.V22W13A;
import static dev.booky.stackdeobf.util.VersionConstants.V23W13A_OR_B;

public final class ApiRoutes {

    private static final String HASTEBIN_API_TOKEN = System.getProperty("web.hastebin-api-token");

    private static final Path CACHE_DIR = Path.of(System.getProperty("mappings.cachedir", "mappings"));
    private static final String DEFAULT_MAPPINGS_PROVIDER = "yarn";
    private static final String DEFAULT_MAPPINGS_VERSION = "3465";
    private static final String DEFAULT_ENVIRONMENT = "client";

    private static final String PREFIX = "/api/v1";

    private final AsyncLoadingCache<CacheKey, CachedMappings> mappingsCache;
    private final AsyncLoadingCache<URI, byte[]> urlCache;

    private final Javalin javalin;
    private final Map<Integer, VersionData> versionData;

    private ApiRoutes(Javalin javalin, Map<Integer, VersionData> versionData) {
        this.javalin = javalin;
        this.versionData = versionData;

        this.mappingsCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .buildAsync(this::loadMapping);
        this.urlCache = Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .buildAsync(this::loadUrl);
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
            case "yarn" -> {
                if (versionData.getWorldVersion() < V18W49A) {
                    throw new BadRequestResponse("Unsupported version for yarn mappings specified: " + version);
                }
                yield new YarnMappingProvider(versionData);
            }
            case "quilt" -> {
                if (versionData.getWorldVersion() < V1_18_2 || versionData.getWorldVersion() == V23W13A_OR_B
                        || (versionData.getWorldVersion() >= V1_19_DEEP_DARK_EXPERIMENTAL_SNAPSHOT_1 && versionData.getWorldVersion() <= V22W13A)) {
                    throw new BadRequestResponse("Unsupported version for quilt mappings specified: " + version);
                }
                yield new QuiltMappingProvider(versionData);
            }
            case "mojang" -> {
                if (versionData.getWorldVersion() < V19W36A && versionData.getWorldVersion() != V1_14_4) {
                    throw new BadRequestResponse("Unsupported version for mojang mappings specified: " + version);
                }
                yield new MojangMappingProvider(versionData, environment);
            }
            default -> throw new BadRequestResponse("Unsupported mappings specified: " + mappings);
        };

        return CachedMappings.create(CACHE_DIR, provider, executor);
    }

    private CompletableFuture<byte[]> loadUrl(URI uri, Executor executor) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri);
        if ("hastebin.com".equals(uri.getHost()) && uri.getPath().startsWith("/raw")) {
            if (HASTEBIN_API_TOKEN != null) {
                request.header("Authorization", "Bearer " + HASTEBIN_API_TOKEN);
            }
        }
        return HttpUtil.getAsync(request.build(), executor)
                .thenApply(HttpResponseContainer::getBody);
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
        // one request per 10 seconds, should be enough
        NaiveRateLimit.requestPerTimeUnit(ctx, 6, TimeUnit.MINUTES);

        URI uri;
        try {
            uri = URI.create(Objects.requireNonNull(ctx.queryParam("url")));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestResponse("Illegal url specified: " + exception);
        }

        long startStep = System.nanoTime();
        return this.getMappings(ctx).thenCompose(mappings -> {
            long urlStep = System.nanoTime();
            ctx.header("Mappings-Time", Long.toString(urlStep - startStep));

            return this.urlCache.get(uri).thenAccept(bytes -> {
                long remapStep = System.nanoTime();
                ctx.header("Url-Time", Long.toString(remapStep - urlStep));

                String str = new String(bytes);
                String remappedStr = mappings.remapString(str);
                ctx.result(remappedStr);

                long resultStep = System.nanoTime();
                ctx.header("Remap-Time", Long.toString(resultStep - remapStep));
                ctx.header("Total-Time", Long.toString(resultStep - startStep));
            });
        });
    }

    private CompletableFuture<?> handleDeobfBodyReq(Context ctx) {
        // one request per 6 seconds, should be enough
        NaiveRateLimit.requestPerTimeUnit(ctx, 10, TimeUnit.MINUTES);

        long startStep = System.nanoTime();
        return this.getMappings(ctx).thenAccept(mappings -> {
            long remapStep = System.nanoTime();
            ctx.header("Mappings-Time", Long.toString(remapStep - startStep));

            String remappedStr = mappings.remapString(ctx.body());
            ctx.result(remappedStr);

            long resultStep = System.nanoTime();
            ctx.header("Remap-Time", Long.toString(resultStep - remapStep));
            ctx.header("Total-Time", Long.toString(resultStep - startStep));
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
