package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:10 29.03.23)

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class HttpUtil {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");
    private static final int RETRY_LIMIT = Integer.getInteger("stackdeobf.http-retry-count", 3);

    private static final Map<Executor, HttpClient> HTTP = new WeakHashMap<>();

    private HttpUtil() {
    }

    private static boolean isSuccess(int code) {
        return code / 100 == 2;
    }

    private static HttpClient getHttpClient(Executor executor) {
        synchronized (HTTP) {
            return HTTP.computeIfAbsent(executor, $ -> HttpClient.newBuilder().executor(executor).build());
        }
    }

    public static CompletableFuture<HttpResponseContainer> getAsync(URI url, Executor executor) {
        return getAsync(url, executor, 0);
    }

    public static CompletableFuture<HttpResponseContainer> getAsync(HttpRequest request, Executor executor) {
        return getAsync(request, executor, 0);
    }

    public static CompletableFuture<HttpResponseContainer> getAsync(URI url, Executor executor, int depth) {
        HttpRequest request = HttpRequest.newBuilder(url).build();
        return getAsync(request, executor, depth);
    }

    public static CompletableFuture<HttpResponseContainer> getAsync(HttpRequest request, Executor executor, int depth) {
        if (depth > RETRY_LIMIT) {
            throw new FailedHttpRequestException("Retry depth exceeded retry limit (" + RETRY_LIMIT + ") – "
                    + "cancelling request to " + request.method() + " " + request.uri());
        }

        LOGGER.info("Trying to request {} {}... (try #{})",
                request.method(), request.uri(), depth);

        Instant start = Instant.now();
        return getHttpClient(executor)
                .sendAsync(request, BodyHandlers.ofByteArray())
                .thenComposeAsync(resp -> {
                    Duration duration = Duration.between(start, Instant.now());

                    boolean success = isSuccess(resp.statusCode());
                    int bodyByteCount = resp.body().length;

                    LOGGER.log(success ? Level.INFO : Level.ERROR,
                            "Received {} bytes ({}) with status {} from {} {} in {}ms",
                            bodyByteCount, FileUtils.byteCountToDisplaySize(bodyByteCount),
                            resp.statusCode(), request.method(), request.uri(), duration.toMillis());

                    Supplier<CompletableFuture<HttpResponseContainer>> retry =
                            () -> getAsync(request, executor, depth + 1);
                    if (!success) {
                        return retry.get();
                    }

                    HttpResponseContainer respContainer = new HttpResponseContainer(request, resp, duration, retry);
                    return CompletableFuture.completedFuture(respContainer);
                }, executor);
    }
}
