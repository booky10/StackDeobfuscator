package dev.booky.stackdeobf.http;
// Created by booky10 in StackDeobfuscator (18:10 29.03.23)

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class HttpUtil {

    private static final Logger LOGGER = LogManager.getLogger("StackDeobfuscator");
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
        HttpRequest request = HttpRequest.newBuilder(url).build();
        return getAsync(request, executor);
    }

    public static CompletableFuture<HttpResponseContainer> getAsync(HttpRequest request, Executor executor) {
        HttpResponse.BodyHandler<byte[]> handler = HttpResponse.BodyHandlers.ofByteArray();

        LOGGER.info("Requesting {}...", request.uri());
        Instant start = Instant.now();

        return getHttpClient(executor)
                .sendAsync(request, handler)
                .thenApplyAsync(resp -> {
                    Duration duration = Duration.between(start, Instant.now());
                    int bodyByteCount = resp.body().length;

                    String message = "Received {} bytes ({}) with status {} from {} {} in {}ms";
                    Object[] args = {bodyByteCount, FileUtils.byteCountToDisplaySize(bodyByteCount),
                            resp.statusCode(), request.method(), request.uri(), duration.toMillis()};

                    if (!isSuccess(resp.statusCode())) {
                        LOGGER.error(message, args);
                        throw new FailedHttpRequestException(resp);
                    }
                    LOGGER.info(message, args);
                    return new HttpResponseContainer(request, resp, duration);
                }, executor);
    }
}
